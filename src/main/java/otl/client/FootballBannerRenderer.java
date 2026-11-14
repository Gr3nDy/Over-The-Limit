package otl.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityBanner;

/** Replaces standing flag banners with a floating football that bobs and spins; wall banners keep vanilla. */
final class FootballBannerRenderer extends TileEntitySpecialRenderer<TileEntityBanner> {
    private static final float SCALE=1.2F;
    private final TileEntitySpecialRenderer<TileEntityBanner> delegate;
    FootballBannerRenderer(TileEntitySpecialRenderer<TileEntityBanner> delegate){this.delegate=delegate;}
    @Override public void renderTileEntityAt(TileEntityBanner banner,double x,double y,double z,float partialTicks,int destroyStage){
        if(!Touchdown.active()||banner.getBlockType()!=Blocks.standing_banner){
            delegate.renderTileEntityAt(banner,x,y,z,partialTicks,destroyStage);
            return;
        }
        double time=(banner.getWorld()==null?0:banner.getWorld().getTotalWorldTime())+partialTicks;
        double phase=banner.getPos().getX()*0.7+banner.getPos().getZ()*1.3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x+0.5,y+1.15+0.14*Math.sin(time*0.105+phase),z+0.5);
        GlStateManager.rotate((float)((time*2.4+phase*25)%360),0F,1F,0F);
        GlStateManager.scale(SCALE,SCALE,SCALE);
        FootballMesh.draw();
        GlStateManager.popMatrix();
    }
}
