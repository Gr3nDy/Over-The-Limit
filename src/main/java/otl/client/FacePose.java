package otl.client;

/** Per-frame face animation values; deterministic, pure math so it can be unit-tested. */
final class FacePose {
    final float lid;    // 0 open .. 1 closed
    final float wink;   // extra closure on the character's left eye while aiming a bow
    final float pupilX; // -1 .. +1, positive glances toward the player's right
    final float pupilY; // -1 .. +1, positive looks down
    final float brow;   // -1 furrow .. +1 raise
    final float wide;   // 0 normal .. 1 wide-eyed
    private static final float GLANCE_SLOT=80f,BLINK_TICKS=2.5f;
    private FacePose(float lid,float wink,float pupilX,float pupilY,float brow,float wide){
        this.lid=lid;this.wink=wink;this.pupilX=pupilX;this.pupilY=pupilY;this.brow=brow;this.wide=wide;
    }
    static FacePose calm(){return new FacePose(0,0,0,0,0,0);}

    static FacePose of(int seed,float age,float intervalTicks,boolean blinking,boolean glancing,boolean aiming,float sinceFire){
        float lid=blinking?blink(seed,age,Math.max(24,intervalTicks)):0;
        float px=0,py=0;
        if(glancing){
            int slot=(int)Math.floor(age/GLANCE_SLOT);
            float t=age/GLANCE_SLOT-slot;
            float[] from=aim(seed,slot-1),to=aim(seed,slot);
            float ease=t<0.12f?t/0.12f:t>0.88f?1-(t-0.88f)/0.12f:1;
            ease=ease*ease*(3-2*ease);
            px=from[0]+(to[0]-from[0])*ease;
            py=from[1]+(to[1]-from[1])*ease;
        }
        float brow=aiming?-0.8f:0;
        float wide=aiming?0.15f:0;
        float wink=aiming?1f:0;
        if(sinceFire>=0&&sinceFire<14)wide=Math.max(wide,1-sinceFire/14);
        return new FacePose(clamp(lid),clamp(wink),clamp(px),clamp(py),brow,clamp(wide));
    }

    /** Deterministic blink pulse; per-slot jitter and an occasional double blink. */
    static float blink(int seed,float age,float interval){
        float best=0;
        int first=(int)Math.floor((age-interval*0.4f)/interval)-1,last=(int)Math.floor(age/interval)+1;
        for(int k=first;k<=last;k++){
            int h=mix(seed,k);
            float start=k*interval+interval*0.75f*frac(h);
            float close=pulse(age-start);
            if((Math.floorMod(h>>>8,6))==0)close=Math.max(close,pulse(age-start-11));
            if(close>best)best=close;
        }
        return Math.min(1,best);
    }
    private static float pulse(float dt){
        if(dt<0)return 0;
        if(dt<BLINK_TICKS)return dt/BLINK_TICKS;
        if(dt<BLINK_TICKS*2)return 1;
        if(dt<BLINK_TICKS*3)return (BLINK_TICKS*3-dt)/BLINK_TICKS;
        return 0;
    }
    static int mix(int seed,int k){
        int h=seed*362437+k*1640531527;
        h^=h>>>15;h*=0x85EBCA6B;h^=h>>>13;
        return h;
    }
    private static float frac(int h){return Math.abs(h%1000)/1000f;}
    private static float[] aim(int seed,int slot){
        int h=mix(seed,slot);
        return new float[]{(Math.floorMod(h,7)-3)/3f*0.8f,(Math.floorMod(h>>>10,5)-2)/2f*0.5f};
    }
    private static float clamp(float v){return Math.max(-1,Math.min(1,v));}
}
