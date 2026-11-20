package otl.client;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatRulesTest {
    @org.junit.Test public void commandWarningsAreAnchoredAndFormattingIndependent(){
        org.junit.Assert.assertTrue(ChatRules.commandWarning("\u00a7c\u26a0 Unknown command"));
        org.junit.Assert.assertTrue(ChatRules.commandWarning(" \u26a0\ufe0f No permission"));
        org.junit.Assert.assertFalse(ChatRules.commandWarning("<Alex>: \u26a0 No permission"));
        org.junit.Assert.assertFalse(ChatRules.commandWarning("(Blue) Alex: \u26a0 No permission"));
    }
    @org.junit.Test public void noticesExcludePlayerChat(){
        org.junit.Assert.assertTrue(ChatRules.playerMessage("\u00a79<Alex>: hello"));
        org.junit.Assert.assertTrue(ChatRules.playerMessage("[Member] <Alex> hello"));
        org.junit.Assert.assertTrue(ChatRules.playerMessage("(Blue Team) Alex: defend"));
        org.junit.Assert.assertTrue(ChatRules.playerMessage("\u00a7e(Party) Alex: hello"));
        org.junit.Assert.assertTrue(ChatRules.playerMessage("From Alex: secret"));
        org.junit.Assert.assertFalse(ChatRules.playerMessage("\u00a7c\u26a0 Unknown command"));
        org.junit.Assert.assertFalse(ChatRules.playerMessage("Match starts in 10 seconds"));
    }
    @Test public void channelPrefixesFitThePacketLimit(){
        for(String key:new String[]{"all","team","party","pm:abcdefghijklmnop"}){
            int limit=ChatRules.inputLimit(key,"hello",true);
            String body=String.join("",java.util.Collections.nCopies(limit,"x"));
            assertEquals(100,ChatRules.outgoing(key,body).length());
            assertEquals(100,ChatRules.inputLimit(key,"/command",true));
            assertEquals(100,ChatRules.inputLimit(key,"lobby text",false));
        }
        assertEquals(88,ChatRules.inputLimit("party","hello",true));
    }
    @Test public void allowlistIsExact(){
        assertEquals("172.65.223.142",ServerScope.host("172.65.223.142"));
        assertEquals("172.65.223.142",ServerScope.host("172.65.223.142:25565"));
        assertEquals("",ServerScope.host("172.65.223.142.evil.net"));
        assertEquals("play.oc.tc",ServerScope.host("PLAY.OC.TC:25565"));assertEquals("vanish.oc.tc",ServerScope.host("vanish.oc.tc"));
        assertEquals("spectrum.oc.tc",ServerScope.host("spectrum.oc.tc:25565"));
        for(String s:new String[]{"oc.tc","evilplay.oc.tc","play.oc.tc.evil.net","localhost","127.0.0.1","play.oc.tc:notaport",""})assertEquals("",ServerScope.host(s));
    }
    @Test public void privateNamesAndDirection(){
        ChatRules.Route from=ChatRules.classify("\u00a77\u00a7oFrom: \u00a7bAlex\u00a7r hello");assertEquals("pm:alex",from.key);assertEquals("Alex",from.name);assertFalse(from.outgoing);
        ChatRules.Route to=ChatRules.classify("\u00a77\u00a7oTo: \u00a7bSteve\u00a7r: hello");assertEquals("pm:steve",to.key);assertTrue(to.outgoing);assertEquals("hello",ChatRules.privateBody("To: Steve: hello"));
    }
    @Test public void privatePrefixesWithAndWithoutDirectionColon(){
        for(String prefix:new String[]{"From Alex: ","From:Alex ","From: Alex: ","From Alex > ","From Alex \u00bb ","From [Member] Alex: ","From <Alex>: ","\u00a77\u00a7oFrom \u00a7bAlex\u00a7f: ","From\u00a0Alex\u202f: ","From\u2009Alex\u2009: "}){
            ChatRules.Route incoming=ChatRules.classify(prefix+"hello");assertEquals(prefix,"pm:alex",incoming.key);assertFalse(incoming.outgoing);assertEquals(prefix,"hello",ChatRules.privateBody(prefix+"hello"));
            String to=prefix.replace("From","To");ChatRules.Route outgoing=ChatRules.classify(to+"hello");assertEquals(to,"pm:alex",outgoing.key);assertTrue(outgoing.outgoing);assertEquals(to,"hello",ChatRules.privateBody(to+"hello"));
        }
    }
    @Test public void privateConversationIdentityAndBodies(){
        assertEquals(ChatRules.classify("From Alex: first").key,ChatRules.classify("To ALEX: second").key);
        assertEquals("first\nsecond",ChatRules.privateBody("From Alex: first\nsecond"));
        for(String s:new String[]{"Alex: From Bob: hello","(Red) Alex: To Bob: hello","Fromage: hello","Tom: hello","To Alex","From: Alex"})assertFalse(s,ChatRules.classify(s).key.startsWith("pm:"));
        assertEquals("/r reply",ChatRules.outgoing("team","/r reply"));assertEquals("/r reply",ChatRules.outgoing("pm:wrongperson","/r reply"));
    }
    @Test public void teamAndYellowPartyOnly(){
        assertEquals("team",ChatRules.classify("\u00a79(Blue Team) Alex: hello").key);
        assertEquals("party",ChatRules.classify("\u00a7e(Party) \u00a7bAlex\u00a7f: hello").key);
        assertEquals("all",ChatRules.classify("\u00a77(Party) Alex: hello").key);
        assertEquals("all",ChatRules.classify("Alex: I said (Party) yesterday").key);
        assertEquals("all",ChatRules.classify("Announcement (Blue): game starts").key);
        assertEquals("all",ChatRules.classify("(Restart) in 10 seconds").key);
    }
    @Test public void pgmChannelsLandInTheirOwnTab(){
        // The shapes PGM's own channel formatters produce: team chat is "(<party name>) <name>: text"
        // in the party's colour, global is "<name>: text", private is "To/From <name>: text".
        assertEquals("team",ChatRules.classify("\u00a7c(Red) \u00a7fAlex\u00a7f: hello").key);
        assertEquals("team",ChatRules.classify("\u00a79(Blue) \u00a7fAlex\u00a7f: hello").key);
        assertEquals("team",ChatRules.classify("\u00a77(Obs) \u00a7fAlex\u00a7f: hello").key);
        assertEquals("team",ChatRules.classify("\u00a7a(Green) \u00a7f[VIP] Alex\u00a7f: hello").key);
        assertEquals("all",ChatRules.classify("\u00a7f<Alex>: hello").key);
        assertEquals("pm:alex",ChatRules.classify("\u00a77To \u00a7fAlex\u00a7f: hello").key);
        assertEquals("pm:alex",ChatRules.classify("\u00a77From \u00a7fAlex\u00a7f: hello").key);
        // A name whose rank tag sits inside the brackets is still the same line.
        assertEquals("team",ChatRules.classify("\u00a7c(Red) \u00a7fAlex\u00a7f: gg \u00a7cwell played").key);
    }
    @Test public void commandsAreExactAndNeverPrivatePrefixed(){
        assertEquals("/msg alex hello",ChatRules.outgoing("pm:alex","hello"));assertEquals("/spawn",ChatRules.outgoing("pm:alex","/spawn"));assertEquals("/t hello",ChatRules.outgoing("team","hello"));assertEquals("hello",ChatRules.outgoing("all","hello"));assertEquals("/party chat hello",ChatRules.outgoing("party","hello"));
        // The tab decides the channel: All is always bare text, and a typed command is never rewritten.
        assertEquals("/t",ChatRules.outgoing("all","/t"));assertEquals("/g",ChatRules.outgoing("team","/g"));
    }
    @Test public void ownMessagesDoNotCountUnread(){
        assertTrue(ChatRules.authoredBy("To: Alex hello","Me"));assertTrue(ChatRules.authoredBy("(Blue) Me: hello","Me"));assertTrue(ChatRules.authoredBy("\u00a7e(Party) Me: hello","Me"));
        assertFalse(ChatRules.authoredBy("From: Alex hello","Me"));assertFalse(ChatRules.authoredBy("(Blue) Alex: hello","Me"));
        // Rank and guild tags sit on either side of the name; none of them hide the sender.
        assertTrue(ChatRules.authoredBy("(Blue) Me [VIP]: hello","Me"));assertTrue(ChatRules.authoredBy("[VIP] <Me> hello","Me"));
        assertTrue(ChatRules.authoredBy("(Blue) [VIP] Me [MOD]: hello","Me"));
        assertEquals("team",ChatRules.classify("(Blue) Me [VIP]: hello").key);
    }
    @Test public void serverProseNeverCreatesPhantomPrivateTabs(){for(String s:new String[]{"to your monument!","\u00a79\u00a7oto your monument!","From the start of the match","To win capture the wool"})assertEquals(s,"all",ChatRules.classify(s).key);}
}
