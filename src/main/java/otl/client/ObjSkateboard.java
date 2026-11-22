package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Small, intentionally limited OBJ reader for the bundled skateboard mesh.
 *
 * <p>The mesh is baked straight out of {@code skateboard_turntable.blend} in metres with the deck
 * up and the wheels at Y=0, so it needs no scaling or re-orienting here. Each vertex carries a
 * flat material colour with the light already baked in, which is why the game's own lighting is
 * left off: the board keeps the deck, grip, trucks and wheels apart without a texture.
 *
 * <p>Every frame is drawn through {@link Tessellator} and {@link GlStateManager}. The mesh was
 * originally compiled into a display list, but a list cannot record a client-array draw: it
 * replays a stale buffer pointer and covers the screen in garbage triangles (the white flash).
 * Raw {@code GL11} calls are avoided for the same reason - they desync the state cache that
 * OptiFine and Lunar rely on.
 */
final class ObjSkateboard {
    private static final ResourceLocation FILE=new ResourceLocation("otl","models/skateboard.obj");
    private static final List<float[]> V=new ArrayList<>(),N=new ArrayList<>();
    private static final List<int[]> F=new ArrayList<>();
    private static boolean loaded;
    private static boolean failed;

    private ObjSkateboard(){}

    private static void load(){
        if(loaded)return;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(
            Minecraft.getMinecraft().getResourceManager().getResource(FILE).getInputStream(),"UTF-8"))){
            String line;
            while((line=r.readLine())!=null){
                String[] a=line.trim().split("\\s+");
                if(a.length<2)continue;
                if("v".equals(a[0])){
                    // "v x y z r g b": the colour is the material's, baked at export time.
                    V.add(new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3]),
                        a.length>6?Float.parseFloat(a[4]):1f,
                        a.length>6?Float.parseFloat(a[5]):1f,
                        a.length>6?Float.parseFloat(a[6]):1f});
                }else if("vn".equals(a[0]))N.add(new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3])});
                else if("f".equals(a[0]))for(int i=1;i+2<a.length;i++)F.add(face(a[1],a[i+1],a[i+2]));
            }
        }catch(Exception e){
            failed=true;
            System.err.println("OTL: could not read the skateboard mesh: "+e);
        }finally{
            loaded=true;
        }
    }

    private static int[] face(String... s){
        int[] out=new int[6];
        for(int i=0;i<3;i++){
            String[] p=s[i].split("/");
            out[i*2]=Integer.parseInt(p[0])-1;
            out[i*2+1]=p.length>2&&!p[2].isEmpty()?Integer.parseInt(p[2])-1:-1;
        }
        return out;
    }

    static void render(){
        if(failed)return;
        load();
        if(failed)return;
        try{
            GlStateManager.disableLighting();
            GlStateManager.disableTexture2D();
            draw();
            // Inverted hull: draw an enlarged copy with the front faces culled, so only the
            // back-facing shell remains as the silhouette. A wireframe pass would outline every
            // internal triangle of the decimated mesh instead.
            GlStateManager.enableCull();
            GlStateManager.cullFace(GL11.GL_FRONT);
            GlStateManager.pushMatrix();
            GlStateManager.scale(1.03f,1.03f,1.03f);
            GlStateManager.color(0.02f,0.02f,0.02f,1f);
            draw();
            GlStateManager.popMatrix();
        }catch(Throwable e){
            failed=true;
            System.err.println("OTL: skateboard render disabled after render failure: "+e);
        }finally{
            GlStateManager.cullFace(GL11.GL_BACK);
            GlStateManager.disableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.color(1f,1f,1f,1f);
        }
    }

    private static void draw(){
        Tessellator t=Tessellator.getInstance();
        WorldRenderer w=t.getWorldRenderer();
        w.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for(int[] f:F)for(int i=0;i<3;i++){
            int vi=f[i*2],ni=f[i*2+1];
            if(vi<0||vi>=V.size())continue;
            float[] p=V.get(vi);
            float[] n=ni>=0&&ni<N.size()?N.get(ni):new float[]{0f,1f,0f};
            w.pos(p[0],p[1],p[2]).tex(0f,0f).color(p[3],p[4],p[5],1f).normal(n[0],n[1],n[2]).endVertex();
        }
        t.draw();
    }
}
