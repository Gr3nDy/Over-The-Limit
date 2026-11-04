package otl.client;
import java.util.regex.*;

final class TeamBubbleRules {
    private static final Pattern JOIN=Pattern.compile("^You joined\\s+(.{1,48}?)[.!]?$",Pattern.CASE_INSENSITIVE);
    static String joined(String formatted){Matcher m=JOIN.matcher(ChatRules.plain(formatted).trim());return m.matches()?m.group(1).trim():null;}
    static String limit(String text,int max){
        String s=ChatRules.plain(text).replace('\n',' ').replace('\r',' ').trim();
        int count=s.codePointCount(0,s.length());return count<=max?s:s.substring(0,s.offsetByCodePoints(0,Math.max(0,max-1)))+"\u2026";
    }
}
