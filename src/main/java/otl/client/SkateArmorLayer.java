package otl.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Keeps vanilla armor materials and slots, but follows the visible rider's joints. */
final class SkateArmorLayer extends LayerBipedArmor {
    private final RenderPlayer player;
    private final LayerBipedArmor vanilla;
    private final boolean primary;
    private final Bent leggings=new Bent(0.5f),armor=new Bent(1f);

    SkateArmorLayer(RenderPlayer player,LayerBipedArmor vanilla,boolean primary){
        super(player);
        this.player=player;
        this.vanilla=vanilla;
        this.primary=primary;
    }

    @Override public void doRenderLayer(EntityLivingBase entity,float swing,float amount,float partial,float age,
        float yaw,float pitch,float scale){
        ModelBase model=player.getMainModel();
        if(!(model instanceof AnimatedModelPlayer)||!((AnimatedModelPlayer)model).skatePose){
            vanilla.doRenderLayer(entity,swing,amount,partial,age,yaw,pitch,scale);
        }else if(primary){
            super.doRenderLayer(entity,swing,amount,partial,age,yaw,pitch,scale);
        }
    }

    @Override protected ModelBiped getArmorModelHook(EntityLivingBase entity,ItemStack stack,int slot,ModelBiped model){
        ModelBiped base=super.getArmorModelHook(entity,stack,slot,model);
        // Modded armor can provide its own mesh and transforms; don't replace it.
        if(base!=model||!(player.getMainModel() instanceof AnimatedModelPlayer))return base;
        AnimatedModelPlayer rider=(AnimatedModelPlayer)player.getMainModel();
        if(!rider.skatePose)return base;
        Bent bent=slot==2?leggings:armor;
        bent.rider=rider;
        // Forge copies the rider's attributes before calling this hook, onto the original model.
        bent.setModelAttributes(rider);
        return bent;
    }

    private static final class Bent extends ModelBiped {
        private final SkateLimb armR,armL,legR,legL;
        private AnimatedModelPlayer rider;

        Bent(float inflate){
            super(inflate);
            armR=AnimatedModelPlayer.split(this,bipedRightArm);
            armL=AnimatedModelPlayer.split(this,bipedLeftArm);
            legR=AnimatedModelPlayer.split(this,bipedRightLeg);
            legL=AnimatedModelPlayer.split(this,bipedLeftLeg);
        }

        @Override public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,Entity entity){
            copy(rider.bipedHead,bipedHead);
            copy(rider.bipedHeadwear,bipedHeadwear);
            copy(rider.bipedBody,bipedBody);
            copy(rider.bipedRightArm,bipedRightArm);
            copy(rider.bipedLeftArm,bipedLeftArm);
            copy(rider.bipedRightLeg,bipedRightLeg);
            copy(rider.bipedLeftLeg,bipedLeftLeg);
            copy(rider.lowerArmR,armR);
            copy(rider.lowerArmL,armL);
            copy(rider.lowerLegR,legR);
            copy(rider.lowerLegL,legL);
            legR.reachScale=rider.lowerLegR.reachScale;
        }

        private static void copy(ModelRenderer from,ModelRenderer to){
            ModelBase.copyModelAngles(from,to);
            to.offsetX=from.offsetX;to.offsetY=from.offsetY;to.offsetZ=from.offsetZ;
        }
    }
}
