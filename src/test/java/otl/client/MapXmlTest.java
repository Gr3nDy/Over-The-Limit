package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapXmlTest {
    private static final String INFECTION="<map><constants fallback='true'><constant id='time-limit'>8</constant><constant id='human-team-name'>Humans</constant><constant id='zombie-team-name'>Zombies</constant></constants>"
            +"<objective>${human-team-name} must survive against the ${zombie-team-name} for ${time-limit} minutes!</objective><gamemode>infection</gamemode>"
            +"<teams><team max='4'>${zombie-team-name}</team><team max='36'>${human-team-name}</team></teams></map>";

    @Test public void cataclysmUsesDefaultInfectionRulesNotInactiveOitq(){
        MapInfo map=MapInfo.read("arcade/infection/infection_cataclysm",true,null,id->id.equals("infection")?INFECTION:null);
        assertEquals("Infection: Cataclysm",map.name);
        assertEquals("Humans must survive against the Zombie Militant for 6 minutes!",map.objective);
        assertEquals(40,map.players);assertEquals("Zombie Militant 4, Humans 36",map.teams);
        assertEquals("infection",map.gamemode);assertFalse(map.incomplete);
        assertTrue(map.variants.contains("oitq"));assertTrue(map.authors.contains("JcNumbers"));
    }

    @Test public void missingIncludeIsExplicitAndNeverUsesTheOtherVariant(){
        MapInfo map=MapInfo.read("arcade/infection/infection_cataclysm",true,null,id->null);
        assertEquals("",map.objective);assertEquals(0,map.players);assertTrue(map.incomplete);
        assertFalse(map.objective.contains("time-limit"));
    }

    @Test public void ffaCapacityAndXmlEntitiesAreParsed(){
        MapInfo map=MapInfo.parse("<map><name>A &amp; B</name><players max='16'/><authors><author contribution='XML'>Alice</author></authors></map>","ffa/test",true,null,id->null);
        assertEquals("A & B",map.name);assertEquals(16,map.players);assertEquals("Alice (XML)",map.authors);
    }
    @Test public void partialBundledNameDoesNotPretendToBeAnExactOtherMap(){
        MapInfo map=MapInfo.parse("<map><name>Infection: Cataclysm</name></map>","arcade/infection/infection_cataclysm",true,null,id->null);
        assertFalse(map.exact("Cataclysm"));assertTrue(map.exact("Infection Cataclysm"));
        assertTrue(map.exact("CommunityMaps/master/arcade/infection/infection_cataclysm"));
        assertFalse(map.exact("PublicMaps/main/ctw/cataclysm"));
    }

    @Test public void cyclicIncludesAreBounded() throws Exception {
        MapXml xml=new MapXml("<map><include id='a'/></map>",id->"<map><include id='a'/><objective>Survive</objective></map>");
        assertEquals(1,xml.included.size());assertEquals("Survive",xml.value("objective"));
    }
    @Test public void explicitConstantsWinEvenWhenFallbackAppearsFirst() throws Exception {
        MapXml xml=new MapXml("<map><constants fallback='true'><constant id='time'>8</constant></constants><constants><constant id='time'>6</constant></constants><objective>Survive ${time} minutes</objective></map>",id->null);
        assertEquals("Survive 6 minutes",xml.value("objective"));
    }

    @Test(expected=org.xml.sax.SAXParseException.class) public void externalEntitiesAreRejected() throws Exception {
        new MapXml("<!DOCTYPE map [<!ENTITY secret SYSTEM 'file:///no-access'>]><map><name>&secret;</name></map>",id->null);
    }
}
