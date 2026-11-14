package otl.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * A dark-leather prolate spheroid with a white stripe near each tip and white laces, drawn with
 * the fixed-function pipeline. Built once and reused by the held item, the thrown ball and the
 * flag banners.
 */
final class FootballMesh {
    private static final int STACKS=16,SLICES=20;
    private static final float WAIST=0.30F,LENGTH=0.5F;
    private static final float[] VERTICES,COLORS;
    /** How many white bands wrap the ball. Pinned by the smoke test. */
    static final int STRIPES;
    static{
        List<Float> vertices=new ArrayList<>();
        List<Float> colors=new ArrayList<>();
        int stripes=0;
        float lx=0.35F,ly=0.62F,lz=0.70F,light=(float)Math.sqrt(lx*lx+ly*ly+lz*lz);
        lx/=light;ly/=light;lz/=light;
        for(int stack=0;stack<STACKS;stack++){
            double top=Math.PI*(stack/(double)STACKS)-Math.PI/2;
            double bottom=Math.PI*((stack+1)/(double)STACKS)-Math.PI/2;
            // A whole row of quads is white, never a blend, so the stripe edge stays crisp. One row
            // either side of the waist, about a fifth of the way in from each tip.
            double band=Math.abs(Math.sin(Math.PI*((stack+0.5)/STACKS)-Math.PI/2));
            boolean stripe=band>0.55&&band<0.72;
            if(stripe)stripes++;
            for(int slice=0;slice<SLICES;slice++){
                double left=2*Math.PI*slice/SLICES;
                double right=2*Math.PI*(slice+1)/SLICES;
                point(vertices,colors,top,left,lx,ly,lz,stripe);
                point(vertices,colors,top,right,lx,ly,lz,stripe);
                point(vertices,colors,bottom,right,lx,ly,lz,stripe);
                point(vertices,colors,bottom,left,lx,ly,lz,stripe);
            }
        }
        // Laces run along the long axis on the +Z face, so a spinning ball flashes white: a thin
        // central lace with the cross stitches over it.
        float from=-0.26F,to=0.26F,half=0.03F,lift=0.012F;
        int segments=8;
        for(int i=0;i<segments;i++){
            float ya=from+(to-from)*i/segments,yb=from+(to-from)*(i+1)/segments;
            strip(vertices,colors,-half,ya,surface(ya)+lift,half,ya,surface(ya)+lift,
                half,yb,surface(yb)+lift,-half,yb,surface(yb)+lift,lx,ly,lz);
        }
        for(int i=0;i<7;i++){
            float y=from+(to-from)*(i+0.5F)/7,z=surface(y)+lift+0.002F;
            strip(vertices,colors,-0.075F,y-0.012F,z,0.075F,y-0.012F,z,
                0.075F,y+0.012F,z,-0.075F,y+0.012F,z,lx,ly,lz);
        }
        STRIPES=stripes;
        VERTICES=toArray(vertices);COLORS=toArray(colors);
    }
    private FootballMesh(){}
    private static float surface(float y){
        float q=1-(y/LENGTH)*(y/LENGTH);
        return WAIST*(float)Math.sqrt(Math.max(0,q));
    }
    private static void point(List<Float> v,List<Float> c,double theta,double phi,float lx,float ly,float lz,
                              boolean stripe){
        float x=WAIST*(float)(Math.cos(theta)*Math.cos(phi));
        float y=LENGTH*(float)Math.sin(theta);
        float z=WAIST*(float)(Math.cos(theta)*Math.sin(phi));
        float nx=x/(WAIST*WAIST),ny=y/(LENGTH*LENGTH),nz=z/(WAIST*WAIST);
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length>1.0E-6F){nx/=length;ny/=length;nz/=length;}
        // Dark maroon leather, the colour of a real ball rather than plain brown.
        if(stripe)add(v,c,x,y,z,nx,ny,nz,lx,ly,lz,0.95F,0.95F,0.93F);
        else add(v,c,x,y,z,nx,ny,nz,lx,ly,lz,0.42F,0.14F,0.11F);
    }
    private static void strip(List<Float> v,List<Float> c,float x0,float y0,float z0,float x1,float y1,float z1,
                              float x2,float y2,float z2,float x3,float y3,float z3,float lx,float ly,float lz){
        add(v,c,x0,y0,z0,0,0,1,lx,ly,lz,0.95F,0.95F,0.93F);
        add(v,c,x1,y1,z1,0,0,1,lx,ly,lz,0.95F,0.95F,0.93F);
        add(v,c,x2,y2,z2,0,0,1,lx,ly,lz,0.95F,0.95F,0.93F);
        add(v,c,x3,y3,z3,0,0,1,lx,ly,lz,0.95F,0.95F,0.93F);
    }
    private static void add(List<Float> v,List<Float> c,float x,float y,float z,float nx,float ny,float nz,
                            float lx,float ly,float lz,float r,float g,float b){
        float shade=nx*lx+ny*ly+nz*lz;
        shade=shade<=0?0.45F:0.45F+0.55F*shade;
        v.add(x);v.add(y);v.add(z);
        c.add(r*shade);c.add(g*shade);c.add(b*shade);
    }
    private static float[] toArray(List<Float> list){
        float[] out=new float[list.size()];
        for(int i=0;i<out.length;i++)out[i]=list.get(i);
        return out;
    }
    /** Draws the ball at the current matrix, one unit tall. Leaves every GL switch as it found it. */
    static void draw(){
        draws++;
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();
        GlStateManager.disableBlend();GlStateManager.disableAlpha();
        GL11.glBegin(GL11.GL_QUADS);
        for(int vertex=0,color=0;vertex<VERTICES.length;vertex+=3,color+=3){
            GlStateManager.color(COLORS[color],COLORS[color+1],COLORS[color+2],1F);
            GL11.glVertex3f(VERTICES[vertex],VERTICES[vertex+1],VERTICES[vertex+2]);
        }
        GL11.glEnd();
        GlStateManager.color(1F,1F,1F,1F);
        if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
        if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
        if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
        if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
        if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
    }
    /** Quads in the mesh, used by the smoke test to prove the mesh was built. */
    static int quads(){return VERTICES.length/12;}
    /** Times FootballMesh.draw() has run, used by the smoke test to prove a render drew the mesh. */
    static int draws;
}
