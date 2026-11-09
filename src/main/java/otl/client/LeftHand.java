package otl.client;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** Mirror only the first-person hand pass, after EntityRenderer resets its matrices. */
final class LeftHand {
    static final LeftHand INSTANCE=new LeftHand();
    private EntityRenderer failedRenderer;

    enum Type {
        ITEMS("Items Only"), BLOCKS("Blocks Only"), TOOLS("Tools Only"), TOOLS_BLOCKS("Tools + Blocks Only"), WEAPONS("Weapons"), ALL("ALL");
        final String label;
        Type(String label){this.label=label;}
        Type next(){return values()[(ordinal()+1)%values().length];}
        boolean useLeft(boolean mainHandLeft,ItemStack stack){return matches(stack)?mainHandLeft:!mainHandLeft;}
        static Type parse(String value){try{return valueOf(value);}catch(IllegalArgumentException ex){return ALL;}}
        boolean matches(ItemStack stack){
            if(this==ALL)return true;
            if(stack==null)return false;
            Item item=stack.getItem();boolean block=item instanceof ItemBlock;
            boolean tool=item instanceof ItemTool||item instanceof ItemHoe||item instanceof ItemShears||item instanceof ItemFishingRod||item instanceof ItemFlintAndSteel;
            switch(this){
                case ITEMS:return !block;
                case BLOCKS:return block;
                case TOOLS:return tool;
                case TOOLS_BLOCKS:return tool||block;
                case WEAPONS:return item instanceof ItemSword||item instanceof ItemBow;
                default:return false;
            }
        }
    }

    static boolean shouldMirror(boolean enabled,boolean approved,int perspective,boolean map){
        return enabled&&approved&&perspective==0&&!map;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(UtilitySettings.leftHand||UtilitySettings.bowOptimizer&&UtilitySettings.bowOptimization)||!ServerScope.active())return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.entityRenderer==null||mc.entityRenderer==failedRenderer||mc.entityRenderer.itemRenderer instanceof Items)return;
        ItemRenderer original=mc.entityRenderer.itemRenderer;
        try{
            // Lunar remaps field names; identify the hand renderer by type and identity instead.
            for(Field field:EntityRenderer.class.getDeclaredFields())if(ItemRenderer.class.isAssignableFrom(field.getType())){
                field.setAccessible(true);
                if(field.get(mc.entityRenderer)!=original)continue;
                field.set(mc.entityRenderer,new Items(mc,original));return;
            }
            throw new IllegalStateException("Hand renderer field not found");
        }catch(Exception ex){failedRenderer=mc.entityRenderer;System.err.println("OTL: first-person renderer could not attach: "+ex.getMessage());}
    }

    static void mirrored(Runnable render){
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),front=GL11.glGetInteger(GL11.GL_FRONT_FACE);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.scale(-1f,1f,1f);
        GlStateManager.matrixMode(mode);
        GL11.glFrontFace(front==GL11.GL_CW?GL11.GL_CCW:GL11.GL_CW);
        try{render.run();}
        finally{
            GL11.glFrontFace(front);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(mode);
        }
    }

    static final class Items extends ItemRenderer {
        private final Minecraft mc;
        private final ItemRenderer original;
        Items(Minecraft mc,ItemRenderer original){super(mc);this.mc=mc;this.original=original;}
        @Override public void renderItemInFirstPerson(float partial){
            ItemStack held=mc.thePlayer==null?null:mc.thePlayer.getHeldItem();
            if(shouldMirror(UtilitySettings.leftHand&&UtilitySettings.handType.useLeft(UtilitySettings.mainHandLeft,held),ServerScope.active(),mc.gameSettings.thirdPersonView,held!=null&&held.getItem() instanceof ItemMap))
                mirrored(()->original.renderItemInFirstPerson(partial));
            else original.renderItemInFirstPerson(partial);
        }
        // Keep the original renderer's equip state, custom animations and overlays intact.
        @Override public void updateEquippedItem(){
            if(BowOptimizer.active()&&mc.thePlayer!=null&&!mc.thePlayer.isDead&&mc.gameSettings.thirdPersonView==0)
                BowOptimizer.stabilize(original,mc.thePlayer.getHeldItem(),mc.thePlayer.inventory.currentItem);
            original.updateEquippedItem();
        }
        @Override public void resetEquippedProgress(){original.resetEquippedProgress();}
        @Override public void resetEquippedProgress2(){original.resetEquippedProgress2();}
        @Override public void renderOverlays(float partial){original.renderOverlays(partial);}
        @Override public void renderItem(EntityLivingBase entity,ItemStack stack,ItemCameraTransforms.TransformType transform){original.renderItem(entity,stack,transform);}
    }
}
