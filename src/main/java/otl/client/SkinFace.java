package otl.client;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Samples the 8x8 face texels of any loaded skin texture. Prefers reading the uploaded
 * texture with GL (works for high-resolution skins); falls back to the decoded
 * BufferedImage the skin manager keeps, so the editor and preview never blank out.
 */
final class SkinFace {
    private static final Map<String,int[]> CACHE=new HashMap<>();
    private static final Map<String,Long> RETRY_AT=new HashMap<>();
    /** Failed readbacks are retried at most twice a second instead of every frame. */
    private static final long RETRY_NANOS=500000000L;
    private static final Map<String,Integer> RETRY_COUNT=new HashMap<>();
    private static final int MAX_RETRIES=3,MAX_CACHE=512;
    private SkinFace(){}

    static int[] facePixels(ResourceLocation texture){return read(texture,true);}
    static int[] hatPixels(ResourceLocation texture){return read(texture,false);}

    /** What the head actually shows: hat texels where opaque, face texels elsewhere. */
    static int[] composite(ResourceLocation texture){
        int[] face=facePixels(texture);
        if(face.length!=64)return new int[0];
        int[] hat=hatPixels(texture);
        if(hat.length!=64)return face;
        int[] out=new int[64];
        for(int i=0;i<64;i++)out[i]=((hat[i]>>>24)>=128)?hat[i]:face[i];
        return out;
    }

    static boolean loaded(ResourceLocation texture){return facePixels(texture).length==64;}
    static void clear(){CACHE.clear();RETRY_COUNT.clear();RETRY_AT.clear();}

    private static int[] read(ResourceLocation texture,boolean facePlane){
        String key=(facePlane?"f:":"h:")+texture;
        int[] cached=CACHE.get(key);
        if(cached!=null)return cached;
        Long retry=RETRY_AT.get(key);
        if(retry!=null&&System.nanoTime()<retry)return new int[0];
        int[] sampled=readGl(texture,facePlane);
        if(!valid(sampled))sampled=readImage(texture,facePlane);
        if(valid(sampled)){
            if(CACHE.size()>=MAX_CACHE)CACHE.clear();
            CACHE.put(key,sampled);RETRY_COUNT.remove(key);RETRY_AT.remove(key);return sampled;
        }
        int attempts=RETRY_COUNT.merge(key,1,Integer::sum);
        if(attempts>=MAX_RETRIES){
            System.err.println("OTL: skin readback for "+key+" failed "+attempts+" times; not retrying again this session");
            if(CACHE.size()>=MAX_CACHE)CACHE.clear();
            CACHE.put(key,new int[0]);RETRY_COUNT.remove(key);RETRY_AT.remove(key);return new int[0];
        }
        if(CACHE.size()>=MAX_CACHE)CACHE.clear();
        RETRY_AT.put(key,System.nanoTime()+RETRY_NANOS);
        return new int[0];
    }

    /** A failed readback is all zeroes; never cache that over a real skin. */
    private static boolean valid(int[] pixels){
        if(pixels.length!=64)return false;
        for(int pixel:pixels)if(pixel!=0)return true;
        return false;
    }

    /** Reads the bound texture with GL and samples each face texel at its centre. */
    private static int[] readGl(ResourceLocation texture,boolean facePlane){
        try{
            Minecraft mc=MinecraftAccess.get();
            ITextureObject t=mc.getTextureManager().getTexture(texture);
            if(t==null){
                // A plain resource skin (the default Steve) is only registered on its first bind.
                mc.getTextureManager().bindTexture(texture);
                t=mc.getTextureManager().getTexture(texture);
            }
            if(t==null)return new int[0];
            int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try{
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,t.getGlTextureId());
                int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
                int height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
                if(width<64||height<32)return new int[0];
                if(!facePlane&&height<64)return new int[0]; // 64x32 skins have no hat layer
                java.nio.ByteBuffer buffer=BufferUtils.createByteBuffer(width*height*4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
                buffer.rewind();
                int scaleX=Math.max(1,width/64),scaleY=Math.max(1,height/64);
                int[] out=new int[64];
                for(int row=0;row<8;row++)for(int col=0;col<8;col++){
                    int texelX=((facePlane?8:40)+col)*scaleX+scaleX/2;
                    int texelY=(8+row)*scaleY+scaleY/2;
                    if(texelX>=width||texelY>=height)continue;
                    int i=(texelY*width+texelX)*4;
                    int r=buffer.get(i)&255,g=buffer.get(i+1)&255,b=buffer.get(i+2)&255,a=buffer.get(i+3)&255;
                    out[row*8+col]=a<<24|r<<16|g<<8|b;
                }
                return out;
            }finally{
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
            }
        }catch(Exception e){
            return new int[0];
        }
    }

    /** Fallback for machines or states where the GL texture cannot be read back. */
    private static int[] readImage(ResourceLocation texture,boolean facePlane){
        try{
            ITextureObject t=MinecraftAccess.get().getTextureManager().getTexture(texture);
            if(t==null)return new int[0];
            Field field=ReflectionHelper.findField(t.getClass(),"bufferedImage","field_110560_d");
            field.setAccessible(true);
            Object value=field.get(t);
            if(!(value instanceof BufferedImage))return new int[0];
            BufferedImage image=(BufferedImage)value;
            int width=image.getWidth(),height=image.getHeight();
            if(width<64||height<32)return new int[0];
            if(!facePlane&&height<64)return new int[0];
            int scaleX=Math.max(1,width/64),scaleY=Math.max(1,height/64);
            int[] out=new int[64];
            for(int row=0;row<8;row++)for(int col=0;col<8;col++){
                int x=((facePlane?8:40)+col)*scaleX+scaleX/2;
                int y=(8+row)*scaleY+scaleY/2;
                if(x>=width||y>=height)continue;
                out[row*8+col]=image.getRGB(x,y);
            }
            return out;
        }catch(Exception e){
            return new int[0];
        }
    }
}
