package otl.client;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Compact, colored 3D quads; fixed orthographic projection never fits/scales a map to its viewport. */
final class MinimapModel {
    static final double PIXELS_PER_BLOCK=2;
    static double screenX(double x,double z){return (x-z)*.7071067811865476;}
    static double screenY(double x,double y,double z){return (x+z)*.408248290463863-y*.816496580927726;}
    static double screenX(double x,double z,double angle){double a=Math.toRadians(angle);return Math.cos(a)*x-Math.sin(a)*z;}
    static double screenY(double x,double y,double z,double angle){double a=Math.toRadians(angle);return (Math.sin(a)*x+Math.cos(a)*z)*.577350269189626-y*.816496580927726;}
    static double angle(boolean rotate,float yaw){return rotate?180-yaw:45;}
    static final class Face {
        final float[] xyz=new float[12];final int color,axis,sign;final double plane;
        Face(double[] p,int u,int v,int height,int width,int axis,int rgb,boolean positive){
            this.axis=axis;this.sign=positive?1:-1;plane=p[axis];
            double shade=axis==1?1:axis==0?.82:.65;
            color=0xFF000000|((int)((rgb>>16&255)*shade)<<16)|((int)((rgb>>8&255)*shade)<<8)|(int)((rgb&255)*shade);
            for(int i=0;i<4;i++){
                int corner=positive?i:3-i;
                double[] point=p.clone();if(corner==1||corner==2)point[u]+=height;if(corner>=2)point[v]+=width;
                for(int d=0;d<3;d++)xyz[i*3+d]=(float)point[d];
            }
        }
    }
    final List<Face> faces;
    final double[] center=new double[3];final double radius;
    /** Rotation-safe bound on projected distance from the center; used to fit the whole map. */
    final double screenRadius;
    private MinimapModel(List<Face> faces){
        this.faces=faces;
        double radiusSquared=0;
        for(int d=0;d<3;d++){
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(Face face:faces)for(int i=0;i<4;i++){low=Math.min(low,face.xyz[i*3+d]);high=Math.max(high,face.xyz[i*3+d]);}
            center[d]=faces.isEmpty()?0:(low+high)/2;
            if(!faces.isEmpty())radiusSquared+=(high-low)*(high-low)/4;
        }
        radius=Math.sqrt(radiusSquared);
        double projected=0;
        for(Face face:faces)for(int i=0;i<4;i++){
            double dx=face.xyz[i*3]-center[0],dy=face.xyz[i*3+1]-center[1],dz=face.xyz[i*3+2]-center[2];
            projected=Math.max(projected,Math.sqrt(dx*dx+dz*dz)+Math.abs(dy)*.816496580927726);
        }
        screenRadius=projected;
    }
    static MinimapModel read(InputStream source)throws IOException{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(source)))){
            if(in.readInt()!=0x4F544C4D||in.readUnsignedByte()!=1)throw new IOException("Unsupported minimap model");
            int x=in.readInt(),y=in.readInt(),z=in.readInt(),count=in.readInt();
            if(Math.abs((long)x)>30000000||Math.abs((long)y)>30000000||Math.abs((long)z)>30000000||count<1||count>2000000)throw new IOException("Invalid minimap model bounds/count");
            List<Face> faces=new ArrayList<>();
            for(int i=0;i<count;i++){
                double[] point={x+(double)in.readUnsignedShort(),y+(double)in.readUnsignedShort(),z+(double)in.readUnsignedShort()};
                int height=in.readUnsignedShort(),width=in.readUnsignedShort(),axis=in.readUnsignedByte(),positive=in.readUnsignedByte(),rgb=in.readInt();
                if(height==0||width==0||axis>2||positive>1)throw new IOException("Invalid minimap quad");
                faces.add(new Face(point,(axis+1)%3,(axis+2)%3,height,width,axis,rgb,positive==1));
            }
            if(in.read()!=-1)throw new IOException("Unexpected minimap model data");
            return new MinimapModel(faces);
        }
    }
}
