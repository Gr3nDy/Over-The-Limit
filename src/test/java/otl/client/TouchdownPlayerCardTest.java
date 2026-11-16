package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchdownPlayerCardTest {
    @Test public void recentlySeenArmorExpiresWithoutInventingEquipment(){
        net.minecraft.item.ItemStack[] items=new net.minecraft.item.ItemStack[2];
        TouchdownCard.ArmorSnapshot snapshot=new TouchdownCard.ArmorSnapshot(items,100);
        assertSame(items,TouchdownCard.recentArmor(snapshot,100));
        assertSame(items,TouchdownCard.recentArmor(snapshot,10_000_000_100L));
        assertNull(TouchdownCard.recentArmor(snapshot,10_000_000_101L));
        assertNull(TouchdownCard.recentArmor(snapshot,99));
        assertNull(TouchdownCard.recentArmor(null,100));
    }
    @Test public void scoreLineHasAReadableFallback(){
        assertEquals("SCORE: 2 - 1",TouchdownCard.scoreLabel("2 - 1"));
        assertEquals("Score unavailable",TouchdownCard.scoreLabel(null));
    }
    @Test public void readsOnlyUnambiguousPgmSymbolicScoreRows(){
        assertArrayEquals(new int[]{'c',2},TouchdownCard.scoreRow("\u00a7c\u2794 \u2b24\u2b24\u00a77\u2b24\u2b24\u2b24"));
        assertArrayEquals(new int[]{'9',5},TouchdownCard.scoreRow("\u00a79\u2794 \u2b24\u2b24\u2b24\u2b24\u2b24"));
        assertArrayEquals(new int[]{'c',0},TouchdownCard.scoreRow("\u00a7c\u2794 \u00a77\u2b24\u2b24\u2b24\u2b24\u2b24"));
        assertNull(TouchdownCard.scoreRow("\u00a77\u2794 \u2b24\u2b24"));
        assertNull(TouchdownCard.scoreRow("\u00a7c\u2794 \u2b24\u00a79\u2b24"));
        assertNull(TouchdownCard.scoreRow("\u00a7cFlag \u2b24"));
    }
    @Test public void onlyImmediateCatchByTheScorerCanBeAPass(){
        long second=1_000_000_000L;
        assertTrue(TouchdownCard.validPass("Passer","Scorer","Scorer",second,2*second,3*second));
        assertFalse(TouchdownCard.validPass("Passer","Scorer","Scorer",second,2*second,5*second));
        assertFalse(TouchdownCard.validPass("Passer","Other","Scorer",second,2*second,3*second));
        assertFalse(TouchdownCard.validPass("Scorer","Scorer","Scorer",second,2*second,3*second));
        assertFalse(TouchdownCard.validPass(null,"Scorer","Scorer",second,2*second,3*second));
    }
    @Test public void slideStylesMoveInTheirChosenDirection(){
        assertEquals(-100F,TouchdownCard.offset(1,0,100,true),0F);
        assertEquals(100F,TouchdownCard.offset(2,0,100,true),0F);
        assertEquals(-100F,TouchdownCard.offset(3,0,100,false),0F);
        assertEquals(100F,TouchdownCard.offset(4,0,100,false),0F);
        for(int style=0;style<=4;style++)assertEquals(0F,TouchdownCard.offset(style,1,100,true),0F);
        assertEquals(0F,TouchdownCard.ease(-1),0F);
        assertEquals(.5F,TouchdownCard.ease(.5F),0.0001F);
        assertEquals(1F,TouchdownCard.ease(2),0F);
    }
    @Test public void animationSettingMigratesAndResets(){
        int old=UtilitySettings.touchdownCardAnimation;
        try{
            com.google.gson.JsonObject settings=new com.google.gson.JsonObject();
            settings.addProperty("touchdown_card_entrance",3);
            UtilitySettings.read(settings);
            assertEquals(3,UtilitySettings.touchdownCardAnimation);
            settings.addProperty("touchdown_card_animation",2);
            UtilitySettings.read(settings);
            assertEquals(2,UtilitySettings.snapshot().get("touchdown_card_animation").getAsInt());
            UtilitySettings.restore("touchdown_card_animation");
            assertEquals(2,UtilitySettings.touchdownCardAnimation);
        }finally{UtilitySettings.touchdownCardAnimation=old;}
    }
}
