package otl.client;

/**
 * Pose curves. Time is in ticks and every angle is in radians; no game state is touched here.
 * Each clip is written against the vanilla model: X swings a limb forward/back, Z spreads it
 * sideways, Y turns it around the shoulder or hip.
 */
final class AnimClips {
    static final int EMOTES=3;
    private static final float PI=(float)Math.PI;

    private AnimClips(){}

    /** Smoothstep, clamped, used for fade-ins. */
    static float smooth(float t){
        t=Math.max(0f,Math.min(1f,t));
        return t*t*(3f-2f*t);
    }

    /** Everyone gets a slow breathing sway, added on top of the vanilla walk. */
    static void idle(AnimPose p,float t){
        p.add(AnimPose.HEAD,0.018f*sin(t*0.071f),0.03f*sin(t*0.043f),0.022f*sin(t*0.061f));
        p.add(AnimPose.BODY,0f,0.02f*sin(t*0.043f+1.2f),0.012f*sin(t*0.05f));
        p.add(AnimPose.RIGHT_ARM,0.02f*sin(t*0.063f),0f,0.035f*sin(t*0.051f));
        p.add(AnimPose.LEFT_ARM,-0.02f*sin(t*0.063f),0f,-0.035f*sin(t*0.051f));
    }

    /**
     * The deck stance: legs planted instead of walking, torso turned into the board, arms out for
     * balance. The back leg kicks while moving, which doubles as a push.
     */
    static void skate(AnimPose p,float swing,float amount,float t){
        float moving=Math.min(1f,amount*1.8f);
        float push=swing*1.3324f;
        p.set(AnimPose.RIGHT_LEG,0.08f+0.55f*moving*Math.max(0f,sin(push)),0f,0.12f);
        p.set(AnimPose.LEFT_LEG,-0.06f,0f,-0.12f);
        p.add(AnimPose.BODY,0.07f*moving,0.30f,0f);
        p.add(AnimPose.RIGHT_ARM,-0.42f,0.30f,0.62f);
        p.add(AnimPose.LEFT_ARM,0.18f,0.30f,-0.62f);
        p.add(AnimPose.HEAD,-0.06f,-0.30f,0f);
    }

    /** One ollie: crouch, tuck the legs, pop the deck. Trick 0 is a plain ollie, 1 a kickflip, 2 a shove-it. */
    static void ollie(AnimPose p,float phase,int trick){
        float s=sin(phase*PI);
        p.add(AnimPose.RIGHT_LEG,-0.50f*s,0f,0f);
        p.add(AnimPose.LEFT_LEG,-0.60f*s,0f,0f);
        p.add(AnimPose.BODY,0.20f*s,0f,0f);
        p.add(AnimPose.RIGHT_ARM,-0.35f*s,0f,0.15f*s);
        p.add(AnimPose.LEFT_ARM,-0.35f*s,0f,-0.15f*s);
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

    private static void wave(AnimPose p,float t){
        float w=sin(t*0.55f);
        p.set(AnimPose.HEAD,-0.04f,0.12f,0.14f);
        p.set(AnimPose.BODY,0.03f,0.04f,0.02f*w);
        p.set(AnimPose.RIGHT_ARM,-2.50f,0f,0.55f+0.30f*w);
        p.set(AnimPose.LEFT_ARM,0.06f,0f,-0.07f);
        p.set(AnimPose.RIGHT_LEG,0f,0f,0.05f);
        p.set(AnimPose.LEFT_LEG,0f,0f,-0.05f);
    }

    private static void dance(AnimPose p,float t){
        float w=sin(t*0.5f),c=cos(t*0.5f);
        p.set(AnimPose.HEAD,0.05f*w,0.25f*w,0.10f*c);
        p.set(AnimPose.BODY,0.05f,0.20f*w,0.06f*c);
        p.set(AnimPose.RIGHT_ARM,-0.70f-1.20f*Math.max(0f,w),0f,0.50f+0.20f*c);
        p.set(AnimPose.LEFT_ARM,-0.70f-1.20f*Math.max(0f,-w),0f,-0.50f+0.20f*c);
        p.set(AnimPose.RIGHT_LEG,0.28f*w,0f,0.10f);
        p.set(AnimPose.LEFT_LEG,-0.28f*w,0f,-0.10f);
    }

    private static void cheer(AnimPose p,float t){
        float b=Math.abs(sin(t*0.35f));
        p.set(AnimPose.HEAD,-0.18f,0f,0f);
        p.set(AnimPose.BODY,-0.05f+0.03f*b,0f,0f);
        p.set(AnimPose.RIGHT_ARM,-2.65f+0.15f*b,0f,0.30f-0.10f*b);
        p.set(AnimPose.LEFT_ARM,-2.65f+0.15f*b,0f,-0.30f+0.10f*b);
        p.set(AnimPose.RIGHT_LEG,0.10f,0f,0.08f);
        p.set(AnimPose.LEFT_LEG,0.10f,0f,-0.08f);
    }

    private static float sin(float v){return (float)Math.sin(v);}
    private static float cos(float v){return (float)Math.cos(v);}
}
