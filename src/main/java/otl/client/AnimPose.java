package otl.client;

/**
 * Limb rotations for one frame, in radians. Each part is either offset from the vanilla pose
 * (add) or replaced outright (set): the skateboard and full-body emotes replace the limbs they
 * own so the vanilla walk cannot show through, while idle motion stays additive.
 */
final class AnimPose {
    static final int HEAD=0,BODY=3,RIGHT_ARM=6,LEFT_ARM=9,RIGHT_LEG=12,LEFT_LEG=15;
    static final int PARTS=18;

    final float[] angle=new float[PARTS];
    final boolean[] replace=new boolean[PARTS];
    boolean board;
    float boardX,boardY,boardZ,boardDrop;

    void add(int part,float x,float y,float z){
        angle[part]+=x;angle[part+1]+=y;angle[part+2]+=z;
    }
    void set(int part,float x,float y,float z){
        angle[part]=x;angle[part+1]=y;angle[part+2]=z;
        replace[part]=replace[part+1]=replace[part+2]=true;
    }
    /** Reused between frames: one pose is pushed per player per frame, so nothing is allocated. */
    void reset(){
        java.util.Arrays.fill(angle,0f);
        java.util.Arrays.fill(replace,false);
        board=false;boardX=boardY=boardZ=boardDrop=0f;
    }
}
