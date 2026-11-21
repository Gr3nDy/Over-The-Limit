package otl.client;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** The vanilla player model with the OTL pose layer and a skater deck under the feet. */
final class AnimatedModelPlayer extends ModelPlayer {
    private final ModelRenderer board;
    boolean boardOut;
    float boardX,boardY,boardZ,boardDrop;

    AnimatedModelPlayer(boolean slim){
        super(0f,slim);
        board=new ModelRenderer(this);
        // Box coordinates are relative to the rotation point, which sits at the soles (y=24);
        // +y runs down in model space, so the deck hangs just under the feet.
        board.addBox(-5f,0f,-9f,10,1,18);
        board.setRotationPoint(0f,24f,0f);
    }

    @Override
    public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,Entity entity){
        super.setRotationAngles(swing,amount,age,yaw,pitch,scale,entity);
        if(entity instanceof EntityPlayer&&PlayerAnim.enabled())
            PlayerAnim.apply(this,(EntityPlayer)entity,swing,amount,age);
        else boardOut=false;
    }

    @Override
    public void render(Entity entity,float swing,float amount,float age,float yaw,float pitch,float scale){
        super.render(entity,swing,amount,age,yaw,pitch,scale);
        if(boardOut&&entity instanceof EntityPlayer&&!entity.isInvisible())drawBoard(scale);
    }

    private void drawBoard(float scale){
        board.rotateAngleX=boardX;
        board.rotateAngleY=boardY;
        board.rotateAngleZ=boardZ;
        GlStateManager.pushMatrix();
        try{
            // The model is already flipped, so +y here moves the deck down: it rises into place.
            GlStateManager.translate(0f,boardDrop,0f);
            GlStateManager.disableTexture2D();
            GlStateManager.color(0.30f,0.33f,0.38f,1f);
            board.render(scale);
        }finally{
            GlStateManager.color(1f,1f,1f,1f);
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }
}
