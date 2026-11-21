package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Small, intentionally limited OBJ reader for the bundled static skateboard mesh. */
final class ObjSkateboard {
    private static final ResourceLocation FILE=new ResourceLocation("otl","models/skateboard.obj");
    private static final ResourceLocation TEXTURE=new ResourceLocation("otl","textures/skateboard.png");
    private static final List<float[]> V=new ArrayList<>(), UV=new ArrayList<>(), N=new ArrayList<>();
    private static final List<int[]> F=new ArrayList<>();
    private static boolean loaded;
    private static int displayList;
    private static float minX,maxX,minY,maxY,minZ,maxZ;

    private ObjSkateboard(){}

    private static void load(){
        if(loaded)return;
        loaded=true;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(
            Minecraft.getMinecraft().getResourceManager().getResource(FILE).getInputStream(),"UTF-8"))){
            String line;
            while((line=r.readLine())!=null){
                String[] a=line.trim().split("\\s+");
                if(a.length==0)continue;
                if("v".equals(a[0]))V.add(new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3])});
                else if("vt".equals(a[0]))UV.add(new float[]{Float.parseFloat(a[1]),1f-Float.parseFloat(a[2])});
                else if("vn".equals(a[0]))N.add(new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3])});
                else if("f".equals(a[0])){
                    for(int i=1;i+2<a.length;i++)F.add(face(a[1],a[i+1],a[i+2]));
                }
            }
            for(float[] p:V){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);minZ=Math.min(minZ,p[2]);maxZ=Math.max(maxZ,p[2]);}
        }catch(Exception e){throw new RuntimeException("Could not load skateboard OBJ",e);}
    }

    private static int[] face(String... s){
        int[] out=new int[9];
        for(int i=0;i<3;i++){
            String[] p=s[i].split("/");
            out[i*3]=Integer.parseInt(p[0])-1;
            out[i*3+1]=p.length>1&&!p[1].isEmpty()?Integer.parseInt(p[1])-1:-1;
            out[i*3+2]=p.length>2?Integer.parseInt(p[2])-1:-1;
        }
        return out;
    }

    static void render(float scale){
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            load();
            if(displayList==0){
                displayList=GL11.glGenLists(1);
                GL11.glNewList(displayList,GL11.GL_COMPILE);
                draw(scale,1f,1f,1f,1f);
                GL11.glEndList();
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
            GL11.glColor4f(1f,1f,1f,1f);
            GL11.glCallList(displayList);
            // Inverted hull: only the enlarged back-facing shell becomes the outline.
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glCullFace(GL11.GL_FRONT);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glColor4f(0f,0f,0f,1f);
            GL11.glPushMatrix();
            GL11.glScalef(1.025f,1.025f,1.025f);
            GL11.glCallList(displayList);
            GL11.glPopMatrix();
        }finally{
            // Restore texture, culling, depth, blend, color and polygon state before Minecraft
            // renders the rest of the player/world. A leaked state here causes the white-screen
            // failure when N first enables the board.
            GL11.glPopAttrib();
        }
    }

    private static void draw(float scale,float red,float green,float blue,float alpha){
        Tessellator t=Tessellator.getInstance(); WorldRenderer w=t.getWorldRenderer();
        GL11.glColor4f(red,green,blue,alpha);
        float sx=Math.max(1e-5f,maxX-minX), sy=Math.max(1e-5f,maxY-minY), sz=Math.max(1e-5f,maxZ-minZ);
        w.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_TEX_NORMAL);
        for(int[] f:F)for(int i=0;i<3;i++){
            int vi=f[i*3],ti=f[i*3+1],ni=f[i*3+2]; float[] p=V.get(vi);
            float x=(p[0]-(minX+maxX)/2f)/sx*1.2f;
            float y=(p[1]-maxY)/sx*1.2f;
            float z=(p[2]-(minZ+maxZ)/2f)/sx*1.2f;
            float[] uv=ti>=0&&ti<UV.size()?UV.get(ti):new float[]{0f,0f};
            float[] n=ni>=0&&ni<N.size()?N.get(ni):new float[]{0f,1f,0f};
            w.pos(x*scale,y*scale,z*scale).tex(uv[0],uv[1]).normal(n[0],n[1],n[2]).endVertex();
        }
        t.draw();
    }
}
