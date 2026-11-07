package otl.client;

import java.util.*;
import java.util.regex.*;

/** Only explicit server reward lines count; player chat and death lines never award kills. */
final class KillConfirmRules {
    private static final Pattern REWARD=Pattern.compile("^\\+([0-9]{1,6})\\s+raindrops\\s*\\|\\s*(Kill Assist|Kill)$",Pattern.CASE_INSENSITIVE);
    private static final long PAIR_NANOS=2000000000L,MULTI_NANOS=3000000000L,DUEL_NANOS=8000000000L;
    static final long POP_NANOS=220000000L,ROW_NANOS=650000000L,REVEAL_NANOS=220000000L,SLIDE_NANOS=130000000L;
    static final float FLAWLESS_HEALTH=18F;
    static final int LONG_SHOT_BLOCKS=30,SHUTDOWN_STREAK=5,COMEBACK_DEATHS=3,MAX_BONUS_RANK=3;
    static final class Reward {
        final int points;final boolean assist;
        boolean bow,revenge,survivor,lastBreath,flawless,longShot,shutdown,underdog,firstBlood,comeback,airborne,sneak;
        int multi;
        long time;
        int streakAt;
        Reward(int points,boolean assist){this.points=points;this.assist=assist;}
        String actionLabel(){return assist?"KILL ASSIST":bow?"BOW KILL":"KILL";}
        List<String> labels(int streak){
            List<String> result=new ArrayList<>();result.add(actionLabel());
            if(assist)return result;
            List<String> tags=new ArrayList<>();
            if(shutdown)tags.add("SHUTDOWN");
            if(revenge)tags.add("REVENGE");
            if(flawless)tags.add("FLAWLESS");
            if(longShot)tags.add("LONG SHOT");
            if(multi>=4)tags.add("MULTI KILL");else if(multi==3)tags.add("TRIPLE KILL");else if(multi==2)tags.add("DOUBLE KILL");
            if(lastBreath)tags.add("LAST BREATH");else if(survivor)tags.add("SURVIVOR");
            if(underdog)tags.add("UNDERDOG");
            if(firstBlood)tags.add("FIRST BLOOD");
            if(comeback)tags.add("COMEBACK");
            if(airborne)tags.add("AIRBORNE");
            if(sneak)tags.add("SNEAK");
            for(int i=0;i<Math.min(MAX_BONUS_RANK,tags.size());i++)result.add(tags.get(i));
            String counter=streakLabel(streak,false);if(!counter.isEmpty())result.add(counter);
            return result;
        }
    }
    /** Facts captured at the kill, attached to whichever reward line follows it. */
    private static final class Tags {
        final long time;final boolean bow,revenge,survivor,lastBreath,flawless,longShot,shutdown,underdog,firstBlood,comeback,airborne,sneak;final int multi;
        Tags(long time,boolean bow,boolean revenge,boolean survivor,boolean lastBreath,boolean flawless,boolean longShot,boolean shutdown,boolean underdog,boolean firstBlood,boolean comeback,boolean airborne,boolean sneak,int multi){
            this.time=time;this.bow=bow;this.revenge=revenge;this.survivor=survivor;this.lastBreath=lastBreath;this.flawless=flawless;this.longShot=longShot;
            this.shutdown=shutdown;this.underdog=underdog;this.firstBlood=firstBlood;this.comeback=comeback;this.airborne=airborne;this.sneak=sneak;this.multi=multi;
        }
        void apply(Reward reward){
            reward.bow=bow;reward.revenge=revenge;reward.survivor=survivor;reward.lastBreath=lastBreath;reward.flawless=flawless;reward.longShot=longShot;
            reward.shutdown=shutdown;reward.underdog=underdog;reward.firstBlood=firstBlood;reward.comeback=comeback;reward.airborne=airborne;reward.sneak=sneak;reward.multi=multi;
        }
    }
    static Reward parse(String text){
        if(text==null||text.length()>256)return null;
        Matcher m=REWARD.matcher(ChatRules.plain(text).trim());
        return m.matches()?new Reward(Integer.parseInt(m.group(1)),m.group(2).equalsIgnoreCase("Kill Assist")):null;
    }
    /** One visible white-stack line and the moment it starts revealing. */
    static final class Row {
        final String text;final long start;
        Row(String text,long start){this.text=text;this.start=start;}
    }
    static final class State {
        int streak,streakExp;Reward reward;long receivedAt;
        String lastAttacker;
        int localKills,deathsInARow,multi;
        long lastKillAt,deathAt;
        String duelVictim;float duelHealth;boolean duelClean,duelConfirmed;long duelAt;
        private final Deque<Tags> pendingTags=new ArrayDeque<>();
        private final Deque<Reward> pendingRewards=new ArrayDeque<>();
        private final Deque<Reward> entries=new ArrayDeque<>();
        private final Map<String,Integer> kills=new HashMap<>(),streaks=new HashMap<>();
        void clear(){
            streak=0;streakExp=0;reward=null;receivedAt=0;lastAttacker=null;
            localKills=0;deathsInARow=0;multi=0;lastKillAt=0;deathAt=0;
            duelVictim=null;duelHealth=0;duelClean=false;duelConfirmed=false;duelAt=0;
            pendingTags.clear();pendingRewards.clear();entries.clear();kills.clear();streaks.clear();
        }
        void died(String attacker){
            streak=0;streakExp=0;reward=null;receivedAt=0;lastAttacker=attacker;multi=0;
            pendingTags.clear();pendingRewards.clear();entries.clear();
            duelVictim=null;duelHealth=0;duelClean=false;duelConfirmed=false;duelAt=0;
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
            while(!pendingTags.isEmpty()&&now-pendingTags.peekFirst().time>PAIR_NANOS)pendingTags.removeFirst();
            while(!pendingRewards.isEmpty()&&now-pendingRewards.peekFirst().time>PAIR_NANOS)pendingRewards.removeFirst();
            KillFeedRules.Death death=KillFeedRules.parse(text);
            if(death!=null){
                if(death.victim.equalsIgnoreCase(local)){died(death.suicide()?null:death.killer);deathObserved(now);return;}
                String victimKey=key(death.victim);
                int victimStreak=streaks.getOrDefault(victimKey,0),victimKills=kills.getOrDefault(victimKey,0);
                if(death.ownKill(local)&&!death.suicide()){
                    boolean revenge=lastAttacker!=null&&lastAttacker.equalsIgnoreCase(death.victim);
                    boolean flawless=death.cause==KillFeedRules.Cause.MELEE&&duelConfirmed&&duelClean
                        &&victimKey.equals(duelVictim)&&duelHealth>=FLAWLESS_HEALTH&&now-duelAt<=DUEL_NANOS;
                    int shot=distance(death);
                    Tags tags=new Tags(now,isBow(death),revenge,health>0&&health<=4,health>0&&health<=2,flawless,
                        isBow(death)&&shot>=LONG_SHOT_BLOCKS,victimStreak>=SHUTDOWN_STREAK,victimKills>localKills,
                        localKills==0,deathsInARow>=COMEBACK_DEATHS,airborne,sneaking,
                        lastKillAt>0&&now-lastKillAt<=MULTI_NANOS?multi+1:1);
                    if(revenge)lastAttacker=null;
                    if(victimKey.equals(duelVictim)){duelVictim=null;duelConfirmed=false;duelClean=false;}
                    kills.put(key(local),localKills+1);localKills++;
                    streaks.put(key(local),streak+1);streaks.put(victimKey,0);
                    deathsInARow=0;lastKillAt=now;multi=tags.multi;
                    if(pendingRewards.isEmpty()){pendingTags.addLast(tags);if(pendingTags.size()>16)pendingTags.removeFirst();}
                    else tags.apply(pendingRewards.removeFirst());
                    return;
                }
                if(!death.suicide()&&death.killer!=null){
                    String killerKey=key(death.killer);
                    kills.put(killerKey,kills.getOrDefault(killerKey,0)+1);
                    streaks.put(killerKey,streaks.getOrDefault(killerKey,0)+1);
                }
                streaks.put(victimKey,0);
                return;
            }
            Reward next=parse(text);if(next==null)return;
            next.time=now;next.survivor=!next.assist&&health>0&&health<=4;next.lastBreath=!next.assist&&health>0&&health<=2;
            if(!next.assist){
                if(!pendingTags.isEmpty())pendingTags.removeFirst().apply(next);
                else{pendingRewards.addLast(next);if(pendingRewards.size()>16)pendingRewards.removeFirst();}
                if(streak<Integer.MAX_VALUE)streak++;
                streakExp=(int)Math.min(Integer.MAX_VALUE,(long)streakExp+next.points);
            }
            next.streakAt=streak;
            entries.addLast(next);
            while(!entries.isEmpty()&&now-entries.peekFirst().time>3400000000L)entries.removeFirst();
            while(entries.size()>8)entries.removeFirst();
            reward=next;receivedAt=now;
        }
        /** White-stack rows for every kill still on screen; each kill queues after the previous kill's rows. */
        List<Row> rows(){
            List<Row> rows=new ArrayList<>();
            long cursor=0;
            for(Reward r:entries){
                List<String> labels=r.labels(r.streakAt);
                long base=Math.max(r.time,cursor);
                for(int i=0;i<labels.size();i++)rows.add(new Row(labels.get(i),base+(long)i*ROW_NANOS));
                cursor=base+(long)labels.size()*ROW_NANOS;
            }
            return rows;
        }
    }
    static String key(String name){return name.toLowerCase(Locale.ROOT);}
    static boolean isBow(KillFeedRules.Death death){
        return death.cause==KillFeedRules.Cause.BOW&&(death.weapon==null||death.weapon.equalsIgnoreCase("Bow")||death.weapon.equalsIgnoreCase("Arrow"));
    }
    static int distance(KillFeedRules.Death death){
        if(death.distance==null)return 0;
        try{return (int)Double.parseDouble(death.distance.replace(",",""));}catch(NumberFormatException e){return 0;}
    }
    static float alpha(long age){return Math.max(0,Math.min(1,(3400000000L-age)/400000000F));}
    /** Ease-out: most of the travel happens immediately, then settles instead of crawling. */
    private static float easeOut(float t){t=Math.max(0,Math.min(1,t));return 1-(1-t)*(1-t)*(1-t);}
    static float pop(long age){return 1+.25F*(1-easeOut(age/(float)POP_NANOS));}
    static String streakLabel(int streak,boolean assist){
        if(assist||streak<2)return "";
        if(streak>=20)return "LEGENDARY";
        if(streak>=15)return "GODLIKE";
        if(streak>=10)return "UNSTOPPABLE";
        if(streak>=5)return "RAMPAGE";
        return streak+" KILL STREAK";
    }
    static float reveal(long age,long delay){return easeOut((age-delay)/(float)REVEAL_NANOS);}
    static int newestRow(List<Row> rows,long now){
        int i=rows.size()-1;
        while(i>=0&&rows.get(i).start>now)i--;
        return i;
    }
    static int slideOffset(float visibility){return Math.round((1-Math.max(0,Math.min(1,visibility)))*12);}
    /** Faster, eased horizontal entry so the row glides in without the linear stutter. */
    static float slideIn(long age,long delay){return easeOut((age-delay)/(float)SLIDE_NANOS);}
}
