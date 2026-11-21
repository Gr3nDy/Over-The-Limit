package otl.client;

import java.util.*;
import java.util.regex.*;

/** Anchored server-prefix recognition; uncertain lines belong to All only. */
final class ChatRules {
    static final String ALL="all",TEAM="team",PARTY="party";
    private static final Pattern FORMAT=Pattern.compile("\u00a7[0-9A-FK-OR]",Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_SPACES=Pattern.compile("\\p{Zs}");
    private static final Pattern PM=Pattern.compile("^(From|To)(?:\\s*:\\s*|\\s+)(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)*<?([A-Za-z0-9_]{1,16})>?(?:\\s*[:>\u00bb]\\s*|\\s+)(.+)$",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern GROUP=Pattern.compile("^\\(([^)\\r\\n]{1,32})\\)\\s*(?:\\[[^\\]\\r\\n]+\\]\\s*)*<?([A-Za-z0-9_]{1,16})>?(?:\\s*\\[[^\\]\\r\\n]{1,32}\\])?\\s*[:>\u00bb]\\s*(.+)$");
    static final class Route {
        final String key,name;final boolean outgoing;
        Route(String key,String name,boolean outgoing){this.key=key;this.name=name;this.outgoing=outgoing;}
    }
    static String plain(String s){return FORMAT.matcher(s).replaceAll("");}
    static boolean commandWarning(String text){return plain(text).trim().startsWith("\u26a0");}
    static boolean playerMessage(String text){
        Route route=classify(text);
        String value=plain(text).replaceAll("\\p{Zs}"," ").trim();
        return !ALL.equals(route.key)||value.matches("(?s)^(?:\\[[^\\]\\r\\n]{1,48}\\]\\s*)*<[^<>\\r\\n]{1,128}>\\s*:?\\s*\\S.*$")
            ||value.matches("(?s)^\\([^)\\r\\n]{1,48}\\)\\s*[^:\\r\\n]{1,128}[:\u00bb]\\s*\\S.*$")
            ||value.matches("(?s)^(?:\\[[^\\]\\r\\n]{1,48}\\]\\s*)*[^\\p{L}\\p{N}\\r\\n<>:]{0,8}[A-Za-z0-9_]{1,16}\\s*[:\u00bb]\\s*\\S.*$");
    }
    static Route classify(String formatted){
        if(formatted.length()>1024)return new Route(ALL,null,false);
        String text=plain(formatted).trim();Matcher pm=PM.matcher(privateText(formatted));
        if(pm.matches()&&privatePrefix(privateText(formatted)))return new Route("pm:"+pm.group(2).toLowerCase(Locale.ROOT),pm.group(2),pm.group(1).equalsIgnoreCase("To"));
        Matcher group=GROUP.matcher(text);
        if(group.matches()){
            if(group.group(1).equalsIgnoreCase("Party")){
                int pos=formatted.toLowerCase(Locale.ROOT).indexOf("(party)");
                if(pos<0)return new Route(ALL,null,false);
                char color='r';Matcher codes=FORMAT.matcher(formatted.substring(0,pos));
                while(codes.find()){char c=Character.toLowerCase(codes.group().charAt(1));if("0123456789abcdefr".indexOf(c)>=0)color=c;}
                return new Route(color=='e'?PARTY:ALL,group.group(2),false);
            }
            return new Route(TEAM,group.group(2),false);
        }
        return new Route(ALL,null,false);
    }
    private static String privateText(String text){return PRIVATE_SPACES.matcher(plain(text)).replaceAll(" ").trim();}
    // Bare prose such as "to your monument!" is not evidence of a private message.
    private static boolean privatePrefix(String text){return text.matches("(?is)^(From|To)\\s*:.*")||text.matches("(?is)^(From|To)\\s+(?:\\[[^\\]\\r\\n]+\\]\\s*)*<?[A-Za-z0-9_]{1,16}>?\\s*[:>\u00bb].*");}
    static String privateBody(String text){if(text.length()>1024)return "";Matcher m=PM.matcher(privateText(text));return m.matches()&&privatePrefix(privateText(text))?m.group(3).trim():"";}
    static String groupTeam(String text){Matcher m=GROUP.matcher(plain(text).trim());return m.matches()&&!m.group(1).equalsIgnoreCase("Party")?m.group(1).trim():null;}
    static String groupBody(String text){Matcher m=GROUP.matcher(plain(text).trim());return m.matches()?m.group(3).trim():"";}
    /**
     * The line PGM closes a welcome block with: a rule of dashes, equals or underscores. It carries
     * no words, so it marks the end of the block on a client in any language.
     */
    static boolean welcomeEnd(String plainLine){
        return plainLine!=null&&plainLine.trim().matches("[-=*_~]{3,}");
    }
    static boolean authoredBy(String formatted,String player){
        Route r=classify(formatted);if(r.outgoing)return true;
        if(r.key.equals(TEAM)||r.key.equals(PARTY))return player.equalsIgnoreCase(r.name);
        return plain(formatted).trim().matches("(?i)^(?:(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)*<"+Pattern.quote(player)+">\\s+|"+Pattern.quote(player)+"\\s*[:>\u00bb]\\s*).+$");
    }
    static String channel(String key){return ALL.equals(key)?"/g":TEAM.equals(key)?"/team":PARTY.equals(key)?"/party chat":null;}
    /**
     * A private line reads as a conversation: the other player's name light purple, our own name
     * plain white, so which end of the message is which is clear at a glance. Only the name in the
     * prefix is touched; the body keeps the server's own colours.
     */
    static net.minecraft.util.IChatComponent privateLine(net.minecraft.util.IChatComponent text,String other,String self){
        String s=text.getFormattedText(),out=colourName(s,other,"\u00a7d");
        if(self!=null&&!self.isEmpty()&&!self.equalsIgnoreCase(other))out=colourName(out,self,"\u00a7f");
        return out.equals(s)?text:new net.minecraft.util.ChatComponentText(out);
    }
    private static String colourName(String s,String name,String code){
        if(name==null||name.isEmpty())return s;
        String lower=s.toLowerCase(Locale.ROOT),needle=name.toLowerCase(Locale.ROOT);
        int at=lower.indexOf(needle);
        // A name in the body is not a name being addressed, so only the prefix is recoloured.
        if(at<0||at>=Math.min(s.length(),64))return s;
        String restore="\u00a7r";Matcher m=FORMAT.matcher(s.substring(0,at));String last=null;
        while(m.find())last=m.group();
        if(last!=null&&"0123456789abcdef".indexOf(Character.toLowerCase(last.charAt(1)))>=0)restore=last;
        return s.substring(0,at)+code+s.substring(at,at+name.length())+restore+s.substring(at+name.length());
    }
    /**
     * Global chat goes out as plain text, the way the vanilla box sends it; only the other
     * channels need PGM's command prefix. A typed command is never rewritten.
     */
    static String outgoing(String selected,String text){
        if(text.startsWith("/")||ALL.equals(selected))return text;
        return (selected.startsWith("pm:")?"/msg "+selected.substring(3):TEAM.equals(selected)?"/t":"/party chat")+" "+text;
    }
    static int inputLimit(String selected,String text,boolean fullChat){return !fullChat||text.startsWith("/")?100:100-outgoing(selected,"").length();}
}
