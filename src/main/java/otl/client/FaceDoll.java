package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.gui.ScaledResolution;
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
        Minecraft mc=Minecraft.getMinecraft();
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

    static void draw(int x, int y, int scale, float mouseX, float mouseY, AbstractClientPlayer player) {
        if (player == null || scale <= 0) return;
        Minecraft client = Minecraft.getMinecraft();
        RenderManager manager = client.getRenderManager();
        float yawOffset = player.renderYawOffset;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float prevHead = player.prevRotationYawHead;
        float head = player.rotationYawHead;
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float)x, (float)y, 50.0F);
            GlStateManager.scale((float)(-scale), (float)scale, (float)scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
            player.renderYawOffset = 0;
            player.rotationYaw = 0;
            player.rotationPitch = 0;
            player.rotationYawHead = player.rotationYaw;
            player.prevRotationYawHead = player.rotationYaw;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            manager.setPlayerViewY(180.0F);
            manager.setRenderShadow(false);
            manager.doRenderEntity(player, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
        } finally {
            manager.setRenderShadow(true);
            player.renderYawOffset = yawOffset;
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.prevRotationYawHead = prevHead;
            player.rotationYawHead = head;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
