package otl.client;

import java.util.*;
import java.util.regex.*;

/** English PGM death templates, plus a name-based fallback for the locales PGM translates them into. */
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
                template.contains("squash")?Cause.ANVIL:fall?Cause.FALL:
                // PGM's plain wording names who hit you and no weapon ("was killed by Steve"), so
                // the cause is still known. Falling through to DEATH drew a bone icon. A line that
                // names only a block or entity has no attacker to show, and stays unknown.
                killer!=null?Cause.MELEE:Cause.DEATH;
        }
        /** A death matched against PGM's own wording for the client's language. */
        Death(String original,Matcher m,Rule rule){
            this.original=original;victim=m.group("v");killer=group(m,"k");weapon=group(m,"w");
            distance=group(m,"d");mob=group(m,"m");
            victimLabel=colored(original,m.start("v"),victim);
            killerLabel=killer==null?(mob==null?"\u00a77World":mob):colored(original,m.start("k"),killer);
            cause=rule.cause;voidFall=rule.voidFall;lava=rule.lava;fall=rule.fall;
        }
        /** A death recognised only by the two names in it, for a locale with no English template. */
        Death(String original,String victim,int victimAt,String killer,int killerAt){
            this.original=original;this.victim=victim;this.killer=killer;
            this.weapon=null;this.distance=null;this.mob=null;
            this.victimLabel=colored(original,victimAt,victim);
            this.killerLabel=colored(original,killerAt,killer);
            // A line that names a player attacker is a player kill, in any language. Without this
            // it fell through to "unknown" and every translated melee kill drew a bone.
            this.cause=killer==null?Cause.DEATH:Cause.MELEE;
            this.voidFall=false;this.lava=false;this.fall=false;
        }
        boolean ownKill(String name){return killer!=null&&killer.equalsIgnoreCase(name);}
        boolean suicide(){return killer==null?mob==null:killer.equalsIgnoreCase(victim);}
        Context context(){return lava?Context.LAVA:voidFall?Context.VOID:cause==Cause.FIRE?Context.FIRE:fall?Context.FALL:Context.NONE;}
        /**
         * A second icon at the end of the row is only for a player kill with something extra to say
         * (a bow shot off a cliff, say). With no attacker the icon in front already says what killed
         * them, so drawing it again at the end repeats the same picture.
         */
        boolean extraContext(){return !suicide()&&context()!=Context.NONE&&cause!=Cause.FIRE&&cause!=Cause.FALL;}
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
    static final class Rule {
        final String template;final Pattern pattern;
        /** Set only for a translated template, where the key says what killed the player. */
        final Cause cause;final boolean lava,voidFall,fall;
        /** Fixed text length and whether the line names a player, used to rank overlapping templates. */
        final int literal;final boolean named;
        Rule(String template){
            this.template=template;cause=null;lava=false;voidFall=false;fall=false;literal=0;named=false;
            StringBuilder regex=new StringBuilder("^");
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
        /** A template as PGM writes it in the client's own language: {0} victim, {1} killer, {2} item, {3} mob, {4} distance. */
        Rule(String spec,String template){
            int colon=spec.indexOf(':');
            Cause keyed=Cause.valueOf(colon<0?spec:spec.substring(0,colon));
            // PGM keys a weaponless player kill as plain "death", so in every language the most
            // common kill line drew a bone. A template that names the killer is a player kill.
            cause=keyed==Cause.DEATH&&template.contains("{1}")?Cause.MELEE:keyed;
            boolean f=false,l=false,v=false;
            if(colon>=0)for(String flag:spec.substring(colon+1).split("\\+")){
                if(flag.equals("fall"))f=true;else if(flag.equals("lava"))l=true;else if(flag.equals("void"))v=true;
            }
            this.template=template;lava=l;voidFall=v;fall=f;
            pattern=Pattern.compile(numbered(template));
            literal=template.replaceAll("\\{[0-4]\\}","").length();
            named=template.contains("{1}");
        }
    }
    private static String numbered(String template){
        template=template.replace("''","'");
        StringBuilder regex=new StringBuilder("^");
        Matcher tokens=Pattern.compile("\\{([0-4])\\}").matcher(template);int end=0;
        while(tokens.find()){
            regex.append(Pattern.quote(template.substring(end,tokens.start())));
            String key=tokens.group(1);
            regex.append("(?<").append("vkwmd".charAt(key.charAt(0)-'0')).append(">")
                .append(key.equals("0")||key.equals("1")?"[A-Za-z0-9_]{1,16}":key.equals("4")?"[0-9]{1,6}(?:[,.][0-9]{1,3})?":key.equals("3")?"[^\\r\\n]{1,48}?":"[^\\r\\n]{1,80}?").append(")");
            end=tokens.end();
        }
        regex.append(Pattern.quote(template.substring(end)));
        // The line may end in punctuation in any script, trail a space, and PGM appends its own "(predicted)".
        return regex.append("\\s*(?:[.!?\\u3002\\uff01\\uff1f])?(?:\\s*\\([^\\r\\n)]{0,40}\\))?$").toString();
    }
    private static final List<Rule> RULES=new ArrayList<>();
    static {
        for(String text:new String[]{
            // Vanilla's own wording, which is what a translated death line actually says when the
            // client renders it: "X was slain by Y using Iron Sword". Read straight from the client
            // language file when that works, and kept here so a missing file still names the weapon.
            "was slain by {k} using {w}","was shot by {k} using {w}","was fireballed by {k} using {w}",
            "was pummeled by {k} using {w}","was killed by {k} using magic","was blown up by {k} using {w}",
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
            "was burnt to a crisp by {m}",
            "went splat","hit the ground too hard","fell off a high place","went splat from {d} blocks up",
            "hit the ground from {d} blocks up","fell {d} blocks and died","forgot how to fly","just learned about gravity",
            "tripped and fell","fell out of a {d} block orbit","fell into lava","fell out of the world"
        })add(text);
        for(String where:new String[]{"off a high place","into lava","out of the world"}){
            for(String verb:new String[]{"punched","spleefed"})add("was "+verb+" "+where+" by {k}");
            // PGM writes a block or entity there when nothing owns the spleef, so the same words
            // with a {m} catch "was spleefed off a high place by Iron Sword".
            for(String verb:new String[]{"punched","spleefed"})add("was "+verb+" "+where+" by {m}");
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
        return parse(formatted,null,null);
    }

    /**
     * PGM translates a death line into each player's own language, so the English templates above
     * only fire for English clients. The same jar also carries those translations, so the exact
     * wording per language is read from {@code death-messages.txt} and matched the same way, which
     * keeps the cause of death instead of falling back to a plain skull.
     */
    static Death parse(String formatted,java.util.function.Predicate<String> known){
        return parse(formatted,known,null);
    }

    /** For callers that only need the cause of a line the client itself rendered. */
    static Death parse(String formatted,String locale){
        return parse(formatted,null,locale);
    }

    static Death parse(String formatted,java.util.function.Predicate<String> known,String locale){
        if(formatted==null||formatted.length()>1024)return null;
        return parseRules(formatted,known,localeRules(locale));
    }

    static Death parseRules(String formatted,java.util.function.Predicate<String> known,List<Rule> localized){
        String plain=ChatRules.plain(formatted);
        Death fallback=null;
        for(Rule rule:localized){
            Matcher m=rule.pattern.matcher(plain);
            if(!m.matches())continue;
            fallback=new Death(formatted,m,rule);
            if(fallback.mob==null||fallback.killer!=null)return fallback;
            break;
        }
        // Vanilla's mob placeholder also accepts the entire phrase "Player's Iron Sword".
        for(Rule rule:RULES){
            Matcher m=rule.pattern.matcher(plain);
            if(m.matches()&&(fallback==null||group(m,"k")!=null))return new Death(formatted,m,rule.template);
        }
        if(fallback!=null)return fallback;
        return known==null?null:byNames(formatted,plain,known);
    }

    /** The client's own language code, which is the one PGM rendered the death message in. */
    static String clientLocale(){
        net.minecraft.client.Minecraft mc=MinecraftAccess.get();
        return mc==null||mc.gameSettings==null?null:mc.gameSettings.language;
    }

    private static final Map<String,List<String>> RAW=new HashMap<>();
    /** Read from the netty thread and the render thread, so a plain map could corrupt. */
    private static final Map<String,List<Rule>> LOADED=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean read;

    private static List<Rule> localeRules(String locale){
        if(locale==null||locale.isEmpty())return Collections.emptyList();
        List<Rule> rules=LOADED.get(locale);
        if(rules!=null)return rules;
        rules=new ArrayList<>();
        // PGM sends the death line as a translatable component, so the client renders it in its
        // own language file: that file is the exact wording on screen, in every language, not just
        // the bundles PGM ships.
        rules.addAll(langRules(clientLang(locale)));
        loadTemplates();
        for(String key:nearby(locale))for(String entry:RAW.get(key)){
            int bar=entry.indexOf('|');
            try{rules.add(new Rule(entry.substring(0,bar),entry.substring(bar+1)));}catch(RuntimeException skip){}
        }
        // Longer fixed text is more specific; a line naming a player beats one naming only an item.
        rules.sort(Comparator.comparingInt((Rule r)->r.literal).reversed().thenComparingInt(r->r.named?0:1));
        LOADED.put(locale,rules);
        return rules;
    }

    /** The client's own language file, or null when the pack does not ship that language. */
    private static String clientLang(String locale){
        net.minecraft.client.Minecraft mc=MinecraftAccess.get();
        if(mc==null)return null;
        try(java.io.InputStream in=mc.getResourceManager()
            .getResource(new net.minecraft.util.ResourceLocation("minecraft","lang/"+locale+".lang")).getInputStream()){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];
            for(int read;(read=in.read(buffer))>0;)out.write(buffer,0,read);
            return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(Exception missing){
            // A language this pack does not ship: the bundled translations still apply.
            return null;
        }
    }

    /**
     * Every {@code death.} line of a Minecraft language file, as rules. The key says what killed
     * the player, so the cause survives translation, and the wording is the client's own.
     */
    static List<Rule> langRules(String langFile){
        List<Rule> rules=new ArrayList<>();
        if(langFile==null)return rules;
        for(String line:langFile.split("[\\r\\n]+")){
            if(!line.startsWith("death."))continue;
            int eq=line.indexOf('=');
            if(eq<0)continue;
            String key=line.substring(0,eq);
            String template=placeholders(line.substring(eq+1));
            // Vanilla's mob key puts the mob's own name in the killer slot, which PGM numbers as
            // the mob argument; reading it as a weapon would label the death "World".
            if(key.endsWith(".mob"))template=template.replace("{1}","{3}");
            try{rules.add(new Rule(spec(key),template));}
            catch(RuntimeException unusable){}
        }
        // Same ranking as the bundled templates: the longer fixed text is the more specific rule.
        rules.sort(Comparator.comparingInt((Rule r)->r.literal).reversed().thenComparingInt(r->r.named?0:1));
        return rules;
    }

    /** The cause a vanilla death key names, in the spec form {@link Rule} takes. */
    private static String spec(String key){
        if(key.contains("arrow"))return "BOW";
        if(key.contains("fireball")||key.contains("explosion"))return "EXPLOSION";
        if(key.contains("lava"))return "FIRE:lava";
        if(key.contains("onFire")||key.contains("inFire"))return "FIRE";
        if(key.contains("magic")||key.contains("wither")||key.contains("dragonBreath"))return "MAGIC";
        if(key.contains("outOfWorld"))return "FALL:void";
        if(key.startsWith("death.fell")||key.contains(".fall"))return "FALL:fall";
        if(key.contains("drown"))return "DROWN";
        if(key.contains("cactus"))return "CACTUS";
        if(key.contains("anvil")||key.contains("fallingBlock"))return "ANVIL";
        return "DEATH";
    }

    /** {@code %1$s} and friends become PGM's numbered arguments: 0 victim, 1 killer, 2 item, 3 mob, 4 distance. */
    static String placeholders(String text){
        StringBuilder out=new StringBuilder(text.length());
        int next=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c!='%'){out.append(c);continue;}
            int j=i+1,index=-1;
            while(j<text.length()&&Character.isDigit(text.charAt(j))){
                index=(index<0?0:index*10)+(text.charAt(j)-'0');j++;
            }
            if(j<text.length()&&text.charAt(j)=='$')j++;
            if(j>=text.length()){out.append(c);continue;}
            char type=text.charAt(j);
            if(type=='%'){out.append('%');i=j;continue;}
            if(type!='s'&&type!='d'){out.append(c);continue;}
            int slot;
            if(index>=0){slot=index-1;next=index;}else slot=next++;
            if(slot<0||slot>4){out.append(c);continue;}
            out.append('{').append(slot).append('}');
            i=j;
        }
        return out.toString();
    }

    /**
     * PGM renders a death line in the nearest bundle it ships, not in the client's exact locale: a
     * Portuguese (pt_PT), Latin-American Spanish or Norwegian (nb_NO) client is sent pt_BR, es_* or
     * no_NO words. Reading only the exact key would never match those lines, so every bundle of the
     * same language is tried, then one of the same country.
     */
    private static List<String> nearby(String locale){
        int cut=locale.indexOf('_');
        String language=(cut<0?locale:locale.substring(0,cut)).toLowerCase(Locale.ROOT);
        String country=(cut<0?"":locale.substring(cut+1)).toLowerCase(Locale.ROOT);
        List<String> sameLanguage=new ArrayList<>(),sameCountry=new ArrayList<>();
        // Snapshot: templates are read on another thread the first time this runs.
        for(String key:new ArrayList<>(RAW.keySet())){
            int keyCut=key.indexOf('_');
            if((keyCut<0?key:key.substring(0,keyCut)).toLowerCase(Locale.ROOT).equals(language))sameLanguage.add(key);
            else if(!country.isEmpty()&&(keyCut<0?"":key.substring(keyCut+1)).toLowerCase(Locale.ROOT).equals(country))sameCountry.add(key);
        }
        sameLanguage.addAll(sameCountry);
        return sameLanguage;
    }

    private static synchronized void loadTemplates(){
        if(read)return;
        try(java.io.InputStream in=KillFeedRules.class.getResourceAsStream("/assets/otl/death-messages.txt")){
            if(in==null)return;
            java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
            for(String line;(line=reader.readLine())!=null;){
                if(line.isEmpty()||line.charAt(0)=='#')continue;
                int bar=line.indexOf('|');
                if(bar<1)continue;
                RAW.computeIfAbsent(line.substring(0,bar),key->new ArrayList<>()).add(line.substring(bar+1));
            }
        }catch(Exception bad){
            System.err.println("OTL: could not read translated death messages: "+bad.getMessage());
        }finally{
            read=true;
        }
    }

    /**
     * PGM translates a death line into each player's own language, so the English templates above
     * only fire for English clients. Every one of those lines still names the victim first and, when
     * there is a killer, names them too: reading the names instead of the words between them works
     * in any language. The cause is then unknown and drawn as a plain death.
     *
     * The names are not always separate words. German, Dutch, Swedish, Danish and Afrikaans glue a
     * possessive onto the killer ("{1}s" renders as "Bobs"), and Japanese can write the two names
     * with nothing between them, so a name is matched as the longest known account name starting
     * where the last one ended.
     *
     * A victim on their own is not enough to go on - "Alex joined the game" has the same shape - so
     * a line with only one name is left alone rather than guessed at.
     */
    private static Death byNames(String formatted,String plain,java.util.function.Predicate<String> known){
        String text=plain.trim();
        if(text.length()<3||text.length()>200)return null;
        if(ChatRules.playerMessage(formatted))return null; // someone talking, not a death
        List<String> names=new ArrayList<>();List<Integer> at=new ArrayList<>();
        int after=-1;
        for(int i=0;i<text.length();i++){
            // A name starts a word, or continues the one just read when a suffix was glued to it.
            if(i>0&&i!=after&&nameChar(text.charAt(i-1)))continue;
            int length=nameAt(text,i,known);
            if(length==0)continue;
            if(names.size()==4)return null; // more names than a kill line ever carries: prose
            names.add(text.substring(i,i+length));at.add(i);
            after=i+length;i+=length-1;
        }
        if(names.size()<2||at.get(0)!=0)return null; // PGM always names the victim first
        return new Death(formatted,names.get(0),0,names.get(1),at.get(1));
    }

    /** Length of the longest account name starting at i, or 0 when none does. */
    private static int nameAt(String text,int i,java.util.function.Predicate<String> known){
        for(int length=Math.min(16,text.length()-i);length>=3;length--)
            if(known.test(text.substring(i,i+length)))return length;
        return 0;
    }
    private static boolean nameChar(char c){return c<128&&(Character.isLetterOrDigit(c)||c=='_');}

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
