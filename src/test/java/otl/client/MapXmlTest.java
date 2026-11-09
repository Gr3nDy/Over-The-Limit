package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.BitSet;

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
    @Test public void overlayRequiresAnExplicitGlobalBuildLimit(){
        assertEquals(35,MapInfo.read("ctw/allure",true,null).maxBuildHeight);
        assertEquals(34,HeightLimitOverlay.layer(35));
        assertEquals(24,MapInfo.parse("<map><constants><constant id='limit'>25</constant></constants><maxbuildheight>${limit}</maxbuildheight></map>","ctw/test",true,null,id->null).maxBuildHeight-1);
        assertEquals(-1,MapInfo.parse("<map><maxbuildheight place='tnt'>25</maxbuildheight></map>","ctw/test",true,null,id->null).maxBuildHeight);
        assertEquals(-1,MapInfo.parse("<map><above id='height-limit' y='25'/></map>","ctw/test",true,null,id->null).maxBuildHeight);
        assertEquals(-1,HeightLimitOverlay.layer(0));
    }
    @Test public void mapKitMaterialsAreCollectedForLimitOverlayFiltering(){
        MapInfo map=MapInfo.parse("<map><kits><kit><item material='wool'/><item material='stone sword'/></kit></kits></map>","ctw/test",true,null,id->null);
        assertTrue(map.kitBlocks.contains("wool"));
        assertTrue(map.kitBlocks.contains("stonesword"));
    }
    @Test public void heightOverlayOnlyTracksNewBlocksAndClearsRemovedOnes(){
        BitSet terrain=new BitSet(256),added=new BitSet(256),current=new BitSet(256);
        terrain.set(5);current.set(5);HeightLimitOverlay.update(terrain,added,current);
        assertTrue(added.isEmpty());
        current.set(9);HeightLimitOverlay.update(terrain,added,current);
        assertFalse(added.get(5));assertTrue(added.get(9));
        current.clear(9);HeightLimitOverlay.update(terrain,added,current);
        assertTrue(added.isEmpty());
        current.clear(5);HeightLimitOverlay.update(terrain,added,current);
        current.set(5);HeightLimitOverlay.update(terrain,added,current);
        assertTrue(added.get(5));
    }
    @Test public void chunkRescansAreThrottledToEveryFourthTick(){
        for(int tick=0;tick<12;tick++)assertEquals(tick%4==0,HeightLimitOverlay.scanDue(tick));
    }
    @Test public void heightIndicatorOnlyShowsNearTheLimitAndFades(){
        assertTrue(HeightLimitOverlay.nearLimit(30,34));
        assertTrue(HeightLimitOverlay.nearLimit(38,34));
        assertFalse(HeightLimitOverlay.nearLimit(29,34));
        assertFalse(HeightLimitOverlay.nearLimit(39,34));
        assertFalse(HeightLimitOverlay.nearLimit(34,-1));
        assertEquals(1F,HeightLimitOverlay.fade(0,4),0.001F);
        assertEquals(.5F,HeightLimitOverlay.fade(2000000000L,4),0.001F);
        assertEquals(0F,HeightLimitOverlay.fade(5000000000L,4),0.001F);
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
