package otl.client;

import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class BowOptimizerTest {
    @Test public void onlyDurabilityUpdatesToTheSameBowSkipTheEquipDip(){
        Item bow=new ItemBow();ItemStack before=new ItemStack(bow),after=new ItemStack(bow);
        before.setItemDamage(10);after.setItemDamage(11);
        assertTrue(BowOptimizer.sameBow(before,after,2,2));
        assertFalse(BowOptimizer.sameBow(before,after,2,3));
        assertFalse(BowOptimizer.sameBow(before,new ItemStack(new Item()),2,2));
        assertFalse(BowOptimizer.sameBow(before,new ItemStack(new ItemBow()),2,2));
        assertFalse(BowOptimizer.sameBow(null,after,2,2));assertFalse(BowOptimizer.sameBow(before,null,2,2));
        after.stackSize=0;assertFalse(BowOptimizer.sameBow(before,after,2,2));after.stackSize=1;
        NBTTagCompound changed=new NBTTagCompound();changed.setString("name","Different bow");after.setTagCompound(changed);
        assertFalse(BowOptimizer.sameBow(before,after,2,2));
        assertEquals(10,before.getItemDamage());assertEquals(11,after.getItemDamage());
    }
    @Test public void eachShotSuppressesOnlyOneMatchingEcho(){
        BowOptimizer.Echoes echoes=new BowOptimizer.Echoes();
        echoes.add(2.31,64,4.31,1000000000L);
        assertFalse(echoes.consume(4,64,4,1100000000L,false));
        assertTrue(echoes.consume(2.25,64,4.25,1200000000L,false));
        assertFalse(echoes.consume(2.25,64,4.25,1300000000L,false));
    }
    @Test public void rapidShotsHaveSeparateEchoesAndUncertainSoundsAreNotMuted(){
        BowOptimizer.Echoes echoes=new BowOptimizer.Echoes();
        echoes.add(0,64,0,1000000000L);echoes.add(1,64,0,1100000000L);
        assertTrue(echoes.consume(0,64,0,1200000000L,false));
        assertFalse(echoes.consume(1,64,0,1300000000L,true));
        assertFalse(echoes.consume(1,64,0,1400000000L,false));
        echoes.add(0,64,0,2000000000L);
        assertFalse(echoes.consume(0,64,0,3500000001L,false));
        echoes.add(0,64,0,4000000000L);echoes.clear();
        assertFalse(echoes.consume(0,64,0,4100000000L,false));
    }
    @Test public void echoHistoryIsBounded(){
        BowOptimizer.Echoes echoes=new BowOptimizer.Echoes();
        for(int i=0;i<17;i++)echoes.add(i,64,0,1000000000L+i);
        assertFalse(echoes.consume(0,64,0,1200000000L,false));
        assertTrue(echoes.consume(16,64,0,1200000000L,false));
    }
    @Test public void moduleTogglesAndReadsItsSavedPreference() throws Exception {
        boolean previous=UtilitySettings.bowOptimizer;
        boolean overlay=UtilitySettings.heightLimitOverlay;
        try{
            OtlModule module=OtlModule.ALL.stream().filter(m->m.id.equals("bow_optimizer")).findFirst().get();
            assertEquals("Bow Optimizer",module.name);
            assertTrue(module.settings(null) instanceof BowOptimizerSettings);
            module.setEnabled(true);assertTrue(module.enabled());
            module.setEnabled(false);assertFalse(module.enabled());
            com.google.gson.JsonObject settings=new com.google.gson.JsonObject();settings.addProperty("bow_optimizer",true);
            java.lang.reflect.Method read=UtilitySettings.class.getDeclaredMethod("read",com.google.gson.JsonObject.class);
            read.setAccessible(true);read.invoke(null,settings);assertTrue(module.enabled());
        }finally{UtilitySettings.bowOptimizer=previous;UtilitySettings.heightLimitOverlay=overlay;}
    }
}
