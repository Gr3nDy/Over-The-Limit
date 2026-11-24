package otl.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Composites visible clothing onto the base atlas before the animated model draws. */
final class SkateSkin {
    // Base X/Y, overlay X/Y, width/height: regions are the same for Steve and Alex.
    private static final int[][] PARTS={
        {0,0,32,0,32,16}, {16,16,16,32,24,16},
        {40,16,40,32,16,16}, {32,48,48,48,16,16},
        {0,16,0,32,16,16}, {16,48,0,48,16,16}
    };
    private static final Map<String,ResourceLocation> BAKED=new LinkedHashMap<>(32,0.75f,true);
    private static final EnumPlayerModelParts[] SKIN_PARTS={EnumPlayerModelParts.HAT,EnumPlayerModelParts.JACKET,
        EnumPlayerModelParts.RIGHT_SLEEVE,EnumPlayerModelParts.LEFT_SLEEVE,
        EnumPlayerModelParts.RIGHT_PANTS_LEG,EnumPlayerModelParts.LEFT_PANTS_LEG};
    private static final int PANTS_FLAGS=EnumPlayerModelParts.LEFT_PANTS_LEG.getPartMask()
        |EnumPlayerModelParts.RIGHT_PANTS_LEG.getPartMask();
    private static final Map<EntityPlayer,RenderSkin> RENDERING=new IdentityHashMap<>();
    private static final class RenderSkin {
        final WatchableObject flag;
        final byte original;
        final int mask;
        int depth=1;

        RenderSkin(WatchableObject flag,int mask){
            this.flag=flag;this.original=(Byte)flag.getObject();this.mask=mask;
        }
    }

    private SkateSkin(){}

    static int mask(EntityPlayer player){
        RenderSkin active=RENDERING.get(player);
        if(active!=null)return active.mask;
        int mask=0;
        for(int i=0;i<SKIN_PARTS.length;i++)if(player.isWearing(SKIN_PARTS[i]))mask|=1<<i;
        return mask;
    }

    static void beginRender(EntityPlayer player){
        if(player.worldObj==null||!player.worldObj.isRemote)return;
        RenderSkin active=RENDERING.get(player);
        if(active!=null){active.depth++;return;}
        int mask=mask(player);
        if(mask==0)return;
        for(WatchableObject flag:player.getDataWatcher().getAllWatched()){
            if(flag.getDataValueId()!=10||!(flag.getObject() instanceof Byte))continue;
            active=new RenderSkin(flag,mask);
            RENDERING.put(player,active);
            // Don't mark the watcher dirty or send changes; only render code sees this transient mask.
            flag.setObject((byte)(active.original&~PANTS_FLAGS));
            return;
        }
    }

    static void endRender(EntityPlayer player){
        RenderSkin active=RENDERING.get(player);
        if(active!=null&&--active.depth==0){
            active.flag.setObject(active.original);
            RENDERING.remove(player);
        }
    }

    static void endFrame(){
        for(RenderSkin active:RENDERING.values())active.flag.setObject(active.original);
        RENDERING.clear();
    }

    static ResourceLocation get(TextureManager textures,ResourceLocation original,int mask){
        if(mask==0)return original;
        ITextureObject source=textures.getTexture(original);
        if(source==null){
            textures.bindTexture(original);
            source=textures.getTexture(original);
        }
        if(source==null)return original;
        int id=source.getGlTextureId();
        String key=original+"/"+id+"/"+mask;
        ResourceLocation cached=BAKED.get(key);
        if(cached!=null)return cached;
        GlStateManager.bindTexture(id);
        int size=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
        int height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
        // shortcut: legacy or unusually large skins use the base atlas; bake them only if needed.
        if(size!=height||size<64||size>1024||size%64!=0)return original;
        IntBuffer pixels=BufferUtils.createIntBuffer(size*size);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,pixels);
        int[] argb=new int[size*size];
        pixels.get(argb);
        boolean loaded=false;
        for(int pixel:argb)if((pixel>>>24)!=0){loaded=true;break;}
        if(!loaded)return original;
        BufferedImage src=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0,0,size,size,argb,0,size);
        ResourceLocation baked=textures.getDynamicTextureLocation("otl_flat_skin",new DynamicTexture(flatten(src,mask)));
        BAKED.put(key,baked);
        if(BAKED.size()>48){
            String oldest=BAKED.keySet().iterator().next();
            textures.deleteTexture(BAKED.remove(oldest));
        }
        return baked;
    }

    static BufferedImage flatten(BufferedImage src,int mask){
        int size=src.getWidth(),scale=size/64;
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try{
            g.drawImage(src,0,0,null);
            for(int i=0;i<PARTS.length;i++){
                if((mask&(1<<i))==0)continue;
                int[] r=PARTS[i];
                g.drawImage(src,r[0]*scale,r[1]*scale,(r[0]+r[4])*scale,(r[1]+r[5])*scale,
                    r[2]*scale,r[3]*scale,(r[2]+r[4])*scale,(r[3]+r[5])*scale,null);
            }
        }finally{g.dispose();}
        for(int i=0;i<PARTS.length;i++)if((mask&(1<<i))!=0)shadeHem(src,out,PARTS[i],scale,i==0?8:4);
        return out;
    }

    private static void shadeHem(BufferedImage src,BufferedImage out,int[] r,int scale,int depth){
        for(int y=scale;y<r[5]*scale;y++){
            // The lid UVs and side UVs meet in the atlas, but not along the same surface.
            if(y>=depth*scale&&y<(depth+1)*scale)continue;
            for(int x=0;x<r[4]*scale;x++){
                int overlayX=r[2]*scale+x,overlayY=r[3]*scale+y;
                if((src.getRGB(overlayX,overlayY)>>>24)!=0)continue;
                int covered=src.getRGB(overlayX,overlayY-scale)>>>24;
                if(covered==0)continue;
                int baseX=r[0]*scale+x,baseY=r[1]*scale+y,pixel=out.getRGB(baseX,baseY);
                if((pixel>>>24)==0)continue;
                int light=255-covered/11;
                out.setRGB(baseX,baseY,(pixel&0xFF000000)|(((pixel>>16)&255)*light/255<<16)
                    |(((pixel>>8)&255)*light/255<<8)|(pixel&255)*light/255);
            }
        }
    }

    static void clear(TextureManager textures){
        for(ResourceLocation texture:BAKED.values())textures.deleteTexture(texture);
        BAKED.clear();
    }
}
