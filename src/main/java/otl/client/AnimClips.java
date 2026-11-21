package otl.client;

/**
 * Pose curves. Time is in ticks and every angle is in radians; no game state is touched here.
 * Each clip is written against the vanilla model: X swings a limb forward/back, Z spreads it
 * sideways, Y turns it around the shoulder or hip. Elbows and knees fold with X only, so a
 * bent limb never shears through the torso.
 */
final class AnimClips {
    static final int EMOTES=3;
    private static final float PI=(float)Math.PI;
    /** A relaxed standing bend: real people never lock their elbows. */
    private static final float STAND_ELBOW=0.16f;

    private AnimClips(){}

    /** Smoothstep, clamped, used for fades. */
    static float smooth(float t){
        t=Math.max(0f,Math.min(1f,t));
        return t*t*(3f-2f*t);
    }

    /**
     * The deck stance: legs planted instead of walking, knees bent for balance. The stance is
     * static - the player is balancing - and one push plays when they start moving, then the board
     * just rolls.
     */
    static void skate(AnimPose p,float push){
        float k=push<0f?0f:sin(push*PI);
        // Back foot at the tail, front foot forward: the back leg is the one that kicks.
        p.set(AnimPose.RIGHT_LEG,-0.14f+0.55f*k,0f,0.10f);
        p.set(AnimPose.LEFT_LEG,0.10f,0f,-0.10f);
        // A riding crouch, knees out over the board; the kick straightens the back leg.
        p.kneeRight=0.50f-0.44f*k;
        p.kneeLeft=0.60f;
        p.kneeRightZ=0.06f;
        p.kneeLeftZ=-0.06f;
        // Shoulders stay square to the board; only the arms counterbalance.
        p.add(AnimPose.RIGHT_ARM,-0.30f,0f,0.34f);
        p.add(AnimPose.LEFT_ARM,0.10f,0f,-0.34f);
        p.elbowRight=0.62f;
        p.elbowLeft=0.50f;
        // The body is replaced, never added: vanilla does not reset the body angles, so an
        // additive value here would keep growing every frame and the model would spin. The head is
        // left alone so it still follows where the player is looking.
        p.set(AnimPose.BODY,0.06f,0f,0f);
    }

    /** One ollie: crouch, tuck both knees, pop the deck. Trick 0 ollie, 1 kickflip, 2 shove-it. */
    static void ollie(AnimPose p,float phase,int trick){
        float s=sin(phase*PI);
        p.add(AnimPose.RIGHT_LEG,-0.34f*s,0f,0f);
        p.add(AnimPose.LEFT_LEG,-0.40f*s,0f,0f);
        p.kneeRight+=0.85f*s;
        p.kneeLeft+=0.95f*s;
        // Replaced: the body angle is not reset by vanilla, so adding to it would never unwind.
        p.set(AnimPose.BODY,0.06f+0.14f*s,0f,0f);
        p.add(AnimPose.RIGHT_ARM,-0.30f*s,0f,0.10f*s);
        p.add(AnimPose.LEFT_ARM,-0.30f*s,0f,-0.10f*s);
        p.elbowRight+=0.25f*s;p.elbowLeft+=0.25f*s;
        if(trick==0)p.boardX=-0.85f*s;
        else if(trick==1)p.boardZ=2f*PI*phase;
        else p.boardY=PI*phase;
    }

    /** Full-body emote; every part is replaced, so the clip owns the whole pose. */
    static void emote(AnimPose p,int id,float t){
        switch(Math.floorMod(id,EMOTES)){
            case 0:wave(p,t);break;
            case 1:dance(p,t);break;
            default:cheer(p,t);
        }
    }

    /** Arm up, elbow bent, forearm swinging from the elbow. The upper arm stays put. */
    private static void wave(AnimPose p,float t){
        float w=sin(t*0.55f);
        p.set(AnimPose.HEAD,-0.05f,0.10f,0.12f);
        p.set(AnimPose.BODY,0.02f,0f,0.02f*w);
        p.set(AnimPose.RIGHT_ARM,-2.45f,0f,0.42f);
        p.set(AnimPose.LEFT_ARM,0.05f,0f,-0.08f);
        p.set(AnimPose.RIGHT_LEG,0.02f,0f,0.06f);
        p.set(AnimPose.LEFT_LEG,-0.02f,0f,-0.06f);
        p.elbowRight=0.95f+0.55f*w;
        p.elbowRightZ=0.30f*w;
        p.elbowLeft=STAND_ELBOW;
        p.kneeRight=0.10f;p.kneeLeft=0.10f;
    }

    /** Alternating steps with the arms pumping: the knees and elbows carry the beat. */
    private static void dance(AnimPose p,float t){
        float w=sin(t*0.5f),c=cos(t*0.5f);
        p.set(AnimPose.HEAD,0.04f*w,0.18f*w,0.08f*c);
        p.set(AnimPose.BODY,0.04f,0f,0.05f*c);
        p.set(AnimPose.RIGHT_ARM,-0.55f-0.85f*Math.max(0f,w),0f,0.45f);
        p.set(AnimPose.LEFT_ARM,-0.55f-0.85f*Math.max(0f,-w),0f,-0.45f);
        p.set(AnimPose.RIGHT_LEG,0.30f*w,0f,0.08f);
        p.set(AnimPose.LEFT_LEG,-0.30f*w,0f,-0.08f);
        p.elbowRight=1.05f+0.45f*Math.max(0f,w);
        p.elbowLeft=1.05f+0.45f*Math.max(0f,-w);
        p.kneeRight=0.25f+0.45f*Math.max(0f,w);
        p.kneeLeft=0.25f+0.45f*Math.max(0f,-w);
    }

    /** Both arms overhead with a bounce; the elbows straighten as the arms go up. */
    private static void cheer(AnimPose p,float t){
        float b=Math.abs(sin(t*0.35f));
        p.set(AnimPose.HEAD,-0.16f,0f,0f);
        p.set(AnimPose.BODY,-0.04f+0.03f*b,0f,0f);
        p.set(AnimPose.RIGHT_ARM,-2.70f,0f,0.28f);
        p.set(AnimPose.LEFT_ARM,-2.70f,0f,-0.28f);
        p.set(AnimPose.RIGHT_LEG,0.06f,0f,0.07f);
        p.set(AnimPose.LEFT_LEG,0.06f,0f,-0.07f);
        p.elbowRight=0.55f-0.35f*b;
        p.elbowLeft=0.55f-0.35f*b;
        p.kneeRight=0.08f+0.20f*b;
        p.kneeLeft=0.08f+0.20f*b;
    }

    private static float sin(float v){return (float)Math.sin(v);}
    private static float cos(float v){return (float)Math.cos(v);}
}
