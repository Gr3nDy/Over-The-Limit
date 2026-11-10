package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class TeamBubbleRulesTest {
    @Test public void bubblesRequireTeamsAndRejectFreeForAllEvenWithSharedColors(){
        assertFalse(TeamBubbleRules.teamMap(null));
        assertFalse(TeamBubbleRules.teamMap(MapInfo.parse("<map><gamemode>ffa</gamemode><players max='24' colors='true'/></map>","ffa/test",true,null,id->null)));
        assertFalse(TeamBubbleRules.teamMap(MapInfo.parse("<map><gamemode>deathmatch</gamemode><players max='24'/></map>","arcade/test",true,null,id->null)));
        for(String mode:new String[]{"FFA","Free-for-All"})
            assertFalse(TeamBubbleRules.teamMap(MapInfo.parse("<map><gamemode>"+mode+"</gamemode><teams><team max='24'>Players</team></teams></map>","arcade/test",true,null,id->null)));
        assertTrue(TeamBubbleRules.teamMap(MapInfo.parse("<map><gamemode>tdm</gamemode><teams><team max='12'>Blue</team><team max='12'>Red</team></teams></map>","tdm/test",true,null,id->null)));
    }
    @Test public void extractsTeamChatWithoutJoinOrColorInformation(){
        TeamBubbleRules.Chat team=TeamBubbleRules.chat("(Orange) [Member] Alex: defend the wool");
        assertNotNull(team);assertEquals("Alex",team.name);assertEquals("defend the wool",team.body);
        assertEquals("hello",TeamBubbleRules.chat("\u00a7b(Blue Team) \u00a7cAlex\u00a7r: hello").body);
        assertEquals("Alex",TeamBubbleRules.chat("(Unknown Team) <Alex>: hello").name);
        assertEquals("observer hello",TeamBubbleRules.chat("(Observers) Alex: observer hello").body);
    }
    @Test public void ignoresGlobalPrivatePartyAndSystemMessages(){
        assertNull(TeamBubbleRules.chat("\u00a7b<Alex>: hello observers"));
        assertNull(TeamBubbleRules.chat("From Alex: secret"));
        assertNull(TeamBubbleRules.chat("\u00a7e(Party) Alex: secret"));
        assertNull(TeamBubbleRules.chat("Alex joined the game"));assertNull(TeamBubbleRules.chat("Objective: Capture wool"));
        assertNull(TeamBubbleRules.chat("You joined Blue"));
        assertNull(TeamBubbleRules.chat("<Alex>: (Blue) Bob: quoted text"));
    }
    @Test public void globalChatNeedsTheColorOfARealTeamJoin(){
        assertEquals('9',TeamBubbleRules.joinColor("\u00a7eYou joined \u00a79Blue\u00a7r team!"));
        assertEquals(0,TeamBubbleRules.joinColor("\u00a7eYou joined \u00a77Observers"));
        assertEquals(0,TeamBubbleRules.joinColor("You joined Blue"));
        assertEquals("hello",TeamBubbleRules.global("\u00a79<BubbleMate>\u00a7r: hello",'9').body);
        assertNull(TeamBubbleRules.global("\u00a7c<BubbleOther>: hello",'9'));
        assertNull(TeamBubbleRules.global("\u00a79(Blue) BubbleMate: team chat",'9'));
        assertNull(TeamBubbleRules.global("\u00a79<Server>: hello",'c'));
    }
    @Test public void readsAColorFromAFormattedTeamPrefix(){
        assertEquals('9',TeamBubbleRules.colorAt("\u00a79",0));
        assertEquals('c',TeamBubbleRules.colorAt("\u00a7l\u00a7c",0));
        assertEquals(0,TeamBubbleRules.colorAt("Blue",0));
    }
    @Test public void stacksThreeMessagesAndSlidesOlderOnesUp(){
        TeamBubbleRules.Stack stack=new TeamBubbleRules.Stack();stack.add("first",0,4000000000L);
        stack.add("second",200000000L,4000000000L);
        assertEquals("first",stack.items.get(1).text);assertEquals(0,stack.items.get(1).slot(200000000L),.001);
        assertTrue(stack.items.get(1).slot(300000000L)>0);assertEquals(1,stack.items.get(1).slot(400000000L),.001);
        stack.add("third",400000000L,4000000000L);stack.add("fourth",500000000L,4000000000L);
        assertEquals(3,stack.items.size());assertEquals("fourth",stack.items.get(0).text);assertEquals("second",stack.items.get(2).text);
        assertEquals(2,stack.items.get(2).slot(700000000L),.001);
        stack.expire(5000000000L);assertTrue(stack.items.isEmpty());
    }
}
