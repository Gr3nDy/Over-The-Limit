package otl.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Draws the football wherever the snowball item would have been; every other item goes to the old renderer. */
final class FootballItemRenderer extends TileEntityItemStackRenderer {
    /**
     * The mesh is one unit tall - a whole block - so anything at or above 1.0 draws a ball bigger
     * than the block-sized space the item is given, in the hand and in the inventory slot alike.
     * 0.7 reads as a ball being carried rather than a beach ball.
     */
    static final float SCALE=0.7F;
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
