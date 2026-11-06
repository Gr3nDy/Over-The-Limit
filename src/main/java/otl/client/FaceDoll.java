package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;

/**
 * Draws the menu doll. Unlike GuiInventory.drawEntityOnScreen this keeps the
 * model fully lit and resets the colour first, so it never inherits the yellow
 * tint left behind by the title text and never goes dark at night.
 */
final class FaceDoll {
    static boolean hat=true;
    /** Set while the menu doll renders so the face layer skips lobby/server gates. */
    static boolean preview=false;
    private FaceDoll() {}

    static void head(int x, int y, int size, AbstractClientPlayer player) {
        if(player==null||size<=0)return;
        Minecraft mc=MinecraftAccess.get();
        RenderPlayer renderer=(RenderPlayer)mc.getRenderManager().<AbstractClientPlayer>getEntityRenderObject(player);
        ModelPlayer model=renderer.getMainModel();
        ModelRenderer head=model.bipedHead,wear=model.bipedHeadwear;
        float[] old={head.rotateAngleX,head.rotateAngleY,head.rotateAngleZ,head.rotationPointX,head.rotationPointY,head.rotationPointZ,wear.rotateAngleX,wear.rotateAngleY,wear.rotateAngleZ,wear.rotationPointX,wear.rotationPointY,wear.rotationPointZ};
        boolean shown=head.showModel,wearShown=wear.showModel,lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        float lx=OpenGlHelper.lastBrightnessX,ly=OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x,y,100);float scale=size*1.25F;
            GlStateManager.scale(scale,scale,-scale);GlStateManager.rotate(20,0,1,0);GlStateManager.rotate(-8,1,0,0);GlStateManager.translate(0,.25,0);
            head.rotateAngleX=head.rotateAngleY=head.rotateAngleZ=0;head.setRotationPoint(0,0,0);head.showModel=true;
            wear.rotateAngleX=wear.rotateAngleY=wear.rotateAngleZ=0;wear.setRotationPoint(0,0,0);wear.showModel=true;
            RenderHelper.enableStandardItemLighting();GlStateManager.enableRescaleNormal();GlStateManager.color(1,1,1,1);
            mc.entityRenderer.enableLightmap();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            mc.getTextureManager().bindTexture(player.getLocationSkin());head.render(1F/16);
            if(hat)wear.render(1F/16);
            preview=true;
            try{new FaceOverlay(renderer).doRenderLayer(player,0,0,1,player.ticksExisted,0,0,1F/16);}finally{preview=false;}
        } finally {
            head.rotateAngleX=old[0];head.rotateAngleY=old[1];head.rotateAngleZ=old[2];head.setRotationPoint(old[3],old[4],old[5]);head.showModel=shown;
            wear.rotateAngleX=old[6];wear.rotateAngleY=old[7];wear.rotateAngleZ=old[8];wear.setRotationPoint(old[9],old[10],old[11]);wear.showModel=wearShown;
            RenderHelper.disableStandardItemLighting();if(lighting)GlStateManager.enableLighting();GlStateManager.disableRescaleNormal();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lx,ly);GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
        }
    }

}
