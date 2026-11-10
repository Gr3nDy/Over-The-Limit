package otl.client;

import java.nio.IntBuffer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Isolated depth buffer permits true isometric yaw rotation without changing the game's depth buffer. */
final class MinimapRenderer {
    private static final int CHUNK=256;
    /** World blocks faded around the player, fraction erased at the centre, and where the falloff starts. */
    static final double CUTAWAY_BLOCKS=5,CUTAWAY_STRENGTH=.65,CUTAWAY_CORE=.7;
    /** The whole-map view keeps a visible spotlight even though 5 blocks shrink to a few pixels. */
    private static final double CUTAWAY_MIN_SHARE=.06;
    static double cutawayRadius(double pixelsPerBlock,double pixels){return Math.max(CUTAWAY_BLOCKS*pixelsPerBlock,pixels*CUTAWAY_MIN_SHARE);}
    /** Erase amount at a distance: flat core, then smoothstep to nothing at the radius. */
    static double cutawayFade(double distance,double radius){
        if(radius<=0)return 0;
        double t=Math.max(0,Math.min(1,(distance/radius-CUTAWAY_CORE)/(1-CUTAWAY_CORE)));
        return CUTAWAY_STRENGTH*(1-t*t*(3-2*t));
    }
    /** Ellipse-normalized erase amount; the fade is biased toward the camera (+y pixels) side. */
    static double cutawayFadeAt(double dx,double dy,double rx,double ry){
        if(rx<=0||ry<=0)return 0;
        double d2=(dx*dx)/(rx*rx)+(dy*dy)/(ry*ry);
        double t=Math.max(0,Math.min(1,(Math.sqrt(d2)-CUTAWAY_CORE)/(1-CUTAWAY_CORE)));
        return CUTAWAY_STRENGTH*(1-t*t*(3-2*t));
    }
    private MinimapModel compiled;
    private int lists,chunks;
    private Framebuffer target;
    boolean loaded(){return lists!=0;}
    void release(){
        if(lists!=0){GL11.glDeleteLists(lists,chunks+1);lists=0;}
        if(target!=null){target.deleteFramebuffer();target=null;}
        compiled=null;
    }
    private static void face(MinimapModel.Face face,int alpha){
        GL11.glColor4ub((byte)(face.color>>16),(byte)(face.color>>8),(byte)face.color,(byte)alpha);
        for(int i=0;i<4;i++)GL11.glVertex3f(face.xyz[i*3],face.xyz[i*3+1],face.xyz[i*3+2]);
    }

    private void compile(MinimapModel model){
        if(compiled==model)return;
        if(lists!=0)GL11.glDeleteLists(lists,chunks+1);
        chunks=(model.faces.size()+CHUNK-1)/CHUNK;lists=GL11.glGenLists(chunks+1);
        if(lists==0)throw new IllegalStateException("Minimap GPU allocation failed");
        for(int chunk=0;chunk<chunks;chunk++){
            GL11.glNewList(lists+chunk,GL11.GL_COMPILE);GL11.glBegin(GL11.GL_QUADS);
            for(int i=chunk*CHUNK;i<Math.min((chunk+1)*CHUNK,model.faces.size());i++)face(model.faces.get(i),255);
            GL11.glEnd();GL11.glEndList();
        }
        GL11.glNewList(lists+chunks,GL11.GL_COMPILE);
        for(int i=0;i<chunks;i++)GL11.glCallList(lists+i);
        GL11.glEndList();compiled=model;
    }
    boolean render(MinimapModel model,double[] anchor,double[] player,double angle,int pixels,double worldHalf,boolean cutaway,int[] head){
        if(!OpenGlHelper.isFramebufferEnabled())return false;
        int framebuffer=GL11.glGetInteger(0x8CA6),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        IntBuffer scissorBox=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_SCISSOR_BOX,scissorBox);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            compile(model);
            if(target==null||target.framebufferWidth!=pixels){
                if(target!=null)target.deleteFramebuffer();
                target=new Framebuffer(pixels,pixels,true);target.setFramebufferColor(0,0,0,0);
            }
            GlStateManager.depthMask(true);target.framebufferClear();target.bindFramebuffer(true);
            double dx=anchor[0]-model.center[0],dy=anchor[1]-model.center[1],dz=anchor[2]-model.center[2];
            double range=Math.max(128,model.radius+Math.sqrt(dx*dx+dy*dy+dz*dz)+32);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GL11.glOrtho(-worldHalf,worldHalf,-worldHalf,worldHalf,-range,range);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
            GlStateManager.rotate(35.26439F,1,0,0);GlStateManager.rotate((float)-angle,0,1,0);GlStateManager.translate(-anchor[0],-anchor[1],-anchor[2]);
            GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableAlpha();GlStateManager.disableBlend();GlStateManager.disableFog();
            GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.enableCull();GlStateManager.cullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glCallList(lists+chunks);
            double perUnit=pixels/(2*worldHalf);
            if(cutaway)cutaway(player,anchor,angle,perUnit,pixels);
            if(head!=null){
                project(anchor,angle,worldHalf,range);
                head(head,player,angle,perUnit,worldHalf);
            }
            return true;
        }finally{
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(matrix);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,framebuffer);
            GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
            GL11.glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }
    /** Erases framebuffer alpha in a soft disc around the player so the map background shows through. */
    private void cutaway(double[] player,double[] anchor,double angle,double perUnit,int pixels){
        double radius=cutawayRadius(perUnit,pixels);
        if(radius<=0)return;
        double px=pixels/2D+(MinimapModel.screenX(player[0],player[2],angle)-MinimapModel.screenX(anchor[0],anchor[2],angle))*perUnit;
        double py=pixels/2D-(MinimapModel.screenY(player[0],player[1],player[2],angle)-MinimapModel.screenY(anchor[0],anchor[1],anchor[2],angle))*perUnit;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GL11.glOrtho(0,pixels,0,pixels,-1,1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GlStateManager.disableCull();GlStateManager.disableTexture2D();
        // Faded terrain must not hide the head marker: reset depth inside the disc before drawing it.
        GlStateManager.enableDepth();GlStateManager.depthMask(true);GL11.glDepthFunc(GL11.GL_ALWAYS);
        GlStateManager.colorMask(false,false,false,false);GL11.glColor4f(0,0,0,1);
        double rx=radius,ry=radius*1.55,cy=py-ry*0.28;
        disc(px,cy,rx,1,ry);
        GlStateManager.colorMask(true,true,true,true);GL11.glDepthFunc(GL11.GL_LEQUAL);GlStateManager.depthMask(false);
        int segments=32;
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_ZERO,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        vertex(px,cy,0,cutawayFadeAt(0,0,rx,ry));
        for(int i=0;i<=segments;i++){double a=Math.PI*2*i/segments;vertex(px+Math.cos(a)*rx*CUTAWAY_CORE,cy+Math.sin(a)*ry*CUTAWAY_CORE,0,cutawayFadeAt(Math.cos(a)*rx*CUTAWAY_CORE,Math.sin(a)*ry*CUTAWAY_CORE,rx,ry));}
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for(int i=0;i<=segments;i++){
            double a=Math.PI*2*i/segments;
            vertex(px+Math.cos(a)*rx*CUTAWAY_CORE,cy+Math.sin(a)*ry*CUTAWAY_CORE,0,cutawayFadeAt(Math.cos(a)*rx*CUTAWAY_CORE,Math.sin(a)*ry*CUTAWAY_CORE,rx,ry));
            vertex(px+Math.cos(a)*rx,cy+Math.sin(a)*ry,0,0);
        }
        GL11.glEnd();
    }
    private static void disc(double px,double py,double rx,double z,double ry){
        int segments=32;double ix=rx*CUTAWAY_CORE,iy=ry*CUTAWAY_CORE;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(px,py,z);
        for(int i=0;i<=segments;i++)GL11.glVertex3d(px+Math.cos(Math.PI*2*i/segments)*ix,py+Math.sin(Math.PI*2*i/segments)*iy,z);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for(int i=0;i<=segments;i++){
            double a=Math.PI*2*i/segments;
            GL11.glVertex3d(px+Math.cos(a)*ix,py+Math.sin(a)*iy,z);
            GL11.glVertex3d(px+Math.cos(a)*rx,py+Math.sin(a)*ry,z);
        }
        GL11.glEnd();
    }
    private static void vertex(double x,double y,double z,double alpha){
        GL11.glColor4f(0,0,0,(float)alpha);GL11.glVertex3d(x,y,z);
    }
    private static void project(double[] anchor,double angle,double worldHalf,double range){
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GL11.glOrtho(-worldHalf,worldHalf,-worldHalf,worldHalf,-range,range);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GlStateManager.rotate(35.26439F,1,0,0);GlStateManager.rotate((float)-angle,0,1,0);GlStateManager.translate(-anchor[0],-anchor[1],-anchor[2]);
    }
    /** Camera-facing head marker; depth testing keeps it behind terrain that is closer to the camera. */
    private static void head(int[] pixels,double[] player,double angle,double perUnit,double worldHalf){
        if(pixels.length!=64)return;
        double scale=UtilitySettings.minimapHeadSize/100F;
        double half=worldHalf*0.075*scale,texel=half/4,a=Math.toRadians(angle);
        double rightX=Math.cos(a),rightZ=-Math.sin(a);
        double upX=-Math.sin(a)*.577350269189626,upY=.816496580927726,upZ=-Math.cos(a)*.577350269189626;
        double x=player[0],y=player[1]+half*upY+.2,z=player[2];
        GlStateManager.enableDepth();GL11.glDepthFunc(GL11.GL_ALWAYS);GlStateManager.depthMask(false);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);
        GlStateManager.disableTexture2D();GlStateManager.disableCull();
        GL11.glBegin(GL11.GL_QUADS);
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            int pixel=pixels[row*8+col];if((pixel>>>24)<16)continue;
            GL11.glColor4ub((byte)(pixel>>16),(byte)(pixel>>8),(byte)pixel,(byte)(pixel>>>24));
            double u0=-half+col*texel,u1=u0+texel,v1=half-(row+1)*texel,v0=v1+texel;
            quad(x,y,z,rightX,rightZ,upX,upY,upZ,u0,u1,v0,v1);
        }
        GL11.glEnd();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }
    private static void quad(double x,double y,double z,double rx,double rz,double ux,double uy,double uz,double u0,double u1,double v0,double v1){
        GL11.glVertex3d(x+rx*u0+ux*v0,y+uy*v0,z+rz*u0+uz*v0);
        GL11.glVertex3d(x+rx*u1+ux*v0,y+uy*v0,z+rz*u1+uz*v0);
        GL11.glVertex3d(x+rx*u1+ux*v1,y+uy*v1,z+rz*u1+uz*v1);
        GL11.glVertex3d(x+rx*u0+ux*v1,y+uy*v1,z+rz*u0+uz*v1);
    }
    void composite(HudLayout.Rect box,float opacity){
        GlStateManager.enableTexture2D();GlStateManager.bindTexture(target.framebufferTexture);GlStateManager.disableDepth();GlStateManager.disableCull();GlStateManager.disableAlpha();
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(1,771,1,771);GlStateManager.color(opacity,opacity,opacity,opacity);GL11.glColor4f(opacity,opacity,opacity,opacity);
        WorldRenderer vertices=Tessellator.getInstance().getWorldRenderer();vertices.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
        vertices.pos(box.x,box.y+box.h,0).tex(0,0).endVertex();vertices.pos(box.x+box.w,box.y+box.h,0).tex(1,0).endVertex();
        vertices.pos(box.x+box.w,box.y,0).tex(1,1).endVertex();vertices.pos(box.x,box.y,0).tex(0,1).endVertex();Tessellator.getInstance().draw();
    }
    static final class State {
        final boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),fog=GL11.glIsEnabled(GL11.GL_FOG),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),cullMode=GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int front=GL11.glGetInteger(GL11.GL_FRONT_FACE);
        final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        void restore(){
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(fog)GlStateManager.enableFog();else GlStateManager.disableFog();
            GlStateManager.depthMask(mask);GlStateManager.depthFunc(depthFunction);GlStateManager.cullFace(cullMode);
            GL11.glFrontFace(front);
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.bindTexture(binding);GL11.glColor4f(1,1,1,1);GlStateManager.color(1,1,1,1);
        }
    }
}
