package otl.client;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TeamBubbleRules {
    static final class Chat {
        final String name,team,body;
        Chat(String name,String team,String body){this.name=name;this.team=team;this.body=body;}
    }
    static Chat chat(String formatted){
        ChatRules.Route route=ChatRules.classify(formatted);
        if(!ChatRules.TEAM.equals(route.key))return null;
        Matcher group=Pattern.compile("^\\(([^)\\r\\n]{1,32})\\)\\s*(?:\\[[^\\]\\r\\n]+\\]\\s*)*<?([A-Za-z0-9_]{1,16})>?\\s*[:>\\u00bb]\\s*(.+)$",Pattern.CASE_INSENSITIVE).matcher(ChatRules.plain(formatted).trim());
        if(!group.matches())return null;
        String player=group.group(2)!=null?group.group(2).trim():null;
        String team=group.group(1)!=null?group.group(1).trim():null;
        String body=group.group(3)!=null?group.group(3).trim():null;
        return new Chat(player,team,body);
    }
    static final class Cooldown {
        private final Map<UUID,Long> next=new HashMap<>();
        boolean accept(UUID player,long now){
            if(now<next.getOrDefault(player,Long.MIN_VALUE))return false;
            next.put(player,now+2000000000L);return true;
        }
        void expire(long now){next.values().removeIf(until->until<=now);}
        void clear(){next.clear();}
    }
    static String limit(String text,int max){
        String s=ChatRules.plain(text).replace('\n',' ').replace('\r',' ').trim();
        int count=s.codePointCount(0,s.length());return count<=max?s:s.substring(0,s.offsetByCodePoints(0,Math.max(0,max-1)))+"\u2026";
    }
}