package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;
import net.minecraft.item.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class LeftHandTest {
    @Test public void otherTypesUseTheOppositeHandInBothDirections(){
        ItemStack block=new ItemStack(new ItemBlock(new Block(Material.rock){})),item=new ItemStack(new Item());
        assertFalse(LeftHand.Type.BLOCKS.useLeft(false,block));assertTrue(LeftHand.Type.BLOCKS.useLeft(false,item));
        assertTrue(LeftHand.Type.BLOCKS.useLeft(true,block));assertFalse(LeftHand.Type.BLOCKS.useLeft(true,item));
        ItemStack sword=new ItemStack(new ItemSword(Item.ToolMaterial.IRON));
        ItemStack tool=new ItemStack(new ItemHoe(Item.ToolMaterial.IRON));
        for(LeftHand.Type type:LeftHand.Type.values())for(ItemStack stack:new ItemStack[]{block,item,sword,tool,null}){
            if(type.matches(stack)){
                assertTrue(type.useLeft(true,stack));assertFalse(type.useLeft(false,stack));
            }else{
                assertFalse(type.useLeft(true,stack));assertTrue(type.useLeft(false,stack));
            }
        }
    }
    @Test public void typesSelectTheRequestedItemCategories(){
        ItemStack food=new ItemStack(new Item()),block=new ItemStack(new ItemBlock(new Block(Material.rock){}));
        ItemStack sword=new ItemStack(new ItemSword(Item.ToolMaterial.IRON)),bow=new ItemStack(new ItemBow());
        ItemStack[] tools={new ItemStack(new ItemTool(1,Item.ToolMaterial.IRON,java.util.Collections.<Block>emptySet()){}),new ItemStack(new ItemHoe(Item.ToolMaterial.IRON)),new ItemStack(new ItemShears()),
                new ItemStack(new ItemFishingRod()),new ItemStack(new ItemFlintAndSteel())};
        assertTrue(LeftHand.Type.ITEMS.matches(food));assertFalse(LeftHand.Type.ITEMS.matches(block));
        assertTrue(LeftHand.Type.ITEMS.matches(sword));assertTrue(LeftHand.Type.ITEMS.matches(bow));
        assertTrue(LeftHand.Type.BLOCKS.matches(block));assertFalse(LeftHand.Type.BLOCKS.matches(food));assertFalse(LeftHand.Type.BLOCKS.matches(sword));
        for(ItemStack tool:tools){
            assertTrue(LeftHand.Type.TOOLS.matches(tool));assertTrue(LeftHand.Type.TOOLS_BLOCKS.matches(tool));
            assertTrue(LeftHand.Type.ITEMS.matches(tool));assertFalse(LeftHand.Type.WEAPONS.matches(tool));
        }
        assertTrue(LeftHand.Type.TOOLS_BLOCKS.matches(block));assertFalse(LeftHand.Type.TOOLS.matches(block));
        assertFalse(LeftHand.Type.TOOLS.matches(sword));assertFalse(LeftHand.Type.TOOLS_BLOCKS.matches(sword));
        assertTrue(LeftHand.Type.WEAPONS.matches(sword));assertTrue(LeftHand.Type.WEAPONS.matches(bow));
        assertFalse(LeftHand.Type.WEAPONS.matches(food));assertFalse(LeftHand.Type.WEAPONS.matches(block));
        for(LeftHand.Type type:LeftHand.Type.values())assertEquals(type==LeftHand.Type.ALL,type.matches(null));
        for(ItemStack item:new ItemStack[]{food,block,sword,bow})assertTrue(LeftHand.Type.ALL.matches(item));
    }
    @Test public void typeCyclesAllOptionsAndUnknownSavedValuesFallBackToAll(){
        java.util.Set<LeftHand.Type> seen=new java.util.HashSet<>();LeftHand.Type type=LeftHand.Type.ALL;
        for(int i=0;i<6;i++){seen.add(type);assertEquals(type,LeftHand.Type.parse(type.name()));type=type.next();}
        assertEquals(6,seen.size());assertEquals(LeftHand.Type.ALL,type);assertEquals(LeftHand.Type.ALL,LeftHand.Type.parse("invalid"));
    }
    @Test public void mirrorsOnlyEnabledFirstPersonOnApprovedServers(){
        assertTrue(LeftHand.shouldMirror(true,true,0,false));
        assertFalse(LeftHand.shouldMirror(false,true,0,false));
        assertFalse(LeftHand.shouldMirror(true,false,0,false));
        assertFalse(LeftHand.shouldMirror(true,true,1,false));
        assertFalse(LeftHand.shouldMirror(true,true,2,false));
        assertFalse(LeftHand.shouldMirror(true,true,0,true));
    }
    @Test public void moduleIsRegisteredAndTogglesItsPreference(){
        boolean before=UtilitySettings.leftHand;
        try{
            OtlModule module=OtlModule.ALL.stream().filter(m->m.id.equals("left_hand")).findFirst().get();
            assertEquals("Prefered Hand",module.name);assertTrue(module.settings(null) instanceof PreferedHandSettings);
            module.setEnabled(true);assertTrue(module.enabled());
            module.setEnabled(false);assertFalse(module.enabled());
        }finally{UtilitySettings.leftHand=before;}
    }
}
