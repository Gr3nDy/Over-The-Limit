package otl.client;
import java.util.*;

final class TeamBubbleRules {
    static final class Chat {
        final String name,body;
        Chat(String name,String body){this.name=name;this.body=body;}
    }
    static Chat chat(String formatted){
        ChatRules.Route route=ChatRules.classify(formatted);
        return ChatRules.TEAM.equals(route.key)?new Chat(route.name,ChatRules.groupBody(formatted)):null;
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
