package otl.client;

import java.util.*;
import java.util.regex.*;

/** Only explicit server reward lines count; player chat and death lines never award kills. */
final class KillConfirmRules {
    private static final Pattern REWARD=Pattern.compile("^\\+([0-9]{1,6})\\s+raindrops\\s*\\|\\s*(Kill Assist|Kill)$",Pattern.CASE_INSENSITIVE);
    // shortcut: only observed English countdowns establish match-first kills; extend for other PGM start messages.
    private static final Pattern MATCH_START=Pattern.compile("^(?:The )?Match starts in [0-9]+ seconds?[.!]?$",Pattern.CASE_INSENSITIVE);
    private static final long PAIR_NANOS=2000000000L,MULTI_NANOS=3000000000L,DUEL_NANOS=8000000000L;
    static final long POP_NANOS=380000000L,ROW_NANOS=650000000L,REVEAL_NANOS=220000000L,SLIDE_NANOS=130000000L;
    static final float FLAWLESS_HEALTH=18F;
    static final int LONG_SHOT_BLOCKS=30,POINT_BLANK_BLOCKS=3,EXTREME_RANGE_BLOCKS=60,DOMINATION_KILLS=5,SHUTDOWN_STREAK=5,UNDERDOG_KILLS=10,COMEBACK_DEATHS=3,MAX_BONUS_RANK=3;
    private static final String[] CHAIN_LABELS={"DOUBLE KILL","TRIPLE KILL","QUAD KILL","PENTA KILL","HEXA KILL","HEPTA KILL","OCTA KILL","NONA KILL"};
    static final class Reward {
        final int points;final boolean assist;
        boolean bow,revenge,survivor,lastBreath,flawless,longShot,shutdown,underdog,firstBlood,comeback,airborne,jumpShot,sneak;
        boolean pointBlank,extremeRange,domination,redemption,cleanSweep,demolition,brawler,hotHand,mixedArsenal;
        int multi;
        long time;
        int streakAt;
        String victim;
        Reward(int points,boolean assist){this.points=points;this.assist=assist;}
        String actionLabel(){return assist?"KILL ASSIST":bow?"BOW KILL":"KILL";}
        List<String> labels(int streak){
            List<String> result=new ArrayList<>();result.add(actionLabel());
            if(assist)return result;
            List<String> tags=new ArrayList<>();
            if(shutdown)tags.add("SHUTDOWN");
            if(redemption)tags.add("REDEMPTION");else if(revenge)tags.add("REVENGE");
            if(flawless)tags.add("FLAWLESS");
            if(pointBlank)tags.add("POINT BLANK");else if(extremeRange)tags.add("EXTREME RANGE");else if(longShot)tags.add("LONG SHOT");
            if(domination)tags.add("DOMINATION");
            if(demolition)tags.add("DEMOLITION");
            if(cleanSweep)tags.add("CLEAN SWEEP");
            if(brawler)tags.add("BRAWLER");
            if(hotHand)tags.add("HOT HAND");
            if(mixedArsenal)tags.add("MIXED ARSENAL");
            String chain=chainLabel(multi);if(!chain.isEmpty())tags.add(chain);
            if(lastBreath)tags.add("LAST BREATH");else if(survivor)tags.add("SURVIVOR");
            if(underdog)tags.add("UNDERDOG");
            if(firstBlood)tags.add("FIRST BLOOD");
            if(comeback)tags.add("COMEBACK");
            if(jumpShot)tags.add("JUMPSHOT");
            if(airborne)tags.add("AIRBORNE");
            if(sneak)tags.add("SNEAK");
            for(int i=0;i<Math.min(MAX_BONUS_RANK,tags.size());i++){
                String tag=tags.get(i);result.add(tag);
                if(multi>=10&&tag.equals(chain))result.add(multi+" KILL CHAIN");
            }
            String counter=streakLabel(streak,false);
            if(!counter.isEmpty()){
                result.add(counter);
                if(streak>=5)result.add(streak+" KILL STREAK");
            }
            return result;
        }
    }
    /** Facts captured at the kill, attached to whichever reward line follows it. */
    private static final class Tags {
        final long time;final String victim;final boolean bow,revenge,survivor,lastBreath,flawless,longShot,shutdown,underdog,firstBlood,comeback,airborne,jumpShot,sneak;final int multi;
        final boolean pointBlank,extremeRange,domination,redemption,cleanSweep,demolition,brawler,hotHand,mixedArsenal;
        Tags(long time,String victim,boolean bow,boolean revenge,boolean survivor,boolean lastBreath,boolean flawless,boolean longShot,boolean shutdown,boolean underdog,boolean firstBlood,boolean comeback,boolean airborne,boolean jumpShot,boolean sneak,int multi,boolean pointBlank,boolean extremeRange,boolean domination,boolean redemption,boolean cleanSweep,boolean demolition,boolean brawler,boolean hotHand,boolean mixedArsenal){
            this.victim=victim;
            this.time=time;this.bow=bow;this.revenge=revenge;this.survivor=survivor;this.lastBreath=lastBreath;this.flawless=flawless;this.longShot=longShot;
            this.shutdown=shutdown;this.underdog=underdog;this.firstBlood=firstBlood;this.comeback=comeback;this.airborne=airborne;this.jumpShot=jumpShot;this.sneak=sneak;this.multi=multi;
            this.pointBlank=pointBlank;this.extremeRange=extremeRange;this.domination=domination;
            this.redemption=redemption;this.cleanSweep=cleanSweep;this.demolition=demolition;this.brawler=brawler;
            this.hotHand=hotHand;this.mixedArsenal=mixedArsenal;
        }
        void apply(Reward reward){
            reward.victim=victim;
            reward.bow=bow;reward.revenge=revenge;reward.survivor=survivor;reward.lastBreath=lastBreath;reward.flawless=flawless;reward.longShot=longShot;
            reward.shutdown=shutdown;reward.underdog=underdog;reward.firstBlood=firstBlood;reward.comeback=comeback;reward.airborne=airborne;reward.jumpShot=jumpShot;reward.sneak=sneak;reward.multi=multi;
            reward.pointBlank=pointBlank;reward.extremeRange=extremeRange;reward.domination=domination;
            reward.redemption=redemption;reward.cleanSweep=cleanSweep;reward.demolition=demolition;reward.brawler=brawler;
            reward.hotHand=hotHand;reward.mixedArsenal=mixedArsenal;
        }
    }
    static Reward parse(String text){
        if(text==null||text.length()>256)return null;
        Matcher m=REWARD.matcher(ChatRules.plain(text).trim());
        return m.matches()?new Reward(Integer.parseInt(m.group(1)),m.group(2).equalsIgnoreCase("Kill Assist")):null;
    }
    /** One visible white-stack line and the moment it starts revealing. */
    static final class Row {
        final String text,victim;final long start;
        Row(String text,long start){this(text,start,null);}
        Row(String text,long start,String victim){this.text=text;this.start=start;this.victim=victim;}
    }
    static final class RespawnPosition {
        private boolean known,pending;
        private double lastX,lastY,lastZ,deathX,deathY,deathZ;
        void died(double x,double y,double z){
            if(pending)return;
            deathX=known?lastX:x;deathY=known?lastY:y;deathZ=known?lastZ:z;pending=true;
        }
        boolean moved(double x,double y,double z){
            // shortcut: the fallback requires a >4-block jump after death; explicit server signals are needed for closer spawns.
            boolean teleported=pending&&known&&distance(x,y,z,lastX,lastY,lastZ)>16&&distance(x,y,z,deathX,deathY,deathZ)>16;
            lastX=x;lastY=y;lastZ=z;known=true;return teleported;
        }
        private static double distance(double x,double y,double z,double a,double b,double c){return (x-a)*(x-a)+(y-b)*(y-b)+(z-c)*(z-c);}
        void resumed(){pending=false;}
        void clear(){known=pending=false;}
    }
    static final class State {
        int streak,streakExp,lifeKills,lifeAssists,recapKills,recapAssists;
        long lifeKillExp,lifeAssistExp,recapExp,recapKillExp,recapAssistExp;
        Reward reward;long receivedAt,recapAt;boolean awaitingRespawn;
        String lastAttacker;
        int localKills,deathsInARow,multi;
        boolean matchStartSeen,firstKillTaken;
        long lastKillAt,deathAt;
        String duelVictim;float duelHealth;boolean duelClean,duelConfirmed;long duelAt;
        private final Deque<Tags> pendingTags=new ArrayDeque<>();
        private final Deque<Reward> pendingRewards=new ArrayDeque<>();
        private final Deque<Reward> entries=new ArrayDeque<>();
        private final Map<String,Integer> kills=new HashMap<>(),streaks=new HashMap<>();
        private final Map<String,Integer> dominationKills=new HashMap<>();
        private final Map<String,Integer> redemptionDeaths=new HashMap<>();
        private final Set<String> chainVictims=new HashSet<>();
        private int chainMelee,chainBow;
        private boolean redemptionDeathCounted;
        private long redemptionDeathAt;
        void clear(){
            streak=0;streakExp=0;lifeKills=0;recapExp=0;recapKills=0;recapAt=0;awaitingRespawn=false;reward=null;receivedAt=0;lastAttacker=null;
            lifeAssists=recapAssists=0;lifeKillExp=lifeAssistExp=recapKillExp=recapAssistExp=0;
            localKills=0;deathsInARow=0;multi=0;lastKillAt=0;deathAt=0;
            matchStartSeen=false;firstKillTaken=false;
            duelVictim=null;duelHealth=0;duelClean=false;duelConfirmed=false;duelAt=0;
            pendingTags.clear();pendingRewards.clear();entries.clear();kills.clear();streaks.clear();dominationKills.clear();
            redemptionDeaths.clear();chainVictims.clear();chainMelee=chainBow=0;redemptionDeathCounted=false;redemptionDeathAt=0;
        }
        void died(String attacker){
            if(attacker!=null)dominationKills.remove(key(attacker));
            if(!awaitingRespawn){recapKillExp=lifeKillExp;recapAssistExp=lifeAssistExp;recapExp=recapKillExp+recapAssistExp;recapKills=lifeKills;recapAssists=lifeAssists;awaitingRespawn=true;}
            lifeKills=lifeAssists=0;lifeKillExp=lifeAssistExp=0;recapAt=0;
            streak=0;streakExp=0;reward=null;receivedAt=0;lastAttacker=attacker;multi=0;
            chainVictims.clear();chainMelee=chainBow=0;
            pendingTags.clear();pendingRewards.clear();entries.clear();
            duelVictim=null;duelHealth=0;duelClean=false;duelConfirmed=false;duelAt=0;
        }
        void respawn(long now,boolean enabled){
            if(!awaitingRespawn)return;
            awaitingRespawn=false;
            redemptionDeathCounted=false;
            if(enabled&&(recapKills>0||recapAssists>0)){recapAt=now;receivedAt=now;}
        }
        void deathObserved(long now){
            if(now-deathAt<PAIR_NANOS)return;
            deathAt=now;deathsInARow++;multi=0;
        }
        /** Starts or keeps the melee duel snapshot; the first hit's victim health is the one that counts. */
        void hit(String victim,float victimHealth,long now){
            if(victim==null)return;
            String key=key(victim);
            if(!key.equals(duelVictim)||now-duelAt>DUEL_NANOS){
                duelVictim=key;duelHealth=victimHealth;duelClean=true;duelConfirmed=false;duelAt=now;
            }
        }
        /** The victim's health dropped after our attack, so the duel is real and not a whiff. */
        void confirmDuel(float victimHealth){if(duelVictim!=null&&victimHealth<duelHealth)duelConfirmed=true;}
        void damaged(){if(duelVictim!=null)duelClean=false;}
        void receive(String text,String local,long now,float health){receive(text,local,now,health,false,false);}
        void receive(String text,String local,long now,float health,boolean airborne,boolean sneaking){
            receive(text,local,now,health,airborne,false,sneaking);
        }
        void receive(String text,String local,long now,float health,boolean airborne,boolean jumping,boolean sneaking){
            if(text!=null&&MATCH_START.matcher(ChatRules.plain(text).trim()).matches()){matchStartSeen=true;firstKillTaken=false;return;}
            while(!pendingTags.isEmpty()&&now-pendingTags.peekFirst().time>PAIR_NANOS)pendingTags.removeFirst();
            while(!pendingRewards.isEmpty()&&now-pendingRewards.peekFirst().time>PAIR_NANOS)pendingRewards.removeFirst();
            KillFeedRules.Death death=KillFeedRules.parse(text);
            if(death!=null){
                if(death.victim.equalsIgnoreCase(local)){
                    if(!death.suicide()&&death.killer!=null){
                        firstKillTaken=true;
                        if(!redemptionDeathCounted||now-redemptionDeathAt>=PAIR_NANOS){
                            String killerKey=key(death.killer);
                            redemptionDeaths.put(killerKey,Math.min(5,redemptionDeaths.getOrDefault(killerKey,0)+1));
                            redemptionDeathCounted=true;redemptionDeathAt=now;
                        }
                    }
                    died(death.suicide()?null:death.killer);deathObserved(now);return;
                }
                String victimKey=key(death.victim);
                int victimStreak=streaks.getOrDefault(victimKey,0),victimKills=kills.getOrDefault(victimKey,0);
                if(death.ownKill(local)&&!death.suicide()){
                    boolean revenge=lastAttacker!=null&&lastAttacker.equalsIgnoreCase(death.victim);
                    boolean flawless=death.cause==KillFeedRules.Cause.MELEE&&duelConfirmed&&duelClean
                        &&victimKey.equals(duelVictim)&&duelHealth>=FLAWLESS_HEALTH&&now-duelAt<=DUEL_NANOS;
                    double shot=distance(death);
                    int against=Math.min(DOMINATION_KILLS,dominationKills.getOrDefault(victimKey,0)+1);
                    dominationKills.put(victimKey,against);
                    boolean chained=multi>0&&lastKillAt>0&&now-lastKillAt<=MULTI_NANOS;
                    if(!chained){chainVictims.clear();chainMelee=chainBow=0;}
                    if(chainVictims.size()<5)chainVictims.add(victimKey);
                    if(death.cause==KillFeedRules.Cause.MELEE)chainMelee=Math.min(3,chainMelee+1);
                    if(isBow(death))chainBow=Math.min(3,chainBow+1);
                    boolean redemption=redemptionDeaths.getOrDefault(victimKey,0)>=5;
                    redemptionDeaths.remove(victimKey);redemptionDeathCounted=false;
                    Tags tags=new Tags(now,death.victim,isBow(death),revenge,health>0&&health<=4,health>0&&health<=2,flawless,
                        isBow(death)&&shot>=LONG_SHOT_BLOCKS&&shot<EXTREME_RANGE_BLOCKS,victimStreak>=SHUTDOWN_STREAK,(long)victimKills-localKills>=UNDERDOG_KILLS,
                          matchStartSeen&&!firstKillTaken,deathsInARow>=COMEBACK_DEATHS,airborne,isBow(death)&&jumping,sneaking,
                        chained?multi+1:1,
                        isBow(death)&&shot>=0&&shot<=POINT_BLANK_BLOCKS,
                        isBow(death)&&shot>=EXTREME_RANGE_BLOCKS,against>=DOMINATION_KILLS,
                        redemption,chainVictims.size()>=5,death.cause==KillFeedRules.Cause.EXPLOSION,chainMelee>=3,chainBow>=3,chainMelee>0&&chainBow>0);
                    if(revenge)lastAttacker=null;
                    if(victimKey.equals(duelVictim)){duelVictim=null;duelConfirmed=false;duelClean=false;}
                    kills.put(key(local),localKills+1);localKills++;
                    firstKillTaken=true;
                    streaks.put(key(local),streak+1);streaks.put(victimKey,0);
                    deathsInARow=0;lastKillAt=now;multi=tags.multi;
                    if(pendingRewards.isEmpty()){pendingTags.addLast(tags);if(pendingTags.size()>16)pendingTags.removeFirst();}
                    else tags.apply(pendingRewards.removeFirst());
                    return;
                }
                if(!death.suicide()&&death.killer!=null){
                    firstKillTaken=true;
                    String killerKey=key(death.killer);
                    kills.put(killerKey,kills.getOrDefault(killerKey,0)+1);
                    streaks.put(killerKey,streaks.getOrDefault(killerKey,0)+1);
                }
                streaks.put(victimKey,0);
                return;
            }
            Reward next=parse(text);if(next==null)return;
            awaitingRespawn=false;recapAt=0;
            next.time=now;next.survivor=!next.assist&&health>0&&health<=4;next.lastBreath=!next.assist&&health>0&&health<=2;
            if(!next.assist){
                if(!pendingTags.isEmpty())pendingTags.removeFirst().apply(next);
                else{pendingRewards.addLast(next);if(pendingRewards.size()>16)pendingRewards.removeFirst();}
                if(streak<Integer.MAX_VALUE)streak++;
                if(lifeKills<Integer.MAX_VALUE)lifeKills++;
                lifeKillExp+=next.points;
            }else{
                if(lifeAssists<Integer.MAX_VALUE)lifeAssists++;
                lifeAssistExp+=next.points;
            }
            streakExp=(int)Math.min(Integer.MAX_VALUE,(long)streakExp+next.points);
            next.streakAt=streak;
            entries.addLast(next);
            while(!entries.isEmpty()&&now-entries.peekFirst().time>3400000000L)entries.removeFirst();
            while(entries.size()>8)entries.removeFirst();
            reward=next;receivedAt=now;
        }
        /** White-stack rows for every kill still on screen; each kill queues after the previous kill's rows. */
        List<Row> rows(){
            List<Row> rows=new ArrayList<>();
            if(recapAt>0&&reward==null){
                rows.add(new Row("EXP "+recapExp,recapAt));
                if(recapKills>0)rows.add(new Row("KILLS EXP "+recapKillExp+" ("+recapKills+" KILLS)",recapAt+rows.size()*ROW_NANOS));
                if(recapAssists>0)rows.add(new Row("ASSISTS EXP "+recapAssistExp+" ("+recapAssists+" ASSISTS)",recapAt+rows.size()*ROW_NANOS));
                return rows;
            }
            long cursor=0;
            for(Reward r:entries){
                List<String> labels=r.labels(r.streakAt);
                long base=Math.max(r.time,cursor);
                for(int i=0;i<labels.size();i++)rows.add(new Row(labels.get(i),base+(long)i*ROW_NANOS,i==0?r.victim:null));
                cursor=base+(long)labels.size()*ROW_NANOS;
            }
            return rows;
        }
    }
    static String key(String name){return name.toLowerCase(Locale.ROOT);}
    static boolean isBow(KillFeedRules.Death death){
        return death.cause==KillFeedRules.Cause.BOW&&(death.weapon==null||death.weapon.equalsIgnoreCase("Bow")||death.weapon.equalsIgnoreCase("Arrow"));
    }
    static double distance(KillFeedRules.Death death){
        if(death.distance==null)return -1;
        try{return Double.parseDouble(death.distance.replace(",",""));}catch(NumberFormatException e){return -1;}
    }
    static float alpha(long age){return Math.max(0,Math.min(1,(3400000000L-age)/400000000F));}
    /** Ease-out: most of the travel happens immediately, then settles instead of crawling. */
    private static float easeOut(float t){t=Math.max(0,Math.min(1,t));return 1-(1-t)*(1-t)*(1-t);}
    static float pop(long age){
        float t=age/(float)POP_NANOS;
        return 1+.45F*(t<.25F?easeOut(t*4):1-easeOut((t-.25F)/.75F));
    }
    static String streakLabel(int streak,boolean assist){
        if(assist||streak<5||streak%(streak<30?5:10)!=0)return "";
        if(streak>=100)return "THE LIMIT BREAKER";
        if(streak>=90)return "NO EQUAL";
        if(streak>=80)return "ABSOLUTE ZERO";
        if(streak>=70)return "WORLD ENDER";
        if(streak>=60)return "APOCALYPSE";
        if(streak>=50)return "ONE MAN ARMY";
        if(streak>=40)return "ANNIHILATION";
        if(streak>=30)return "BEYOND THE LIMIT";
        if(streak>=25)return "IMMORTAL";
        if(streak>=20)return "LEGENDARY";
        if(streak>=15)return "GODLIKE";
        if(streak>=10)return "UNSTOPPABLE";
        return "RAMPAGE";
    }
    static String chainLabel(int kills){return kills<2?"":kills>=10?"MULTI KILL":CHAIN_LABELS[kills-2];}
    static float reveal(long age,long delay){return easeOut((age-delay)/(float)REVEAL_NANOS);}
    static float rowOffset(int index,List<Row> rows,long now){
        float y=index>=2?14:0;
        for(int j=index+1;j<rows.size();j++)if(j!=2)y-=14*reveal(now-rows.get(j).start,0);
        return y;
    }
    static int newestRow(List<Row> rows,long now){
        int i=rows.size()-1;
        while(i>=0&&rows.get(i).start>now)i--;
        return i;
    }
    static int slideOffset(float visibility){return Math.round((1-Math.max(0,Math.min(1,visibility)))*12);}
    /** Faster, eased horizontal entry so the row glides in without the linear stutter. */
    static float slideIn(long age,long delay){return easeOut((age-delay)/(float)SLIDE_NANOS);}
}
