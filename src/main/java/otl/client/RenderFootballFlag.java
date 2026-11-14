package otl.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

/**
 * PGM's touchdown flag floats at the post as a dropped banner item. On a Touchdown map that one
 * entity becomes the football, keeping the flag's own hover and spin; every other dropped item,
 * and any banner on a head, in a hand or in a slot, still renders vanilla.
 */
final class RenderFootballFlag extends RenderEntityItem {
    RenderFootballFlag(RenderManager manager,RenderItem item){super(manager,item);}
    @Override public void doRender(EntityItem entity,double x,double y,double z,float yaw,float partialTicks){
        ItemStack stack=entity==null?null:entity.getEntityItem();
        if(!Touchdown.active()||stack==null||stack.getItem()!=Items.banner){
            super.doRender(entity,x,y,z,yaw,partialTicks);
            return;
        }
        // The flag's own numbers: hover from RenderEntityItem, ground lift for a block-ish item.
        float age=(float)entity.getAge()+partialTicks;
        float bob=MathHelper.sin(age/10.0F+entity.hoverStart)*0.1F+0.1F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y+bob+0.125F,z);
        GlStateManager.rotate((age/20.0F+entity.hoverStart)*(180F/(float)Math.PI),0F,1F,0F);
        GlStateManager.scale(0.62F,0.62F,0.62F);
        FootballMesh.draw();
        GlStateManager.popMatrix();
    }
}
