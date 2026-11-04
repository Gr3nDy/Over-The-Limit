package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws the animated face over the player's head: eyes, pupils, brows, blush and glow.
 * Original implementation for OTL. Eyes, pupils and brows replay the player's own face
 * pixels, so the eye colour follows the skin instead of a fixed white. Pupils are the
 * skin's own pupil cells, shifted for a glance. Lids use validated face skin. Blush is a
 * crisp light-pink bar on the outer skin layer. Head box: face texel (c,r) covers
 * x=[c-4,c-3], y=[r-8,r-7], z=-4; the outer layer is inflated and uses 9/8 of a texel
 * per cell. Both follow the Over the hat layer setting. Blush is a short, crisp bar under
 * each eye, mirrored outward on both cheeks. Sneaking hides the rig for that player.
 */
public final class FaceOverlay implements LayerRenderer<AbstractClientPlayer> {
    private static final int EYE=1,PUPIL=2,BROW=3,LID=4;
    private static final float HAT_SCALE=9f/8f;
    private static final float PUPIL_RANGE=0.5f;
    private static final int BROW_TINT=0xFF7A6758;
    private static final int BLUSH_COLOR=0xD0FFB6C1;
    private static final int GLOW_TINT=0x7896CCFF;
    private static final Map<Integer,List<Group>> GROUP_CACHE=new LinkedHashMap<Integer,List<Group>>(32,0.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Integer,List<Group>> e){return size()>128;}
    };
    private final RenderPlayer renderer;

    public FaceOverlay(RenderPlayer renderer){this.renderer=renderer;}
    public boolean shouldCombineTextures(){return false;}

    public void doRenderLayer(AbstractClientPlayer p,float swing,float swingAmount,float partial,float age,float yaw,float pitch,float scale){
        Minecraft mc=MinecraftAccess.get();
        boolean own=p==mc.thePlayer;
        boolean preview=FaceDoll.preview;
        if(!preview&&p.isSneaking())return;
        UtilitySettings.load();FaceConfig.load();
        if(!preview){
            if(!UtilitySettings.faceRig||!ServerScope.active())return;
            if(!own&&!UtilitySettings.otherFaces)return;
        }
        boolean blink=FaceConfig.allows(FaceConfig.blinking,own);
        boolean glance=FaceConfig.allows(FaceConfig.glances,own);
        boolean blush=FaceConfig.allows(FaceConfig.blush,own);
        boolean glow=FaceConfig.allows(FaceConfig.glow,own);
        boolean overHat=FaceConfig.allows(FaceConfig.hat,own);
        if(!blink&&!glance&&!blush&&!glow)return;

        ResourceLocation skin=p.getLocationSkin();
        int[] marks=FaceMarks.effective(skin);
        if(marks==null)return;
        List<Group> groups=cachedGroups(marks);
        if(groups.isEmpty())return;
        int[] face=SkinFace.facePixels(skin);
        if(face.length!=64)return;
        int[] hat=SkinFace.hatPixels(skin);
        boolean layered=hat.length==64;

        PlayerSignals.Data signal=PlayerSignals.of(p);
        FacePose pose=FacePose.of(p.getEntityId(),age+partial,FaceConfig.blinkSeconds*20,blink,glance,signal.aiming,p.ticksExisted+partial-signal.firedAt);

        GlStateManager.pushMatrix();
        try{
            renderer.getMainModel().bipedHead.postRender(scale);
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.color(1,1,1,1);
            int worldLight=p.getBrightnessForRender(partial);
            int packed=preview||!FaceConfig.naturalLight?0xF000F0:worldLight;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,packed%65536,packed/65536);
            Tessellator tessellator=Tessellator.getInstance();
            WorldRenderer wr=tessellator.getWorldRenderer();
            mc.getTextureManager().bindTexture(skin);
            wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            drawBase(wr,scale,marks,face,hat,groups,pose,overHat);
            tessellator.draw();
            if(glow){
                GlStateManager.disableLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
                GlStateManager.tryBlendFuncSeparate(770,1,1,0);
                wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                drawGlow(wr,scale,face,hat,groups,overHat);
                tessellator.draw();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);
                GlStateManager.enableLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,packed%65536,packed/65536);
            }
            if(blush){
                GlStateManager.disableLighting();
                GlStateManager.disableTexture2D();
                drawBlush(tessellator,wr,scale,groups,layered);
                GlStateManager.enableTexture2D();
                GlStateManager.enableLighting();
            }
            if(pose.lid>0.01f||pose.wink>0.01f){
                mc.getTextureManager().bindTexture(skin);
                wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                drawLids(wr,scale,marks,face,hat,groups,pose,overHat);
                tessellator.draw();
            }
        }finally{
            GlStateManager.popMatrix();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.color(1,1,1,1);
        }
    }

    private void drawBase(WorldRenderer wr,float s,int[] marks,int[] face,int[] hat,List<Group> groups,FacePose pose,boolean overHat){
        for(Group g:groups){
            Plane plane=new Plane(overHat);
            float t=plane.texel;
            float x0=plane.left+g.minC*t,x1=plane.left+(g.maxC+1)*t;
            float y0=plane.top+g.minR*t,y1=plane.top+(g.maxR+1)*t;
            float closure=pose.lid;
            if(isLeftEye(g))closure=Math.max(closure,pose.wink);
            int alpha=Math.round(255*Math.max(0,Math.min(1,1-closure)));
            int tint=0xFFFFFF|(alpha<<24);
            boolean[] pupils=pupilMask(g,marks,face,hat);

            // Stable skin-derived eye background, cell by cell. Pupil cells borrow the
            // eye pixel from their own row so a glance reveals skin, not a white block.
            for(int[] cell:g.cells){
                int i=cell[1]*8+cell[0];
                int bg=backgroundCell(g,pupils,marks,face,hat,i);
                int col=srcCol(face,hat,bg),row=bg/8;
                float bx=plane.left+cell[0]*t,by=plane.top+cell[1]*t;
                float u=col/64f,v=(8+row)/64f;
                uvQuad(wr,s,bx,by,bx+t,by+t,plane.z(1),u,v,u+1/64f,v+1/64f,tint);
            }

            float dx=pose.pupilX*PUPIL_RANGE*t,dy=pose.pupilY*PUPIL_RANGE*t;
            for(int[] cell:g.cells){
                int i=cell[1]*8+cell[0];
                if(!pupils[i])continue;
                int c=cell[0],r=cell[1];
                float ox=plane.left+c*t+dx,oy=plane.top+r*t+dy;
                float px0=Math.max(x0,ox),px1=Math.min(x1,ox+t);
                float py0=Math.max(y0,oy),py1=Math.min(y1,oy+t);
                if(px1-px0<0.01f||py1-py0<0.01f)continue;
                int col=srcCol(face,hat,i);
                float u=col/64f,v=(8+r)/64f;
                uvQuad(wr,s,px0,py0,px1,py1,plane.z(2),
                    u+(px0-ox)/t/64f,v+(py0-oy)/t/64f,
                    u+(px1-ox)/t/64f,v+(py1-oy)/t/64f,tint);
            }
        }
        for(int i=0;i<64;i++){
            if(marks[i]!=BROW)continue;
            boolean hatPlane=overHat;
            float t=hatPlane?HAT_SCALE:1f,left=hatPlane?-4.5f:-4f,top=hatPlane?-8.5f:-8f,base=hatPlane?-4.5f:-4f;
            float lift=(-pose.brow*0.25f-pose.wide*0.1f)*(hatPlane?HAT_SCALE:1f);
            int c=i%8,r=i/8;
            float bx=left+c*t,by=top+r*t+lift;
            int col=srcCol(face,hat,i);
            float u=col/64f,v=(8+r)/64f;
            uvQuad(wr,s,bx,by,bx+t,by+t,base-0.014f,u,v,u+1/64f,v+1/64f,BROW_TINT);
        }
    }

    /** A short light-pink bar under each eye, shifted outward so both cheeks mirror. */
    private void drawBlush(Tessellator tessellator,WorldRenderer wr,float s,List<Group> groups,boolean layered){
        Plane plane=new Plane(layered);
        float t=plane.texel,h=t*0.5f;
        float left=plane.left,right=plane.left+8*t,chin=plane.top+8*t;
        for(Group g:groups){
            if(g.maxR>=7)continue;
            float top=plane.top+(g.maxR+0.5f)*t;
            if(top>=chin-0.01f)continue;
            float x0=left+g.minC*t,x1=left+(g.maxC+1)*t;
            boolean rightEye=(g.minC+g.maxC)<7;
            float shift=rightEye?-h:h;
            float bx0=Math.max(left,x0+shift),bx1=Math.min(right,x1+shift);
            float by0=top,by1=Math.min(chin,by0+h);
            if(bx1-bx0<0.01f||by1-by0<0.01f)continue;
            wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            flatQuad(wr,s,bx0,by0,bx1,by1,plane.z(3),BLUSH_COLOR);
            tessellator.draw();
        }
    }

    private void drawGlow(WorldRenderer wr,float s,int[] face,int[] hat,List<Group> groups,boolean overHat){
        for(Group g:groups){
            Plane plane=new Plane(overHat);
            float t=plane.texel;
            for(int[] cell:g.cells){
                int i=cell[1]*8+cell[0];
                int col=srcCol(face,hat,i);
                float bx=plane.left+cell[0]*t,by=plane.top+cell[1]*t;
                float u=col/64f,v=(8+cell[1])/64f;
                uvQuad(wr,s,bx,by,bx+t,by+t,plane.z(4),u,v,u+1/64f,v+1/64f,GLOW_TINT);
            }
        }
    }

    private void drawLids(WorldRenderer wr,float s,int[] marks,int[] face,int[] hat,List<Group> groups,FacePose pose,boolean overHat){
        float open=Math.max(0,Math.min(1,pose.lid-0.12f*pose.wide));
        int sharedRows=0;
        for(Group g:groups)sharedRows=Math.max(sharedRows,g.maxR-g.minR+1);
        float sharedUpper=Math.round(Math.min(1,open)*sharedRows*2)/2f;
        for(Group g:groups){
            Plane plane=new Plane(overHat);
            float t=plane.texel;
            float lid=open;
            if(isLeftEye(g))lid=Math.max(lid,pose.wink); // character's left eye (screen right)
            float upper=sharedUpper;
            if(lid>open)upper=Math.round(Math.min(1,lid)*sharedRows*2)/2f;
            upper=Math.min(upper,g.maxR-g.minR+1); // keep the lid within this eye
            if(upper<=0.01f)continue;
            int skin=manualLidCell(g,marks,face);
            if(skin<0)skin=referenceLidCell(g,marks,face);
            if(skin<0)skin=sharedSkinCell(Collections.singletonList(g),marks,face);
            int col=srcCol(face,hat,skin),row=skin/8;
            float x0=plane.left+g.minC*t,x1=plane.left+(g.maxC+1)*t;
            float y0=plane.top+g.minR*t,y1=y0+upper*t;
            float u=col/64f,v=(8+row)/64f;
            uvQuad(wr,s,x0,y0,x1,y1,plane.z(5),u,v,u+1/64f,v+1/64f,0xFFF2F2F2);
        }
    }

    /** Pupil cells: the marked Pupil cells, or the darkest column when only Eye marks exist. */
    private static boolean[] pupilMask(Group g,int[] marks,int[] face,int[] hat){
        boolean[] mask=new boolean[64];
        boolean any=false;
        for(int[] cell:g.cells){
            int i=cell[1]*8+cell[0];
            if(marks[i]==PUPIL){mask[i]=true;any=true;}
        }
        if(any)return mask;
        int bestC=-1;
        long bestSum=Long.MAX_VALUE;
        for(int c=g.minC;c<=g.maxC;c++){
            long sum=0;
            for(int[] cell:g.cells){
                if(cell[0]!=c)continue;
                sum+=lum(srcPx(face,hat,cell[1]*8+cell[0]));
            }
            // Ties go to the inner column, which reads as a pupil rather than a wall eye.
            boolean tie=sum==bestSum&&bestC>=0;
            int inner=(g.minC+g.maxC)<7?g.maxC:g.minC;
            if(bestC<0||sum<bestSum||(tie&&c==inner)){bestSum=sum;bestC=c;}
        }
        if(bestC>=0)for(int[] cell:g.cells)if(cell[0]==bestC)mask[cell[1]*8+cell[0]]=true;
        return mask;
    }

    /** Background pixel for a cell: its own pixel, or the row's eye pixel under a pupil. */
    private static int backgroundCell(Group g,boolean[] pupils,int[] marks,int[] face,int[] hat,int i){
        if(!pupils[i])return i;
        int r=i/8,best=-1,bestLum=-1;
        for(int[] cell:g.cells){
            int j=cell[1]*8+cell[0];
            if(pupils[j])continue;
            if(cell[1]==r)return j;
            int l=lum(srcPx(face,hat,j));
            if(l>bestLum){bestLum=l;best=j;}
        }
        return best<0?i:best;
    }

    /** One validated skin pixel shared by both lids, so the eyes close consistently. */
    private static int sharedSkinCell(List<Group> groups,int[] marks,int[] face){
        int tone=skinTone(face),best=-1,bestScore=Integer.MAX_VALUE;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            int i=r*8+c,p=face[i];
            if(marks[i]!=0||(p>>>24)<200)continue;
            int score=colourDistance(p,tone)*8;
            for(Group g:groups){
                int cx=g.minC+g.maxC,cy=g.minR+g.maxR;
                int d=(2*c-cx)*(2*c-cx)+(2*r-cy)*(2*r-cy);
                if(r<g.minR) d+=12;
                score+=d;
            }
            if(score<bestScore){bestScore=score;best=i;}
        }
        if(best>=0)return best;
        return Math.max(0,Math.min(63,groups.get(0).maxR*8+groups.get(0).minC));
    }

    /** A manually painted Lid mark selects the exact skin pixel used for that eye. */
    private static int manualLidCell(Group g,int[] marks,int[] face){
        int best=-1,bestD=Integer.MAX_VALUE;
        int cx=g.minC+g.maxC,cy=g.minR+g.maxR;
        for(int i=0;i<64;i++)if(marks[i]==LID&&(face[i]>>>24)>=64){
            int c=i%8,r=i/8,d=(2*c-cx)*(2*c-cx)+(2*r-cy)*(2*r-cy);
            if((c<4)!=(g.minC+g.maxC<7))continue;
            if(d<bestD){bestD=d;best=i;}
        }
        return best;
    }

    /** Reference-style automatic lid source: nearest validated skin pixel above the eye. */
    private static int referenceLidCell(Group g,int[] marks,int[] face){
        int tone=skinTone(face),center=g.minC+g.maxC,best=-1,bestScore=Integer.MAX_VALUE;
        for(int r=g.minR-1;r>=0;r--){
            for(int c=g.minC;c<=g.maxC;c++){
                int i=r*8+c,p=face[i];
                if(marks[i]!=0||(p>>>24)<200||colourDistance(p,tone)>40)continue;
                int score=Math.abs(2*c-center)*2+colourDistance(p,tone);
                if(score<bestScore){bestScore=score;best=i;}
            }
            if(best>=0)return best;
        }
        return -1;
    }

    private static int skinTone(int[] plane){
        Map<Integer,int[]> buckets=new HashMap<>();
        for(int i=24;i<64;i++){
            int p=plane[i];if((p>>>24)<64)continue;
            int key=((p>>16&255)>>3)<<10|((p>>8&255)>>3)<<5|(p&255)>>3;
            int[] e=buckets.get(key);if(e==null){e=new int[]{0,0,0,0};buckets.put(key,e);}
            e[0]++;e[1]+=p>>16&255;e[2]+=p>>8&255;e[3]+=p&255;
        }
        int[] best=null;for(int[] e:buckets.values())if(best==null||e[0]>best[0])best=e;
        return best==null?0xFFC8A078:0xFF000000|(best[1]/best[0])<<16|(best[2]/best[0])<<8|best[3]/best[0];
    }
    private static int colourDistance(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}

    private static int skinCell(Group g,int[] marks,int[] face){
        int cx=g.minC+g.maxC,cy=g.minR+g.maxR;
        int best=-1,bestD=Integer.MAX_VALUE;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            int i=r*8+c;
            if(marks[i]!=0||(face[i]>>>24)<200)continue;
            int d=(2*c-cx)*(2*c-cx)+(2*r-cy)*(2*r-cy);
            if(r<g.minR)d+=6; // prefer a temple or cheek over the forehead/hair
            if(d<bestD){bestD=d;best=i;}
        }
        if(best>=0)return best;
        for(int r=Math.min(7,g.maxR+1);r>=0;r--){
            int i=r*8+Math.max(0,Math.min(7,(g.minC+g.maxC)/2));
            if((face[i]>>>24)>=64)return i;
        }
        return Math.max(0,Math.min(63,g.maxR*8+g.minC));
    }

    private static int srcPx(int[] face,int[] hat,int i){
        int f=face[i];
        if((f>>>24)>=64)return f;
        if(hat.length==64&&(hat[i]>>>24)>=64)return hat[i];
        return f;
    }

    private static int srcCol(int[] face,int[] hat,int i){
        if((face[i]>>>24)>=64)return 8+i%8;
        if(hat.length==64&&(hat[i]>>>24)>=64)return 40+i%8;
        return 8+i%8;
    }

    private static boolean isLeftEye(Group g){return g.minC+g.maxC>=7;}

    private static final class Plane {
        final float texel,left,top,base;
        Plane(boolean hat){texel=hat?HAT_SCALE:1f;left=hat?-4.5f:-4f;top=hat?-8.5f:-8f;base=hat?-4.5f:-4f;}
        float z(int pass){return base-pass*0.006f;}
    }

    private static void uvQuad(WorldRenderer wr,float s,float x0,float y0,float x1,float y1,float z,float u0,float v0,float u1,float v1,int argb){
        int a=argb>>>24&255,r=argb>>16&255,g=argb>>8&255,b=argb&255;
        wr.pos(x0*s,y0*s,z*s).tex(u0,v0).color(r,g,b,a).normal(0,0,-1).endVertex();
        wr.pos(x1*s,y0*s,z*s).tex(u1,v0).color(r,g,b,a).normal(0,0,-1).endVertex();
        wr.pos(x1*s,y1*s,z*s).tex(u1,v1).color(r,g,b,a).normal(0,0,-1).endVertex();
        wr.pos(x0*s,y1*s,z*s).tex(u0,v1).color(r,g,b,a).normal(0,0,-1).endVertex();
    }

    /** Flat colour quad used for the cheek bar. */
    private static void flatQuad(WorldRenderer wr,float s,float x0,float y0,float x1,float y1,float z,int argb){
        int a=argb>>>24&255,r=argb>>16&255,g=argb>>8&255,b=argb&255;
        wr.pos(x0*s,y0*s,z*s).color(r,g,b,a).endVertex();
        wr.pos(x1*s,y0*s,z*s).color(r,g,b,a).endVertex();
        wr.pos(x1*s,y1*s,z*s).color(r,g,b,a).endVertex();
        wr.pos(x0*s,y1*s,z*s).color(r,g,b,a).endVertex();
    }

    private static int lum(int c){return ((c>>16&255)*30+(c>>8&255)*59+(c&255)*11)/100;}

    private static final class Group {
        final List<int[]> cells=new ArrayList<>();
        int minR=8,maxR=-1,minC=8,maxC=-1;
    }

    private static List<Group> cachedGroups(int[] marks){
        int key=Arrays.hashCode(marks);
        List<Group> cached=GROUP_CACHE.get(key);
        if(cached!=null)return cached;
        List<Group> made=groups(marks);
        GROUP_CACHE.put(key,made);
        return made;
    }

    private static List<Group> groups(int[] marks){
        boolean[] seen=new boolean[64];
        List<Group> out=new ArrayList<>();
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            int i=r*8+c,kind=marks[i];
            if((kind!=EYE&&kind!=PUPIL)||seen[i])continue;
            Group g=new Group();
            Deque<Integer> stack=new ArrayDeque<>();
            stack.push(i);seen[i]=true;
            while(!stack.isEmpty()){
                int cell=stack.pop();
                int cr=cell/8,cc=cell%8;
                g.cells.add(new int[]{cc,cr,marks[cell]});
                g.minR=Math.min(g.minR,cr);g.maxR=Math.max(g.maxR,cr);
                g.minC=Math.min(g.minC,cc);g.maxC=Math.max(g.maxC,cc);
                int[][] near={{cc-1,cr},{cc+1,cr},{cc,cr-1},{cc,cr+1}};
                for(int[] n:near){
                    if(n[0]<0||n[0]>7||n[1]<0||n[1]>7)continue;
                    int j=n[1]*8+n[0],k=marks[j];
                    if(!seen[j]&&(k==EYE||k==PUPIL)){seen[j]=true;stack.push(j);}
                }
            }
            out.add(g);
        }
        return out;
    }
}
