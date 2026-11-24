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

    /** Clean skate base pose: a quiet side-on balance, with one short push layered on top. */
    static void skate(AnimPose p,float push){
        float k=pushContact(push);
        // A skater rides side-on, so both feet turn across the deck about the hip. Without the Y
        // rotation the player stands on the board like it is a bench, facing the way it rolls.
        // The back foot kicks once, then returns to the same planted base pose.
        p.set(AnimPose.RIGHT_LEG,0.06f+0.72f*k,1.02f,0.02f+0.15f*k);
        p.set(AnimPose.LEFT_LEG,-0.36f,1.04f,-0.08f);
        p.kneeRight=0.24f+0.15f*k;
        p.kneeLeft=0.25f;
        // The chest opens back toward the direction of travel while the feet stay across the
        // deck; the head is left to the game so it still follows where the player looks.
        p.set(AnimPose.BODY,0.07f,0.42f,0f);
        // Arms hang out from the body for balance with soft elbows, never folded inward.
        p.set(AnimPose.RIGHT_ARM,-0.12f,0.18f,0.42f);
        p.set(AnimPose.LEFT_ARM,-0.08f,-0.18f,-0.42f);
        p.elbowRight=0.22f;p.elbowLeft=0.22f;
    }

    static void skateCrouch(AnimPose p,float amount){
        p.add(AnimPose.BODY,0.16f*amount,0f,0f);
        p.add(AnimPose.RIGHT_LEG,-0.24f*amount,0f,0f);
        p.add(AnimPose.LEFT_LEG,-0.24f*amount,0f,0f);
        p.kneeRight+=0.42f*amount;p.kneeLeft+=0.42f*amount;
        p.add(AnimPose.RIGHT_ARM,-0.12f*amount,0f,0f);
        p.add(AnimPose.LEFT_ARM,-0.12f*amount,0f,0f);
    }

    static float fallStrength(double drop,double speed){
        // A normal 1.8.9 jump only rises about 1.25 blocks.
        return smooth(((float)drop-1.35f)/0.55f)*smooth(((float)speed-0.24f)/0.30f);
    }

    static void skateFall(AnimPose p,float amount){
        p.add(AnimPose.BODY,0.13f*amount,0f,0f);
        p.add(AnimPose.RIGHT_ARM,-0.22f*amount,0f,0.32f*amount);
        p.add(AnimPose.LEFT_ARM,-0.22f*amount,0f,-0.32f*amount);
        p.add(AnimPose.RIGHT_LEG,0.09f*amount,0f,0f);
        p.add(AnimPose.LEFT_LEG,0.09f*amount,0f,0f);
        p.kneeRight-=0.12f*amount;p.kneeLeft-=0.12f*amount;
        p.boardX=-0.24f*amount;
    }

    static void skateGrind(AnimPose p,float weight){
        if(weight<=0f)return;
        for(int leg:new int[]{AnimPose.RIGHT_LEG,AnimPose.LEFT_LEG}){
            p.angle[leg]+=(-0.16f-p.angle[leg])*weight;
            p.angle[leg+1]*=1f-weight;
            p.angle[leg+2]*=1f-weight;
        }
        p.kneeRight+=(0.34f-p.kneeRight)*weight;
        p.kneeLeft+=(0.34f-p.kneeLeft)*weight;
        p.add(AnimPose.BODY,0.09f*weight,-0.28f*weight,0f);
        p.add(AnimPose.RIGHT_ARM,-0.18f*weight,0f,0.29f*weight);
        p.add(AnimPose.LEFT_ARM,-0.18f*weight,0f,-0.29f*weight);
        p.boardY+=PI*0.5f*weight;
    }

    /** One ollie: crouch, tuck both knees, pop the deck. Trick 0 ollie, 1 kickflip, 2 shove-it. */
    static void ollie(AnimPose p,float phase,int trick){
        float tuck=pulse(phase,0.04f,0.38f,0.85f);
        float land=pulse(phase,0.72f,0.87f,1f);
        float flick=pulse(phase,0.12f,0.32f,0.64f);
        float spin=smooth((phase-0.16f)/0.62f);
        p.add(AnimPose.RIGHT_LEG,-0.48f*tuck-0.22f*land,0f,0f);
        p.add(AnimPose.LEFT_LEG,-0.60f*tuck-0.24f*land,0f,trick==1?-0.38f*flick:0f);
        p.kneeRight+=0.73f*tuck+0.42f*land;
        p.kneeLeft+=0.73f*tuck+0.44f*land;
        p.add(AnimPose.BODY,0.19f*tuck+0.17f*land,trick==2?-0.34f*flick:0f,0f);
        p.add(AnimPose.RIGHT_ARM,-0.42f*tuck,0f,0.26f*tuck);
        p.add(AnimPose.LEFT_ARM,-0.36f*tuck,0f,-0.26f*tuck);
        p.elbowRight+=0.3f*tuck;p.elbowLeft+=0.3f*tuck;
        p.boardDrop=0.16f*tuck;
        if(trick==0)p.boardX=-0.90f*pulse(phase,0f,0.28f,0.8f);
        else if(trick==1)p.boardZ=2f*PI*spin;
        else p.boardY=PI*spin;
    }

    static float pulse(float t,float start,float peak,float end){
        return smooth((t-start)/(peak-start))*(1f-smooth((t-peak)/(end-peak)));
    }

    static float pushContact(float t){
        return smooth((t-0.05f)/0.25f)*(1f-smooth((t-0.68f)/0.32f));
    }

    static float pushDip(float t){
        return smooth((t-0.18f)/0.24f)*(1f-smooth((t-0.64f)/0.36f));
    }

    static float place(float t){return smooth((t-0.32f)/0.30f);}
    static float mount(float t){return smooth((t-0.60f)/0.34f);}
    static float setDownZ(float t){return -0.78f*place(t)*(1f-smooth((t-0.67f)/0.25f));}

    static void deploy(AnimPose p,float t){
        float reach=pulse(t,0f,0.22f,0.55f);
        float lower=pulse(t,0.23f,0.56f,0.79f);
        p.set(AnimPose.RIGHT_ARM,1.48f*reach-0.48f*lower,0.10f*reach,0.15f+0.19f*reach);
        p.set(AnimPose.LEFT_ARM,-0.18f*lower,0f,-0.30f*lower);
        p.set(AnimPose.BODY,0.22f*lower,-0.20f*reach,0f);
        p.set(AnimPose.RIGHT_LEG,-0.26f*lower,0f,0.06f);
        p.set(AnimPose.LEFT_LEG,-0.28f*lower,0f,-0.06f);
        p.kneeRight=0.5f*lower;p.kneeLeft=0.54f*lower;
        p.elbowRight=-0.64f*reach-0.26f*lower;p.elbowLeft=-0.16f*lower;
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
