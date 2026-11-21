package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;

/** Hides only the local skater's held item in third person; inventory is never changed. */
final class SkateHeldItemLayer implements LayerRenderer<EntityLivingBase>{
    private final LayerHeldItem vanilla;
    SkateHeldItemLayer(RendererLivingEntity<?> renderer){vanilla=new LayerHeldItem(renderer);}
    public void doRenderLayer(EntityLivingBase e,float a,float b,float c,float d,float f,float g,float s){
        Minecraft mc=Minecraft.getMinecraft();
        if(e==mc.thePlayer&&PlayerAnim.skating()&&mc.gameSettings.thirdPersonView!=0)return;
        vanilla.doRenderLayer(e,a,b,c,d,f,g,s);
    }
    public boolean shouldCombineTextures(){return vanilla.shouldCombineTextures();}
}
