package otl.client;

import net.minecraft.block.BlockBanner;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntityBanner;

/**
 * Replaces flag banners with a floating football that bobs and spins. PGM places a flag from a
 * block face, so it is a wall banner on some posts and a standing one on others - both count.
 */
final class FootballBannerRenderer extends TileEntitySpecialRenderer<TileEntityBanner> {
    private static final float SCALE=1.2F;
    private final TileEntitySpecialRenderer<TileEntityBanner> delegate;
    FootballBannerRenderer(TileEntitySpecialRenderer<TileEntityBanner> delegate){this.delegate=delegate;}
    @Override public void renderTileEntityAt(TileEntityBanner banner,double x,double y,double z,float partialTicks,int destroyStage){
        // A banner item has no world, and getBlockType() would dereference it.
        if(!Touchdown.active()||banner.getWorld()==null||!(banner.getBlockType() instanceof BlockBanner)){
            delegate.renderTileEntityAt(banner,x,y,z,partialTicks,destroyStage);
            return;
        }
        double time=banner.getWorld().getTotalWorldTime()+partialTicks;
        double phase=banner.getPos().getX()*0.7+banner.getPos().getZ()*1.3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x+0.5,y+1.15+0.14*Math.sin(time*0.105+phase),z+0.5);
        GlStateManager.rotate((float)((time*2.4+phase*25)%360),0F,1F,0F);
        GlStateManager.scale(SCALE,SCALE,SCALE);
        FootballMesh.draw();
        GlStateManager.popMatrix();
    }
}
