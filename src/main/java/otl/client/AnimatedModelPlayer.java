package otl.client;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The vanilla player model with a single anatomical cut at each elbow and knee, plus a skateboard.
 *
 * <p>The split reuses the vanilla renderers instead of drawing replacements, because a child
 * renderer inherits the parent's transform: sneaking, a held item and the armour layer all keep
 * working, and the lower half follows the shoulder wherever the game puts it.
 */
final class AnimatedModelPlayer extends ModelPlayer {
    /** Skin atlas offsets, fixed by the skin format: the same for slim and classic. */
    private static final int ARM_R_U=40,ARM_R_V=16,ARM_L_U=32,ARM_L_V=48;
    private static final int LEG_R_U=0,LEG_R_V=16,LEG_L_U=16,LEG_L_V=48;
    private static final int ARM_RW_U=40,ARM_RW_V=32,ARM_LW_U=48,ARM_LW_V=48;
    private static final int LEG_RW_U=0,LEG_RW_V=32,LEG_LW_U=0,LEG_LW_V=48;

    final ModelRenderer lowerArmR,lowerArmL,lowerLegR,lowerLegL;
    final ModelRenderer lowerArmRw,lowerArmLw,lowerLegRw,lowerLegLw;
    private final ModelRenderer deck,trucks,wheels;

    boolean boardOut;
    float boardX,boardY,boardZ,boardDrop;
    float elbowRight,elbowLeft,kneeRight,kneeLeft;
    float elbowRightZ,elbowLeftZ,kneeRightZ,kneeLeftZ;

    AnimatedModelPlayer(boolean slim){
        super(0f,slim);
        lowerArmR=split(bipedRightArm,ARM_R_U,ARM_R_V,0f);
        lowerArmL=split(bipedLeftArm,ARM_L_U,ARM_L_V,0f);
        lowerLegR=split(bipedRightLeg,LEG_R_U,LEG_R_V,0f);
        lowerLegL=split(bipedLeftLeg,LEG_L_U,LEG_L_V,0f);
        lowerArmRw=split(bipedRightArmwear,ARM_RW_U,ARM_RW_V,0.25f);
        lowerArmLw=split(bipedLeftArmwear,ARM_LW_U,ARM_LW_V,0.25f);
        lowerLegRw=split(bipedRightLegwear,LEG_RW_U,LEG_RW_V,0.25f);
        lowerLegLw=split(bipedLeftLegwear,LEG_LW_U,LEG_LW_V,0.25f);

        deck=board(0);trucks=board(1);wheels=board(2);
    }

    /**
     * Cuts a limb once at its real joint. Hands and feet remain attached to the forearm/shin;
     * they are not separate artificial joints.
     *
     * <p>The geometry is read back from the box the vanilla model built, so slim arms and the
     * inflated outer layer both come out right.
     *
     * shortcut: the end faces of the lower pieces sample the wrong texels (only the side faces are
     * exact), which shows as a small patch on the palm and the sole; upgrade to a hand-built box
     * with per-face UVs if that ever becomes visible in play.
     */
    private ModelRenderer split(ModelRenderer limb,int u,int v,float inflate){
        ModelBox box=limb.cubeList.get(0);
        // posX1..posZ2 already include the inflation, so it is taken back out before the halves
        // are rebuilt with the same inflate; otherwise the outer layer grows twice.
        float x=box.posX1+inflate,y=box.posY1+inflate,z=box.posZ1+inflate;
        int w=Math.round(box.posX2-box.posX1-2f*inflate);
        int h=Math.round(box.posY2-box.posY1-2f*inflate);
        int d=Math.round(box.posZ2-box.posZ1-2f*inflate);
        int half=h/2;
        limb.cubeList.clear();
        limb.cubeList.add(new ModelBox(limb,u,v,x,y,z,w,half,d,inflate));
        // A bare renderer defaults to a 64x32 atlas; the player skin is 64x64, so copying the
        // parent's size is what keeps the lower halves sampling the right texels.
        ModelRenderer lower=new ModelRenderer(this);
        lower.textureWidth=limb.textureWidth;
        lower.textureHeight=limb.textureHeight;
        lower.cubeList.add(new ModelBox(lower,u,v+half,x,0f,z,w,half,d,inflate));
        // The joint sits half a limb down, so the child box starts exactly where the upper ends.
        lower.setRotationPoint(0f,y+half,0f);
        limb.addChild(lower);
        return lower;
    }

    /** 0 deck, 1 trucks, 2 wheels: flat-coloured groups hanging under the feet. */
    private ModelRenderer board(int part){
        ModelRenderer r=new ModelRenderer(this);
        // Model Y grows downward. The player's foot/ground plane is 24, and the wheel boxes end
        // at local Y=4, so anchor the assembly at 20: wheel bottoms land at 24 while the deck
        // remains visibly above the ground instead of sinking the deck into it.
        r.setRotationPoint(0f,20f,0f);
        if(part==0){
            r.addBox(-5f,0f,-11f,10,1,22);          // deck
            r.addBox(-5f,-1f,-12f,10,1,3);          // nose curl
            r.addBox(-5f,-1f,9f,10,1,3);            // tail curl
        }else if(part==1){
            r.addBox(-4f,1f,-7f,8,1,2);
            r.addBox(-4f,1f,5f,8,1,2);
        }else{
            r.addBox(-4f,2f,-8f,2,2,3);r.addBox(2f,2f,-8f,2,2,3);
            r.addBox(-4f,2f,5f,2,2,3);r.addBox(2f,2f,5f,2,2,3);
        }
        return r;
    }

    @Override
    public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,Entity entity){
        // Every angle starts at its vanilla default before the game poses the model. Vanilla does
        // not reset bipedHead.rotateAngleZ or bipedBody.rotateAngleX/Z, so anything that adds to
        // them - including a plain sway - piles up frame after frame and the model spins. Player
        // Animator restores its defaults the same way, then assigns absolute values.
        resetAngles();
        super.setRotationAngles(swing,amount,age,yaw,pitch,scale,entity);
        boardOut=false;
        elbowRight=elbowLeft=kneeRight=kneeLeft=0f;
        elbowRightZ=elbowLeftZ=kneeRightZ=kneeLeftZ=0f;
        if(entity instanceof EntityPlayer&&PlayerAnim.enabled())
            PlayerAnim.apply(this,(EntityPlayer)entity,swing,amount,age);
        bend(lowerArmR,lowerArmRw,elbowRight,elbowRightZ);
        bend(lowerArmL,lowerArmLw,elbowLeft,elbowLeftZ);
        bend(lowerLegR,lowerLegRw,kneeRight,kneeRightZ);
        bend(lowerLegL,lowerLegLw,kneeLeft,kneeLeftZ);
    }

    private static void bend(ModelRenderer lower,ModelRenderer lowerWear,float x,float z){
        lower.rotateAngleX=lowerWear.rotateAngleX=x;
        lower.rotateAngleZ=lowerWear.rotateAngleZ=z;
    }

    /** Vanilla defaults for the pose angles, so a stale frame can never leak into the next. */
    private void resetAngles(){
        for(ModelRenderer r:new ModelRenderer[]{bipedHead,bipedBody,bipedRightArm,bipedLeftArm,bipedRightLeg,bipedLeftLeg}){
            r.rotateAngleX=0f;r.rotateAngleY=0f;r.rotateAngleZ=0f;
        }
    }

    /**
     * The first-person hand renders the arm renderers directly, without going through
     * setRotationAngles, so it would inherit whatever the last third-person frame left behind -
     * including a bent elbow. Straighten the arm first so the held hand looks vanilla.
     */
    @Override
    public void renderRightArm(){
        straighten(bipedRightArm,bipedRightArmwear,lowerArmR,lowerArmRw);
        super.renderRightArm();
    }

    @Override
    public void renderLeftArm(){
        straighten(bipedLeftArm,bipedLeftArmwear,lowerArmL,lowerArmLw);
        super.renderLeftArm();
    }

    private static void straighten(ModelRenderer... parts){
        for(ModelRenderer r:parts)r.rotateAngleX=r.rotateAngleY=r.rotateAngleZ=0f;
    }

    @Override
    public void render(Entity entity,float swing,float amount,float age,float yaw,float pitch,float scale){
        super.render(entity,swing,amount,age,yaw,pitch,scale);
        if(boardOut&&!entity.isInvisible())drawBoard(scale);
    }

    private void drawBoard(float scale){
        deck.rotateAngleX=trucks.rotateAngleX=wheels.rotateAngleX=boardX;
        deck.rotateAngleY=trucks.rotateAngleY=wheels.rotateAngleY=boardY;
        deck.rotateAngleZ=trucks.rotateAngleZ=wheels.rotateAngleZ=boardZ;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(0f,boardDrop,0f);
            GlStateManager.disableTexture2D();
            // Flat colours: a painted deck, metal hardware, pale wheels.
            GlStateManager.color(0.20f,0.22f,0.26f,1f);deck.render(scale);
            GlStateManager.color(0.62f,0.65f,0.70f,1f);trucks.render(scale);
            GlStateManager.color(0.90f,0.90f,0.86f,1f);wheels.render(scale);
        }finally{
            GlStateManager.color(1f,1f,1f,1f);
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }
}
