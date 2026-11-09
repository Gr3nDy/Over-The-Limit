package otl.client;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TeamBubbleRules {
    private static final Pattern JOIN=Pattern.compile("^You joined (?:the )?(.+?)(?: team)?[.!]?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GLOBAL=Pattern.compile("^(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)*<?([A-Za-z0-9_]{1,16})>?\\s*[:>\\u00bb]\\s*(.+)$");
    private static final Pattern TEAM_CHAT=Pattern.compile("^\\(([^)\\r\\n]{1,32})\\)\\s*(?:\\[[^\\]\\r\\n]+\\]\\s*)*<?([A-Za-z0-9_]{1,16})>?\\s*[:>\\u00bb]\\s*(.+)$",Pattern.CASE_INSENSITIVE);
    static final class Chat {
        final String name,team,body;
        Chat(String name,String team,String body){this.name=name;this.team=team;this.body=body;}
    }
    static Chat chat(String formatted){
        ChatRules.Route route=ChatRules.classify(formatted);
        if(!ChatRules.TEAM.equals(route.key))return null;
        Matcher group=TEAM_CHAT.matcher(ChatRules.plain(formatted).trim());
        if(!group.matches())return null;
        String player=group.group(2)!=null?group.group(2).trim():null;
        String team=group.group(1)!=null?group.group(1).trim():null;
        String body=group.group(3)!=null?group.group(3).trim():null;
        return new Chat(player,team,body);
    }
    static boolean joinMessage(String formatted){return JOIN.matcher(ChatRules.plain(formatted).trim()).matches();}
    static char joinColor(String formatted){
        String plain=ChatRules.plain(formatted).trim();Matcher join=JOIN.matcher(plain);
        if(!join.matches()||join.group(1).toLowerCase(Locale.ROOT).matches(".*(?:observer|spectator).*") )return 0;
        return colorAt(formatted,join.start(1));
    }
    static Chat global(String formatted,char teamColor){
        if(!ChatRules.ALL.equals(ChatRules.classify(formatted).key))return null;
        Matcher match=GLOBAL.matcher(ChatRules.plain(formatted).trim());
        if(!match.matches()||colorAt(formatted,match.start(1))!=teamColor)return null;
        return new Chat(match.group(1),"",match.group(2).trim());
    }
    static char colorAt(String formatted,int offset){
        int visible=0;char color=0;
        for(int i=0;i<formatted.length();i++){
            if(formatted.charAt(i)=='\u00a7'&&i+1<formatted.length()){
                char code=Character.toLowerCase(formatted.charAt(++i));
                if("0123456789abcdef".indexOf(code)>=0)color=code;
                else if(code=='r')color=0;
            }else if(visible++==offset)return color;
        }
        return visible==offset?color:0;
    }
    static final class Message {
        final String text;final long until;
        private float from,target;private long movedAt;
        Message(String text,long now,long duration){this.text=text;until=now+duration;from=-.25F;movedAt=now;}
        float slot(long now){float t=Math.max(0,Math.min(1,(now-movedAt)/200000000F));return from+(target-from)*(1-(1-t)*(1-t)*(1-t));}
        void move(int slot,long now){from=slot(now);target=slot;movedAt=now;}
    }
    static final class Stack {
        final List<Message> items=new ArrayList<>();
        void add(String text,long now,long duration){
            expire(now);items.add(0,new Message(text,now,duration));if(items.size()>3)items.remove(3);
            for(int i=1;i<items.size();i++)items.get(i).move(i,now);
        }
        void expire(long now){
            if(items.removeIf(m->m.until<=now))for(int i=0;i<items.size();i++)items.get(i).move(i,now);
        }
    }
    static String limit(String text,int max){
        String s=ChatRules.plain(text).replace('\n',' ').replace('\r',' ').trim();
        int count=s.codePointCount(0,s.length());return count<=max?s:s.substring(0,s.offsetByCodePoints(0,Math.max(0,max-1)))+"\u2026";
    }
}
