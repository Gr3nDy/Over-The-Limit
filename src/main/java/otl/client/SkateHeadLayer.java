package otl.client;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;

/** Blocks and skulls in the helmet slot are drawn separately from armor. */
final class SkateHeadLayer implements LayerRenderer<EntityLivingBase> {
    private final RenderPlayer renderer;
    private final AnimatedModelPlayer model;
    private final LayerCustomHead vanilla,animated;

    SkateHeadLayer(RenderPlayer renderer,AnimatedModelPlayer model,LayerCustomHead vanilla){
        this.renderer=renderer;
        this.model=model;
        this.vanilla=vanilla;
        animated=new LayerCustomHead(model.bipedHead);
    }

    @Override public void doRenderLayer(EntityLivingBase entity,float swing,float amount,float partial,
        float age,float yaw,float pitch,float scale){
        if(renderer.getMainModel()!=model){
            vanilla.doRenderLayer(entity,swing,amount,partial,age,yaw,pitch,scale);
            return;
        }
        ModelRenderer head=model.bipedHead;
        GlStateManager.pushMatrix();
        try{
            // LayerCustomHead calls postRender(), which applies pivots but not the rider's offsets.
            GlStateManager.translate(head.offsetX,head.offsetY,head.offsetZ);
            animated.doRenderLayer(entity,swing,amount,partial,age,yaw,pitch,scale);
        }finally{
            GlStateManager.popMatrix();
        }
    }

    @Override public boolean shouldCombineTextures(){return vanilla.shouldCombineTextures();}
}
