package otl.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.item.Item;

/** A thrown snowball becomes a football whose long axis follows the throw and spins in flight. */
final class RenderFootballSnowball extends RenderSnowball<EntitySnowball> {
    private static final float SCALE=0.62F;
    RenderFootballSnowball(RenderManager manager,Item item,RenderItem renderItem){super(manager,item,renderItem);}
    @Override public void doRender(EntitySnowball entity,double x,double y,double z,float yaw,float partialTicks){
        if(!Touchdown.active()){
            super.doRender(entity,x,y,z,yaw,partialTicks);
            return;
        }
        double mx=entity.motionX,my=entity.motionY,mz=entity.motionZ;
        double speed=Math.sqrt(mx*mx+my*my+mz*mz);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        if(speed>1.0E-4){
            double ax=mz,az=-mx,length=Math.sqrt(ax*ax+az*az);
            double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,my/speed))));
            if(length>1.0E-6)GlStateManager.rotate((float)angle,(float)(ax/length),0F,(float)(az/length));
            else GlStateManager.rotate(my<0?180F:0F,1F,0F,0F);
        }
        GlStateManager.rotate((float)(((entity.ticksExisted+partialTicks)*38)%360),0F,1F,0F);
        GlStateManager.scale(SCALE,SCALE,SCALE);
        FootballMesh.draw();
        GlStateManager.popMatrix();
    }
}
