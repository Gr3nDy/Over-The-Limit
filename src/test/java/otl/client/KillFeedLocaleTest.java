package otl.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * PGM translates a death line into the reader's own language, so the English templates never fire
 * for a non-English client. The bundled per-language templates are the only thing that keeps the
 * cause, the killer and the weapon on those lines; without them a foreign death is a bare skull.
 */
public class KillFeedLocaleTest {
    @Test public void englishMobRuleDoesNotSwallowPlayerAndSword(){
        List<KillFeedRules.Rule> rules=KillFeedRules.langRules("death.attack.mob=%1$s was slain by %2$s");
        KillFeedRules.Death death=KillFeedRules.parseRules("Alex was slain by Player's Iron Sword",null,rules);
        assertEquals("Player",death.killer);
        assertEquals("Iron Sword",death.weapon);
        assertEquals(KillFeedRules.Cause.MELEE,death.cause);
        assertEquals("Zombie",KillFeedRules.parseRules("Alex was slain by Zombie",null,rules).mob);
    }
    private static KillFeedRules.Death parse(String line,String locale){
        KillFeedRules.Death death=KillFeedRules.parse(line,locale);
        assertNotNull("A death line in "+locale+" was not recognised: "+line,death);
        return death;
    }

    /** Every template in the bundled file, filled in, must match back to itself. */
    @Test public void everyBundledTemplateMatchesItsOwnWording() throws Exception{
        List<String> failures=new ArrayList<>();
        int checked=0;
        for(String entry:entries()){
            int bar=entry.indexOf('|');
            String locale=entry.substring(0,bar);
            String rest=entry.substring(bar+1);
            int pipe=rest.indexOf('|');
            String template=rest.substring(pipe+1);
            String line=template.replace("{0}","Bob").replace("{1}","Alice")
                .replace("{2}","Iron Sword").replace("{3}","Zombie").replace("{4}","5")
                .replace("''","'");
            checked++;
            if(KillFeedRules.parse(line,locale)==null)failures.add(locale+" :: "+line);
        }
        assertTrue("No templates were read",checked>1000);
        assertTrue(failures.size()+" of "+checked+" templates did not match:\n"
            +String.join("\n",failures.subList(0,Math.min(15,failures.size()))),failures.isEmpty());
    }

    @Test public void germanLinesKeepKillerAndCause(){
        KillFeedRules.Death blast=parse("Bob wurde von Alice in die Luft gesprengt","de_DE");
        assertEquals("Bob",blast.victim);
        assertEquals("Alice",blast.killer);
        assertEquals(KillFeedRules.Cause.EXPLOSION,blast.cause);
    }

    @Test public void aDeathWithNoAttackerIsStillRecognised(){
        // Nobody to read from the names, so only the template can say what happened.
        KillFeedRules.Death fall=parse("Bob ist von einem hohen Ort heruntergefallen","de_DE");
        assertTrue("A fall was read as a player kill",fall.suicide());
        assertEquals(KillFeedRules.Cause.FALL,fall.cause);
        assertEquals(KillFeedRules.Context.FALL,fall.context());
        // One icon only: nobody attacked, so there is nothing extra to add at the end.
        assertFalse(fall.extraContext());
    }

    @Test public void theClientsOwnLanguageSelectsItsTemplates(){
        // The client reports "de_DE"; the bundled keys must be found without guessing.
        assertNotNull(KillFeedRules.parse("Bob wurde von Alice in die Luft gesprengt","de_DE"));
        // A locale the jar has no wording for falls back to the two names, so the row still appears.
        KillFeedRules.Death byNames=KillFeedRules.parse("Bob t\u00f6tete Alice",
            name->name.equals("Bob")||name.equals("Alice"),"xx_XX");
        assertNotNull(byNames);
        assertEquals("Bob",byNames.victim);
        assertEquals("Alice",byNames.killer);
    }

    /**
     * PGM translates into the nearest bundle it ships, so the client's own locale often has no key
     * here. Missing that would drop the death entirely, which is what "the feed only works in
     * English" looks like.
     */
    @Test public void aLocaleWithoutItsOwnBundleReadsTheNearestOne(){
        // Portugal is sent the Brazilian wording; Spanish Latin America and Taiwan the same.
        KillFeedRules.Death portuguese=parse("Bob sentiu a f\u00faria dos punhos de Alice","pt_PT");
        assertEquals("Bob",portuguese.victim);
        assertEquals("Alice",portuguese.killer);
        assertEquals(KillFeedRules.Cause.MELEE,portuguese.cause);

        KillFeedRules.Death spanish=parse("Bob sinti\u00f3 la furia de los pu\u00f1os de Alice","es_UY");
        assertEquals("Alice",spanish.killer);
        assertEquals(KillFeedRules.Cause.MELEE,spanish.cause);

        // The Chinese template, built from codepoints so the source stays ASCII.
        String chinese="Bob "+new String(new char[]{34987,19968,21482})+" Zombie "+new String(new char[]{26432,27515,20102});
        KillFeedRules.Death zh=parse(chinese,"zh_TW");
        assertEquals("Zombie",zh.mob);
        assertEquals(KillFeedRules.Cause.MELEE,zh.cause);

        // Norwegian Bokmal's language code differs from PGM's no_NO, so the country has to match.
        KillFeedRules.Death norwegian=parse("Bob f\u00f8lte raseriet til Alice's never","nb_NO");
        assertEquals("Alice",norwegian.killer);
        assertEquals(KillFeedRules.Cause.MELEE,norwegian.cause);
    }

    @Test public void anEnglishLineStillWorksWhenTheClientIsNotEnglish(){
        // PGM has no bundle for this language, so it sends its English wording.
        KillFeedRules.Death death=parse("Bob was slain by Alice's Iron Sword","tr_TR");
        assertEquals("Bob",death.victim);
        assertEquals("Alice",death.killer);
        assertEquals(KillFeedRules.Cause.MELEE,death.cause);
    }

    /**
     * PGM sends the death line as a translatable component, so the client renders the words from
     * its own language file. Taiwan writes them in Traditional Chinese, which no shipped bundle
     * matches: reading the client's own file is what keeps the cause there.
     */
    @Test public void theClientsOwnLanguageFileSuppliesTheWording(){
        String zhTW="death.attack.player=%1$s \u88ab %2$s \u6bba\u6b7b\u4e86\n"
            +"death.attack.player.item=%1$s \u88ab %2$s \u7528 %3$s \u6bba\u6b7b\u4e86\n"
            +"death.attack.arrow=%1$s \u88ab %2$s \u5c04\u6bba\u4e86\n"
            +"death.fell.accident.generic=%1$s \u5f9e\u9ad8\u8655\u6454\u6b7b\u4e86\n"
            +"chat.type.text=<%s> %s\n";
        List<KillFeedRules.Rule> rules=KillFeedRules.langRules(zhTW);
        assertEquals("Only the death keys are read",4,rules.size());
        KillFeedRules.Death kill=matchFirst(rules,"Bob \u88ab Alice \u6bba\u6b7b\u4e86");
        assertEquals("Bob",kill.victim);
        assertEquals("Alice",kill.killer);
        assertEquals(KillFeedRules.Cause.MELEE,kill.cause);
        assertEquals(KillFeedRules.Cause.BOW,matchFirst(rules,"Bob \u88ab Alice \u5c04\u6bba\u4e86").cause);
        KillFeedRules.Death fall=matchFirst(rules,"Bob \u5f9e\u9ad8\u8655\u6454\u6b7b\u4e86");
        assertEquals(KillFeedRules.Cause.FALL,fall.cause);
        assertEquals(KillFeedRules.Context.FALL,fall.context());
        assertTrue("a fall was read as a player kill",fall.suicide());
    }

    /** Java's `%1$s` and `%s` become PGM's numbered arguments, so the wording can be reused as-is. */
    @Test public void javaPlaceholdersBecomePgmArguments(){
        assertEquals("{0} was slain by {1}",KillFeedRules.placeholders("%1$s was slain by %2$s"));
        assertEquals("{0} was shot by {1} using {2}",KillFeedRules.placeholders("%s was shot by %s using %s"));
        assertEquals("{0} fell {4} blocks",KillFeedRules.placeholders("%1$s fell %5$s blocks"));
        assertEquals("100% sure",KillFeedRules.placeholders("100%% sure"));
    }

    /** Vanilla's mob key fills the killer slot with a mob, and a lava death keeps its lava context. */
    @Test public void aMobNameIsReadAsTheMobAndLavaKeepsItsContext(){
        List<KillFeedRules.Rule> rules=KillFeedRules.langRules(
            "death.attack.mob=%1$s was slain by %2$s\n"
            +"death.attack.lava=%1$s tried to swim in lava\n");
        KillFeedRules.Death mob=matchFirst(rules,"Bob was slain by Zombie");
        assertEquals("Zombie",mob.mob);
        assertNull("a mob name was read as a weapon",mob.weapon);
        KillFeedRules.Death lava=matchFirst(rules,"Bob tried to swim in lava");
        assertEquals(KillFeedRules.Context.LAVA,lava.context());
    }

    private static KillFeedRules.Death matchFirst(List<KillFeedRules.Rule> rules,String line){
        for(KillFeedRules.Rule rule:rules){
            java.util.regex.Matcher m=rule.pattern.matcher(line);
            if(m.matches())return new KillFeedRules.Death(line,m,rule);
        }
        fail("No rule matched: "+line);
        return null;
    }

    private static List<String> entries() throws Exception{
        List<String> out=new ArrayList<>();
        try(InputStream in=KillFeedRules.class.getResourceAsStream("/assets/otl/death-messages.txt")){
            assertNotNull("The bundled death messages are missing from the jar",in);
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            for(String line;(line=reader.readLine())!=null;){
                if(line.isEmpty()||line.charAt(0)=='#')continue;
                int bar=line.indexOf('|');
                if(bar<1)continue;
                out.add(line);
            }
        }
        return out;
    }
}
