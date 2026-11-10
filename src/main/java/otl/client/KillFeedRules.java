package otl.client;

import java.util.*;
import java.util.regex.*;

/** English PGM death templates, not arbitrary prose containing player names. */
final class KillFeedRules {
    enum HeadMode {
        ONLY_ME("Only Me"),ALL("ALL"),NONE("None");
        final String label;HeadMode(String label){this.label=label;}
        static HeadMode parse(String value){try{return valueOf(value);}catch(IllegalArgumentException bad){return ONLY_ME;}}
        boolean shows(String player,String local){return player!=null&&(this==ALL||this==ONLY_ME&&player.equalsIgnoreCase(local));}
    }
    enum FilterMode {
        ALL("ALL"),ONLY_ME("Only Me"),TEAM("Only My Team");
        final String label;FilterMode(String label){this.label=label;}
        static FilterMode parse(String value){try{return valueOf(value);}catch(IllegalArgumentException bad){return ALL;}}
        boolean shows(Death death,String local,java.util.function.Predicate<String> teammate){
            if(this==ALL)return true;
            if(death.victim.equalsIgnoreCase(local)||death.ownKill(local))return true;
            return this==TEAM&&(teammate.test(death.victim)||death.killer!=null&&teammate.test(death.killer));
        }
    }
    enum Cause { MELEE, BOW, EXPLOSION, FIRE, MAGIC, FALL, DROWN, CACTUS, ANVIL, DEATH }
    enum Context { NONE, LAVA, FIRE, VOID, FALL }
    static final class Death {
        final String original,victim,killer,weapon,distance,mob;
        final String victimLabel,killerLabel;
        final Cause cause;
        final boolean voidFall,lava,fall;
        boolean assist;
        final long receivedAt=System.nanoTime();
        Death(String original,Matcher m,String template){
            this.original=original;victim=m.group("v");killer=group(m,"k");weapon=group(m,"w");
            distance=group(m,"d");mob=group(m,"m");
            victimLabel=colored(original,m.start("v"),victim);
            killerLabel=killer==null?(mob==null?"\u00a77World":mob):colored(original,m.start("k"),killer);
            voidFall=template.contains("out of the world");lava=template.contains("lava")||weapon!=null&&weapon.equalsIgnoreCase("Lava");
            fall=voidFall||template.contains("high place")||template.contains("spleefed")||template.contains("fell")||template.contains("ground")||template.contains("splat")||template.contains("gravity")||template.contains("orbit");
            cause=template.contains("shot")||template.contains("sniped")||template.contains("stray")?Cause.BOW:
                template.contains("blown")||template.contains("blew")||template.contains("creeper")?Cause.EXPLOSION:
                template.contains("burnt")||template.contains("flames")?Cause.FIRE:
                template.contains("magic")||template.contains("potion")||template.contains("poison")||template.contains("wither")?Cause.MAGIC:
                template.contains("slain")||template.contains("fists")||template.contains("punched")||template.contains("knocked")?Cause.MELEE:
                template.contains("breathe")?Cause.DROWN:template.contains("cactus")?Cause.CACTUS:
                template.contains("squash")?Cause.ANVIL:fall?Cause.FALL:Cause.DEATH;
        }
        boolean ownKill(String name){return killer!=null&&killer.equalsIgnoreCase(name);}
        boolean suicide(){return killer==null?mob==null:killer.equalsIgnoreCase(victim);}
        Context context(){return lava?Context.LAVA:voidFall?Context.VOID:cause==Cause.FIRE?Context.FIRE:fall?Context.FALL:Context.NONE;}
        boolean extraContext(){return suicide()?context()!=Context.NONE||cause!=Cause.DEATH:context()!=Context.NONE&&cause!=Cause.FIRE&&cause!=Cause.FALL;}
        String distanceLabel(){return distance==null?"":" (+"+distance+" Blocks)";}
        int border(String local,int killColor,int deathColor,int assistColor,int opacity){
            if(suicide())return 0;
            if(!victim.equalsIgnoreCase(local)&&!ownKill(local)&&!assist)return 0;
            int rgb=victim.equalsIgnoreCase(local)?deathColor:ownKill(local)?killColor:assistColor;
            return (Math.max(0,Math.min(100,opacity))*255/100<<24)|(rgb&0xFFFFFF);
        }
    }
    static final class History {
        private static final long ASSIST_NANOS=2000000000L;
        private static final int MAX_PENDING_ASSISTS=8;
        final List<Death> entries=new ArrayList<>();
        private final Deque<PendingAssist> pendingAssists=new ArrayDeque<>();
        private static final class PendingAssist {
            final long at;final String local;final java.util.function.Predicate<String> teammate;
            PendingAssist(long at,String local,java.util.function.Predicate<String> teammate){this.at=at;this.local=local;this.teammate=teammate;}
        }
        void add(Death death){
            entries.add(0,death);if(entries.size()>1000)entries.remove(1000);
            // shortcut: assist rewards name no victim, so a reward that arrives before its death line claims the next death.
            for(Iterator<PendingAssist> it=pendingAssists.iterator();it.hasNext();){
                PendingAssist assist=it.next();
                long age=death.receivedAt-assist.at;
                if(age<0)continue;
                if(age>ASSIST_NANOS){it.remove();continue;}
                it.remove();
                if(eligible(death,assist.local,assist.teammate))death.assist=true;
                break;
            }
        }
        boolean assist(String local,long now){
            return assist(local,now,name->true);
        }
        boolean assist(String local,long now,java.util.function.Predicate<String> teammate){
            Death match=null;
            for(Death death:entries){
                if(now-death.receivedAt>ASSIST_NANOS)break;
                if(now<death.receivedAt||!eligible(death,local,teammate))continue;
                match=death;break;
            }
            if(match==null){
                pendingAssists.addLast(new PendingAssist(now,local,teammate));
                while(pendingAssists.size()>MAX_PENDING_ASSISTS)pendingAssists.removeFirst();
                expireAssists(now);
                return true;
            }
            if(match.assist)return false;
            match.assist=true;return true;
        }
        private void expireAssists(long now){
            while(!pendingAssists.isEmpty()&&now-pendingAssists.peekFirst().at>ASSIST_NANOS)pendingAssists.removeFirst();
        }
        private static boolean eligible(Death death,String local,java.util.function.Predicate<String> teammate){
            return death.killer!=null&&!death.suicide()&&!death.ownKill(local)&&!death.victim.equalsIgnoreCase(local)
                &&teammate.test(death.victim);
        }
        void clear(){entries.clear();pendingAssists.clear();}
    }
    /** The feed has no victim in assist rewards, so it labels the local player with their team color. */
    static String assistLabel(String name,char color){
        return "\u00a7"+(color==0?'b':color)+"\u00a7l"+name+"\u00a7r";
    }
    private static final class Rule {
        final String template;final Pattern pattern;
        Rule(String template){
            this.template=template;StringBuilder regex=new StringBuilder("^");
            Matcher tokens=Pattern.compile("\\{([vkwdm])\\}").matcher(template);int end=0;
            while(tokens.find()){
                regex.append(Pattern.quote(template.substring(end,tokens.start())));
                String key=tokens.group(1);
                regex.append("(?<").append(key).append(">").append(key.equals("v")||key.equals("k")?"[A-Za-z0-9_]{1,16}":key.equals("d")?"[0-9]{1,6}(?:[,.][0-9]{1,3})?":key.equals("m")?"[A-Za-z][A-Za-z '-]{0,47}?":"[^\\r\\n]{1,80}?").append(")");
                end=tokens.end();
            }
            regex.append(Pattern.quote(template.substring(end))).append("(?:[.!])?(?: \\(predicted\\))?$");
            pattern=Pattern.compile(regex.toString());
        }
    }
    private static final List<Rule> RULES=new ArrayList<>();
    static {
        for(String text:new String[]{
            "was slain by {k}'s {w}","felt the fury of {k}'s fists","was killed by {k}",
            "was killed by {k}'s {w} block","was killed by {k}'s {m}",
            "was killed by {k}'s magic","was killed by {k}'s potion of {w}",
            "was killed by the magic of {k}'s {m}","was killed by a potion of {w} from {k}'s {m}",
            "was shot by {k} from {d} blocks","was sniped by {k} from {d} blocks",
            "was shot by {k} with a {w} from {d} blocks","was sniped by {k} with a {w} from {d} blocks",
            "was shot by {k}'s {m}","was shot by a {w} from {k}'s {m}",
            "was blown up by {k}","was blown up by {k} from {d} blocks",
            "was blown up by {k}'s {w}","was blown up by {k}'s {w} from {d} blocks",
            "was burnt to a crisp by {k}","was burnt to a crisp by {k}'s flaming {w}","was burnt to a crisp by {k}'s {w}",
            "was squashed to death by {k}","was squashed by {k}'s falling {w}","was suffocated by {k}",
            "tried to hug {k}'s cactus","tried to hug {k}'s creeper",
            "forgot to breathe","was struck by lightning","starved to death","died of poisoning","withered away","died from unknown causes","died",
            "was killed by magic","was killed by a potion of {w}","was killed by a {m}'s magic","was killed by a {m}'s potion of {w}",
            "was killed by a {w} block","was killed by a {m}","was slain by a {m} with a {w}","was slain by a {m}",
            "was hit by a stray arrow","was hit by a stray {w}","was shot by a {m}","was shot by a {w} from a {m}",
            "blew up","was blown up by TNT","was blown up by a {m}","tried to hug a creeper",
            "went up in flames","was burnt to a crisp by a flaming {w}","was burnt to a crisp by Lava",
            "was squashed to death","was squashed by a falling {w}","suffocated","tried to hug a cactus",
            "went splat","hit the ground too hard","fell off a high place","went splat from {d} blocks up",
            "hit the ground from {d} blocks up","fell {d} blocks and died","forgot how to fly","just learned about gravity",
            "tripped and fell","fell out of a {d} block orbit","fell into lava","fell out of the world"
        })add(text);
        for(String where:new String[]{"off a high place","into lava","out of the world"}){
            for(String verb:new String[]{"punched","spleefed"})add("was "+verb+" "+where+" by {k}");
            add("was knocked "+where+" by {k}'s {w}");add("was knocked "+where+" by a {m}");
            for(String verb:new String[]{"shot","sniped"}){
                add("was "+verb+" "+where+" by {k} from {d} blocks");
                add("was "+verb+" "+where+" by {k} with a {w} from {d} blocks");
            }
            add("was shot "+where+" by a stray arrow");add("was shot "+where+" by a stray {w}");
            add("was shot "+where+" by a {m}");add("was shot "+where+" by {k}'s {m}");
            add("was shot "+where+" by a {w} from {k}'s {m}");
            for(String verb:new String[]{"blown","spleefed"}){
                add("was "+verb+" "+where);add("was "+verb+" "+where+" by {k}");
                add("was "+verb+" "+where+" by {k}'s {w}");
                add("was "+verb+" "+where+" by {k}'s {w} from {d} blocks");
            }
        }
        RULES.sort(Comparator.comparingInt((Rule r)->r.template.replaceAll("\\{.\\}","").length()).reversed());
    }
    private static void add(String text){RULES.add(new Rule("{v} "+text));}
    static String feedLabel(String label,Death death,String local){
        boolean involved=death.victim.equalsIgnoreCase(local)||death.ownKill(local);
        return involved?label.substring(0,2)+"\u00a7l"+label.substring(2):label;
    }
    static boolean hitRow(float mx,float my,int x,int y,int width){return mx>=x&&mx<x+width&&my>=y&&my<y+20;}
    static float opacity(long age,int seconds,boolean browsing){return browsing?1:Math.max(0,Math.min(1,(seconds*1000000000L-age)/1000000000F));}
    static int faded(int color,float opacity){return (color&0xFFFFFF)|((int)((color>>>24)*opacity)<<24);}
    static int panelColor(boolean hovered,int opacity,float fade){return faded(hovered?0xEE202020:0xCC000000,Math.max(0,Math.min(100,opacity))/100F*fade);}
    static float slideOffset(long age,boolean enabled){float t=Math.max(0,Math.min(1,age/200000000F));return enabled?-22*(1-t)*(1-t)*(1-t):0;}
    static Death parse(String formatted){
        if(formatted==null||formatted.length()>1024)return null;
        String plain=ChatRules.plain(formatted);
        // shortcut: English PGM templates only; add locale templates when localized servers are needed.
        for(Rule rule:RULES){Matcher m=rule.pattern.matcher(plain);if(m.matches())return new Death(formatted,m,rule.template);}
        return null;
    }
    private static String group(Matcher m,String key){try{return m.group(key);}catch(IllegalArgumentException absent){return null;}}
    private static String colored(String formatted,int offset,String name){
        char color='7';int plain=0;
        for(int i=0;i<formatted.length();i++){
            char c=formatted.charAt(i);
            if(c=='\u00a7'&&i+1<formatted.length()){
                char code=Character.toLowerCase(formatted.charAt(++i));
                if("0123456789abcdef".indexOf(code)>=0)color=code;else if(code=='r')color='7';
            }else if(plain++==offset)break;
        }
        return "\u00a7"+color+name+"\u00a7r";
    }
}
