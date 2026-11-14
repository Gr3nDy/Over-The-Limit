package otl.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Draws the football wherever the snowball item would have been; every other item goes to the old renderer. */
final class FootballItemRenderer extends TileEntityItemStackRenderer {
    private static final float SCALE=1.25F;
    private final TileEntityItemStackRenderer delegate;
    FootballItemRenderer(TileEntityItemStackRenderer delegate){this.delegate=delegate;}
    @Override public void renderByItem(ItemStack stack){
        if(Touchdown.active()&&stack!=null&&stack.getItem()==Items.snowball){
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.5F,0.5F,0.5F);
            GlStateManager.scale(SCALE,SCALE,SCALE);
            FootballMesh.draw();
            GlStateManager.popMatrix();
            return;
        }
        delegate.renderByItem(stack);
    }
}
