package otl.client;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatPolishTest {
    @Test public void partyWarningsAreAnchored(){
        assertTrue(ChatWarnings.party("\u00a7cYou are not in a party. Start one with /party <player>"));
        assertFalse(ChatWarnings.party("(Party) Alex: You are not in a party"));assertFalse(ChatWarnings.party("Alex: You are not in a party"));
    }
    @Test public void privateWarningsAreConservative(){
        assertTrue(ChatWarnings.privateMessage("Player Alex not found."));assertTrue(ChatWarnings.privateMessage("You cannot message yourself."));
        assertFalse(ChatWarnings.privateMessage("Alex: Player Steve not found."));assertFalse(ChatWarnings.privateMessage("Server restarting"));
    }
    @Test public void teamJoinIsExactServerMessage(){
        assertEquals("Blue Team",TeamBubbleRules.joined("\u00a77You Joined \u00a79Blue Team"));assertEquals("Red",TeamBubbleRules.joined("You joined Red."));
        assertNull(TeamBubbleRules.joined("Alex: You Joined Blue"));assertNull(TeamBubbleRules.joined("You left Blue"));
    }
    @Test public void teamPrefixAndBodyAreSeparate(){
        assertEquals("Blue Team",ChatRules.groupTeam("(Blue Team) Alex: hello"));assertEquals("hello",ChatRules.groupBody("(Blue Team) Alex: hello"));assertNull(ChatRules.groupTeam("\u00a7e(Party) Alex: hello"));
    }
    @Test public void bubblesLimitTextWithoutBrokenUnicode(){
        assertEquals("short",TeamBubbleRules.limit("short",80));String s=TeamBubbleRules.limit("12345678901234567890",10);assertEquals("123456789\u2026",s);
        assertEquals("a\u2026",TeamBubbleRules.limit("a\uD83D\uDE00more",2));assertEquals("hello world",TeamBubbleRules.limit("hello\nworld",80));
    }
}
