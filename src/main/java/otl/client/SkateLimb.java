package otl.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/** A continuous bend of the original limb mesh and UV map. */
final class SkateLimb extends ModelRenderer {
    private final List<PositionTextureVertex[]> skin;
    private final float[] points=new float[12];
    final float minX,maxX,bottom,minZ,maxZ;
    boolean insideBoot,insideLeggings;
    float reachScale=1f;

    SkateLimb(ModelBase model,ModelRenderer bone){
        super(model);
        ModelBox box=bone.cubeList.get(0);
        rotationPointY=(box.posY1+box.posY2)*0.5f;
        minX=box.posX1;maxX=box.posX2;bottom=box.posY2;minZ=box.posZ1;maxZ=box.posZ2;
        skin=mesh(bone);
        bone.addChild(this);
    }

    private static List<PositionTextureVertex[]> mesh(ModelRenderer part){
        ModelBox box=part.cubeList.remove(0);
        TexturedQuad[] faces=ReflectionHelper.getPrivateValue(ModelBox.class,box,"quadList","field_78254_i");
        List<PositionTextureVertex[]> mesh=new ArrayList<>();
        for(TexturedQuad face:faces){
            PositionTextureVertex[] original=face.vertexPositions;
            double top=original[0].vector3D.yCoord,bottom=top;
            for(PositionTextureVertex vertex:original){
                top=Math.min(top,vertex.vector3D.yCoord);
                bottom=Math.max(bottom,vertex.vector3D.yCoord);
            }
            if(top==bottom){mesh.add(original);continue;}
            PositionTextureVertex[] opposite=new PositionTextureVertex[4];
            for(int i=0;i<4;i++){
                PositionTextureVertex start=original[i];
                for(PositionTextureVertex end:original){
                    if(end.vector3D.xCoord==start.vector3D.xCoord&&end.vector3D.zCoord==start.vector3D.zCoord
                        &&end.vector3D.yCoord!=start.vector3D.yCoord){opposite[i]=end;break;}
                }
            }
            for(int strip=0;strip<6;strip++){
                PositionTextureVertex[] quad=new PositionTextureVertex[4];
                for(int i=0;i<4;i++){
                    PositionTextureVertex start=original[i],end=opposite[i];
                    // Inflated armor and bare skin need identical interior bend rings at the knee.
                    double y=ringY(top,bottom,box.posY1,box.posY2,start.vector3D.yCoord==top?strip:strip+1);
                    float t=(float)((y-start.vector3D.yCoord)/(end.vector3D.yCoord-start.vector3D.yCoord));
                    quad[i]=new PositionTextureVertex((float)start.vector3D.xCoord,
                        (float)y,
                        (float)start.vector3D.zCoord,
                        start.texturePositionX+(end.texturePositionX-start.texturePositionX)*t,
                        start.texturePositionY+(end.texturePositionY-start.texturePositionY)*t);
                }
                mesh.add(quad);
            }
        }
        return mesh;
    }

    @Override public void render(float scale){
        if(isHidden||!showModel)return;
        Tessellator tess=Tessellator.getInstance();
        WorldRenderer out=tess.getWorldRenderer();
        out.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_NORMAL);
        float sx=(float)Math.sin(rotateAngleX),cx=(float)Math.cos(rotateAngleX);
        float sz=(float)Math.sin(rotateAngleZ),cz=(float)Math.cos(rotateAngleZ);
        draw(out,skin,scale,sx,cx,sz,cz);
        tess.draw();
    }

    static float hingeWeight(float distance){return AnimClips.smooth((distance+2f)/4f);}
    static float bootInset(float distanceFromSole){return 1f-AnimClips.smooth(distanceFromSole/4f);}
    static double ringY(double top,double bottom,float first,float last,int ring){
        if(ring==0)return top;
        if(ring==6)return bottom;
        return first+(last-first)*ring/6f;
    }

    private void draw(WorldRenderer out,List<PositionTextureVertex[]> faces,float scale,
        float sx,float cx,float sz,float cz){
        for(PositionTextureVertex[] face:faces){
            for(int i=0;i<4;i++){
                PositionTextureVertex vertex=face[i];
                float x=(float)vertex.vector3D.xCoord,y=(float)vertex.vector3D.yCoord-rotationPointY;
                float z=(float)vertex.vector3D.zCoord;
                if(insideLeggings){
                    float inset=0.18f*(1f-AnimClips.smooth(Math.abs(y)/4f));
                    x+=((minX+maxX)*0.5f-x)*inset;
                    z+=((minZ+maxZ)*0.5f-z)*inset;
                }
                if(insideBoot){
                    // Keep a skin surface for chainmail holes, but tuck the toe and heel under the boot.
                    float inset=bootInset(bottom-(float)vertex.vector3D.yCoord);
                    x+=((minX+maxX)*0.5f-x)*0.32f*inset;
                    z+=((minZ+maxZ)*0.5f-z)*0.32f*inset;
                    y-=0.85f*inset;
                }
                float bend=hingeWeight(y);
                y*=reachScale;
                float ry=cx*y-sx*z;
                points[i*3]=(x+(cz*x-sz*ry-x)*bend)*scale;
                points[i*3+1]=(rotationPointY*reachScale+y+(sz*x+cz*ry-y)*bend)*scale;
                points[i*3+2]=(z+(sx*y+cx*z-z)*bend)*scale;
            }
            float ax=points[3]-points[0],ay=points[4]-points[1],az=points[5]-points[2];
            float bx=points[6]-points[0],by=points[7]-points[1],bz=points[8]-points[2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<1e-9f)continue;
            for(int i=0;i<4;i++)out.pos(points[i*3],points[i*3+1],points[i*3+2])
                .tex(face[i].texturePositionX,face[i].texturePositionY)
                .normal(nx/length,ny/length,nz/length).endVertex();
        }
    }
}
