package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class BowAccuracyTest {
    @Test public void prefixCanBeHiddenWithoutChangingThePercentage(){
        boolean previous=UtilitySettings.bowAccuracyPrefix;
        try{
            UtilitySettings.bowAccuracyPrefix=true;assertEquals("Bow Accuracy: 33.33%",BowAccuracy.hudText("33.33%"));
            UtilitySettings.bowAccuracyPrefix=false;assertEquals("33.33%",BowAccuracy.hudText("33.33%"));
            assertEquals("--%",BowAccuracy.hudText("--%"));
        }finally{UtilitySettings.bowAccuracyPrefix=previous;}
    }
    @Test public void percentageUsesPlayerHitsAndReleasedShotsWithConfigurableDecimals(){
        BowAccuracy.Stats stats=new BowAccuracy.Stats();
        assertEquals("0.00%",stats.percentage(2));
        stats.hit();assertEquals(0,stats.hits);
        stats.shot();stats.shot();stats.shot();stats.hit();
        assertEquals("33.33%",stats.percentage(2));
        assertEquals("33.3%",stats.percentage(1));
        assertEquals("33%",stats.percentage(0));
        stats.hit();stats.hit();stats.hit();assertEquals(3,stats.hits);
        assertEquals("100.00%",stats.percentage(2));
    }
    @Test public void matchEndResetsAndStopsCountingUntilTheNextMatch(){
        for(String ending:new String[]{"Game over!","Tied!","Your team won!","Your team lost","Red wins!","Attackers win!","Admin has ended the match."}){
            BowAccuracy.Stats stats=new BowAccuracy.Stats();stats.shot();stats.hit();
            long generation=stats.generation;stats.notice(ending);
            assertEquals(ending,0,stats.shots);assertEquals(0,stats.hits);assertTrue(stats.ended);
            assertTrue(stats.generation>generation);
            stats.shot();stats.hit();assertEquals(0,stats.shots);
            stats.notice("The match has started!");stats.shot();assertEquals(1,stats.shots);assertFalse(stats.ended);
        }
    }
    @Test public void mapChangesResetButPlayerQuotesAndMapLookupsDoNot(){
        BowAccuracy.Stats stats=new BowAccuracy.Stats();
        stats.notice("Playing Allure by Author");stats.shot();stats.hit();
        stats.notice("Playing Allure by Author");assertEquals(1,stats.shots);
        stats.notice("<Alex>: Game over!");stats.notice("Alex: Red wins!");
        stats.notice("From Alex: Game over!");stats.notice("(Blue) Alex: Game over!");
        stats.notice("XML: OvercastCommunity/PublicMaps ctw/other_map");
        assertEquals(1,stats.shots);assertFalse(stats.ended);
        stats.notice("Playing Hallows by Author");assertEquals(0,stats.shots);assertEquals(0,stats.hits);
        stats.shot();stats.clear();assertNull(stats.map);assertEquals(0,stats.shots);
    }
    @Test public void multilineAndFormattedMatchMessagesAreRecognized(){
        BowAccuracy.Stats stats=new BowAccuracy.Stats();stats.shot();stats.hit();
        stats.notice("\u00a76Game over!\n\u00a7cRed wins!");
        assertEquals(0,stats.hits);assertTrue(stats.ended);
        stats.notice("Go!");stats.shot();assertEquals("0.00%",stats.percentage(2));assertFalse(stats.ended);
    }
    @Test public void hitMarkerIsWhiteOnHitRedOnKillAndThenExpires(){
        BowAccuracy.Feedback feedback=new BowAccuracy.Feedback();long now=1000000000L;
        assertEquals(0,feedback.color(now));
        feedback.hit(now);assertEquals(0xFFFFFFFF,feedback.color(now));
        feedback.kill(now+100000000L);assertEquals(0xFFFF5555,feedback.color(now+100000000L));
        feedback.hit(now+200000000L);assertEquals(0xFFFF5555,feedback.color(now+200000000L));
        assertEquals(0,feedback.color(now+500000000L));
        feedback.kill(now+600000000L);feedback.clear();assertEquals(0,feedback.color(now+600000000L));
    }
    @Test public void onlyOneFreshHitSoundCanBeReplacedPerConfirmation(){
        BowAccuracy.Feedback feedback=new BowAccuracy.Feedback();long now=1000000000L;
        assertFalse(feedback.sound(now));feedback.hit(now);
        assertTrue(feedback.sound(now+1));assertFalse(feedback.sound(now+2));
        feedback.hit(now);assertFalse(feedback.sound(now+500000000L));
        feedback.hit(now);feedback.clear();assertFalse(feedback.sound(now+1));
    }
    @Test public void settingsAreClampedAndBowHudPositionIsRecognized() throws Exception {
        boolean master=UtilitySettings.bowOptimizer,optimization=UtilitySettings.bowOptimization,accuracy=UtilitySettings.bowAccuracy;
        boolean sound=UtilitySettings.bowHitSound,marker=UtilitySettings.bowHitMarker,overlay=UtilitySettings.heightLimitOverlay,prefix=UtilitySettings.bowAccuracyPrefix;
        int opacity=UtilitySettings.bowAccuracyOpacity,decimals=UtilitySettings.bowAccuracyDecimals,size=UtilitySettings.bowAccuracySize,markerSize=UtilitySettings.bowHitMarkerSize;
        try{
            com.google.gson.JsonObject settings=new com.google.gson.JsonObject();
            settings.addProperty("bow_optimizer",true);settings.addProperty("bow_optimization",false);settings.addProperty("bow_accuracy",true);
            settings.addProperty("bow_accuracy_prefix",false);
            settings.addProperty("bow_accuracy_opacity",150);settings.addProperty("bow_accuracy_decimals",9);settings.addProperty("bow_accuracy_size",1);
            settings.addProperty("bow_hit_sound",true);settings.addProperty("bow_hit_marker",true);settings.addProperty("bow_hit_marker_size",999);
            java.lang.reflect.Method read=UtilitySettings.class.getDeclaredMethod("read",com.google.gson.JsonObject.class);read.setAccessible(true);read.invoke(null,settings);
            assertTrue(UtilitySettings.bowOptimizer);assertFalse(UtilitySettings.bowOptimization);assertTrue(UtilitySettings.bowAccuracy);
            assertFalse(UtilitySettings.bowAccuracyPrefix);
            assertEquals(100,UtilitySettings.bowAccuracyOpacity);assertEquals(2,UtilitySettings.bowAccuracyDecimals);assertEquals(50,UtilitySettings.bowAccuracySize);
            assertTrue(UtilitySettings.bowHitSound);assertTrue(UtilitySettings.bowHitMarker);assertEquals(200,UtilitySettings.bowHitMarkerSize);
            assertTrue(HudLayout.valid("bow_accuracy"));
        }finally{
            UtilitySettings.bowOptimizer=master;UtilitySettings.bowOptimization=optimization;UtilitySettings.bowAccuracy=accuracy;
            UtilitySettings.bowHitSound=sound;UtilitySettings.bowHitMarker=marker;UtilitySettings.heightLimitOverlay=overlay;
            UtilitySettings.bowAccuracyPrefix=prefix;
            UtilitySettings.bowAccuracyOpacity=opacity;UtilitySettings.bowAccuracyDecimals=decimals;UtilitySettings.bowAccuracySize=size;UtilitySettings.bowHitMarkerSize=markerSize;
        }
    }
}
