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

    static boolean hatCovered(ResourceLocation texture,int col,int row){
        int[] hat=hatPixels(texture);
        return hat.length==64&&(hat[row*8+col]>>>24)>=128;
    }
    static boolean loaded(ResourceLocation texture){return facePixels(texture).length==64;}
    static void clear(){CACHE.clear();}

    private static int[] read(ResourceLocation texture,boolean facePlane){
        String key=(facePlane?"f:":"h:")+texture;
        int[] cached=CACHE.get(key);
        if(cached!=null)return cached;
        int[] sampled=readGl(texture,facePlane);
        if(sampled.length!=64)sampled=readImage(texture,facePlane);
        if(sampled.length==64)CACHE.put(key,sampled);
        return sampled;
    }

    /** Reads the bound texture with GL and samples each face texel at its centre. */
    private static int[] readGl(ResourceLocation texture,boolean facePlane){
        try{
            Minecraft mc=MinecraftAccess.get();
            ITextureObject t=mc.getTextureManager().getTexture(texture);
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
