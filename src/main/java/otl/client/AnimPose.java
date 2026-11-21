package otl.client;

/**
 * Limb rotations for one frame, in radians. Each part is either offset from the vanilla pose
 * (add) or replaced outright (set): the skateboard and full-body emotes replace the limbs they
 * own so the vanilla walk cannot show through, while idle motion stays additive.
 *
 * <p>Elbows and knees are separate because the rendered limbs are split at the joint - the
 * vanilla model is one rigid box per limb and cannot bend.
 */
final class AnimPose {
    static final int HEAD=0,BODY=3,RIGHT_ARM=6,LEFT_ARM=9,RIGHT_LEG=12,LEFT_LEG=15;
    static final int PARTS=18;

    final float[] angle=new float[PARTS];
    final boolean[] replace=new boolean[PARTS];
    /** Elbow and knee bend, in radians; positive folds the limb backwards. */
    float elbowRight,elbowLeft,kneeRight,kneeLeft;
    /** Sideways splay of the lower limb, so a wave or a kick is not a flat hinge. */
    float elbowRightZ,elbowLeftZ,kneeRightZ,kneeLeftZ;
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
        elbowRight=elbowLeft=kneeRight=kneeLeft=0f;
        elbowRightZ=elbowLeftZ=kneeRightZ=kneeLeftZ=0f;
        board=false;boardX=boardY=boardZ=boardDrop=0f;
    }
}
