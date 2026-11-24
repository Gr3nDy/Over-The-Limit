package otl.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.util.ResourceLocation;

/**
 * The vanilla player model with continuous bends at the elbows and knees.
 */
final class AnimatedModelPlayer extends ModelPlayer {
    final SkateLimb lowerArmR,lowerArmL,lowerLegR,lowerLegL;
    private final ModelRenderer[] poseParts;
    private final Vec3[] defaultPivots;
    boolean skatePose;
    private float planted,push;
    float elbowRight,elbowLeft,kneeRight,kneeLeft;
    float elbowRightZ,elbowLeftZ,kneeRightZ,kneeLeftZ;

    AnimatedModelPlayer(boolean slim){
        super(0f,slim);
        lowerArmR=new SkateLimb(this,bipedRightArm);
        lowerArmL=new SkateLimb(this,bipedLeftArm);
        lowerLegR=new SkateLimb(this,bipedRightLeg);
        lowerLegL=new SkateLimb(this,bipedLeftLeg);
        poseParts=new ModelRenderer[]{bipedHead,bipedHeadwear,bipedBody,bipedBodyWear,
            bipedRightArm,bipedRightArmwear,bipedLeftArm,bipedLeftArmwear,
            bipedRightLeg,bipedRightLegwear,bipedLeftLeg,bipedLeftLegwear};
        defaultPivots=new Vec3[poseParts.length];
        for(int i=0;i<poseParts.length;i++)defaultPivots[i]=pivot(poseParts[i]);
        // Keep vanilla's visibility flags, but never draw a second surface over the base skin.
        for(ModelRenderer shell:new ModelRenderer[]{bipedHeadwear,bipedBodyWear,bipedRightArmwear,bipedLeftArmwear,
            bipedRightLegwear,bipedLeftLegwear})shell.cubeList.clear();
        // ModelPlayer resets showModel, so hard-hide only the duplicated pants shells.
        bipedRightLegwear.isHidden=bipedLeftLegwear.isHidden=true;
    }

    @Override public void render(Entity entity,float swing,float amount,float age,float yaw,float pitch,float scale){
        if(!(entity instanceof AbstractClientPlayer)){
            super.render(entity,swing,amount,age,yaw,pitch,scale);
            return;
        }
        TextureManager textures=MinecraftAccess.get().getTextureManager();
        ResourceLocation original=((AbstractClientPlayer)entity).getLocationSkin();
        try{
            textures.bindTexture(SkateSkin.get(textures,original,SkateSkin.mask((EntityPlayer)entity)));
            super.render(entity,swing,amount,age,yaw,pitch,scale);
        }finally{
            textures.bindTexture(original);
        }
    }

    static SkateLimb split(ModelBase model,ModelRenderer limb){
        return new SkateLimb(model,limb);
    }

    @Override
    public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,Entity entity){
        // Every angle starts at its vanilla default before the game poses the model. Vanilla does
        // not reset bipedHead.rotateAngleZ or bipedBody.rotateAngleX/Z, so anything that adds to
        // them - including a plain sway - piles up frame after frame and the model spins. Player
        // Animator restores its defaults the same way, then assigns absolute values.
        resetAngles();
        skatePose=false;
        planted=0f;push=-1f;
        super.setRotationAngles(swing,amount,age,yaw,pitch,scale,entity);
        // External renderers can recreate the old shells even when their vanilla geometry is empty.
        bipedRightLegwear.showModel=bipedLeftLegwear.showModel=false;
        elbowRight=elbowLeft=kneeRight=kneeLeft=0f;
        elbowRightZ=elbowLeftZ=kneeRightZ=kneeLeftZ=0f;
        if(entity instanceof EntityPlayer&&PlayerAnim.enabled())
            PlayerAnim.apply(this,(EntityPlayer)entity,swing,amount,age);
        bend(lowerArmR,elbowRight,elbowRightZ);
        bend(lowerArmL,elbowLeft,elbowLeftZ);
        bend(lowerLegR,kneeRight,kneeRightZ);
        bend(lowerLegL,kneeLeft,kneeLeftZ);
        lowerLegR.reachScale=1f;
        net.minecraft.item.ItemStack boots=entity instanceof EntityPlayer?((EntityPlayer)entity).getCurrentArmor(0):null;
        net.minecraft.item.ItemStack leggings=entity instanceof EntityPlayer?((EntityPlayer)entity).getCurrentArmor(1):null;
        lowerLegR.insideBoot=lowerLegL.insideBoot=skatePose&&boots!=null&&boots.getItem() instanceof net.minecraft.item.ItemArmor;
        lowerLegR.insideLeggings=lowerLegL.insideLeggings=skatePose&&leggings!=null
            &&leggings.getItem() instanceof net.minecraft.item.ItemArmor;
        if(planted>0f&&entity instanceof EntityPlayer)plantFeet((EntityPlayer)entity,lowerLegR.insideBoot?1f:0f);
    }

    private static void bend(ModelRenderer lower,float x,float z){
        lower.rotateAngleX=x;
        lower.rotateAngleZ=z;
    }

    void skateOffsets(float mount,float hop,float step,float planted,float push){
        skatePose=true;
        this.planted=planted;
        this.push=push;
        float lift=-0.17f*mount-hop;
        // Turn the chest around the waist so leaning never pulls it off the legs.
        Vec3 waist=new Vec3(0,12,0);
        Vec3 shift=waist.subtract(rotate(bipedBody,waist)).addVector(-16f*step,0,0);
        Vec3 rightHip=rotate(bipedBody,hip(bipedRightLeg,mount)).add(shift).subtract(pivot(bipedRightLeg));
        Vec3 leftHip=rotate(bipedBody,hip(bipedLeftLeg,mount)).add(shift).subtract(pivot(bipedLeftLeg));
        // Tilt the chest around the waist, not the hips around the chest.
        if(push>0f&&planted>0f){
            bipedBody.rotateAngleZ-=0.19f*AnimClips.pushContact(push)*planted;
            bipedBody.rotateAngleX+=0.09f*AnimClips.pushDip(push)*planted;
            shift=waist.subtract(rotate(bipedBody,waist)).addVector(-16f*step,0,0);
        }
        move(bipedBody,bipedBodyWear,shift,lift);
        attach(bipedRightArm,bipedRightArmwear,pivot(bipedRightArm),shift,lift);
        attach(bipedLeftArm,bipedLeftArmwear,pivot(bipedLeftArm),shift,lift);
        move(bipedRightLeg,bipedRightLegwear,rightHip,lift);
        move(bipedLeftLeg,bipedLeftLegwear,leftHip,lift);
        // Face overlays and head-slot items use postRender(), which only knows the head pivot.
        Vec3 headAnchor=pivot(bipedHead);
        Vec3 head=rotate(bipedBody,new Vec3(headAnchor.xCoord,headAnchor.yCoord*(1f-mount),headAnchor.zCoord)).add(shift);
        bipedHead.rotationPointX=bipedHeadwear.rotationPointX=(float)head.xCoord;
        bipedHead.rotationPointY=(float)head.yCoord+16f*lift;
        bipedHeadwear.rotationPointY=bipedHead.rotationPointY;
        bipedHead.rotationPointZ=bipedHeadwear.rotationPointZ=(float)head.zCoord;
        bipedHead.offsetY=bipedHeadwear.offsetY=0f;
    }

    private void plantFeet(EntityPlayer rider,float inflate){
        float pushing=AnimClips.pushContact(push);
        if(pushing>0f)shiftWeight(pushing*planted,AnimClips.pushDip(push)*planted);
        double support=sole(bipedLeftLeg,lowerLegL,inflate);
        float delta=(float)(deckPlane(rider)-support)*planted;
        for(ModelRenderer part:poseParts)part.rotationPointY+=delta;
        if(pushing>0f)pushOff(rider,inflate,pushing*planted);
    }

    private void shiftWeight(float amount,float dip){
        Vec3 anchor=frontFoot();
        // The knee moves inward; the boot keeps its heading and its spot on the board.
        float flex=0.28f*amount+0.16f*dip;
        bipedLeftLeg.rotateAngleX-=flex;
        bipedLeftLeg.rotateAngleZ+=0.12f*amount;
        lowerLegL.rotateAngleX+=flex;
        lowerLegL.rotateAngleZ-=0.10f*amount;
        Vec3 shift=anchor.subtract(frontFoot());
        for(ModelRenderer part:poseParts){
            part.rotationPointX+=(float)shift.xCoord;
            part.rotationPointZ+=(float)shift.zCoord;
        }
    }

    private void pushOff(EntityPlayer rider,float inflate,float weight){
        ModelRenderer hip=bipedRightLeg;
        SkateLimb shin=lowerLegR;
        float oldX=hip.rotateAngleX,oldY=hip.rotateAngleY,oldZ=hip.rotateAngleZ;
        float oldKnee=shin.rotateAngleX,oldKneeZ=shin.rotateAngleZ;
        double ground=deckPlane(rider)+PlayerAnim.DECK_TOP/0.9375*16;
        double x=-5.85,z=-2.2+5.4*AnimClips.smooth((push-0.30f)/0.38f);
        double y=ground-inflate;
        solvePushLeg(x,y,z);
        solvePushLeg(x,y+ground-sole(hip,shin,inflate),z);
        hip.rotateAngleX=oldX+(hip.rotateAngleX-oldX)*weight;
        hip.rotateAngleY=oldY*(1f-weight);
        hip.rotateAngleZ=oldZ+(hip.rotateAngleZ-oldZ)*weight;
        shin.rotateAngleX=oldKnee+(shin.rotateAngleX-oldKnee)*weight;
        shin.rotateAngleZ=oldKneeZ*(1f-weight);
        shin.reachScale=1f+(shin.reachScale-1f)*weight;
    }

    private void solvePushLeg(double x,double y,double z){
        ModelRenderer hip=bipedRightLeg;
        SkateLimb shin=lowerLegR;
        double dx=x-hip.rotationPointX,dy=y-hip.rotationPointY,dz=z-hip.rotationPointZ;
        double vertical=Math.sqrt(dx*dx+dy*dy);
        double desired=Math.sqrt(vertical*vertical+dz*dz);
        // shortcut: a small render-only reach compensates for the board height without dipping the planted leg.
        shin.reachScale=(float)Math.max(1,Math.min(1.18,desired/(shin.bottom-0.05)));
        double thigh=shin.rotationPointY*shin.reachScale,calf=shin.bottom*shin.reachScale-thigh;
        double reach=Math.min(thigh+calf-0.05,desired);
        // Use side lean to reach off the board, keeping the boot pointed along the swipe.
        hip.rotateAngleY=0f;
        hip.rotateAngleZ=(float)Math.atan2(-dx,dy);
        shin.rotateAngleZ=0f;
        hip.rotateAngleX=(float)(Math.atan2(dz,vertical)
            -Math.acos((reach*reach+thigh*thigh-calf*calf)/(2*reach*thigh)));
        shin.rotateAngleX=(float)Math.acos((reach*reach-thigh*thigh-calf*calf)/(2*thigh*calf));
    }

    Vec3 frontFoot(){
        return transform(bipedLeftLeg,transform(lowerLegL,new Vec3(0,lowerLegL.bottom-lowerLegL.rotationPointY,0)));
    }

    boolean frontHipAttached(){
        Vec3 waist=transform(bipedBody,new Vec3(0,12,0));
        return pivot(bipedLeftLeg).subtract(waist).dotProduct(rotate(bipedBody,new Vec3(0,1,0)))<0;
    }

    private static double deckPlane(EntityPlayer rider){
        boolean sneak=rider.isSneaking();
        double drop=sneak&&!(rider instanceof net.minecraft.client.entity.EntityPlayerSP)?0.125:0;
        return (1.5078125-(sneak?0.2:0)-(PlayerAnim.DECK_TOP+drop)/0.9375)*16;
    }

    double footDeckError(EntityPlayer rider){
        double deck=deckPlane(rider);
        return Math.max(Math.abs(sole(bipedLeftLeg,lowerLegL,0)-deck),
            Math.abs(sole(bipedRightLeg,lowerLegR,0)-deck));
    }

    boolean pushFootOnGround(EntityPlayer rider){
        SkateLimb shin=lowerLegR;
        double ground=deckPlane(rider)+PlayerAnim.DECK_TOP/0.9375*16;
        Vec3 foot=bootPoint(bipedRightLeg,shin,0,shin.bottom,0);
        return foot.xCoord<-5.0&&Math.abs(sole(bipedRightLeg,shin,0)-ground)<0.75
            &&Math.abs(sole(bipedLeftLeg,lowerLegL,0)-deckPlane(rider))<0.001;
    }

    private static double sole(ModelRenderer hip,SkateLimb shin,float inflate){
        double result=Double.NEGATIVE_INFINITY;
        for(int i=0;i<4;i++){
            double x=(i&1)==0?shin.minX-inflate:shin.maxX+inflate;
            double z=(i&2)==0?shin.minZ-inflate:shin.maxZ+inflate;
            result=Math.max(result,bootPoint(hip,shin,x,shin.bottom+inflate,z).yCoord);
        }
        return result;
    }

    private static Vec3 bootPoint(ModelRenderer hip,SkateLimb shin,double x,double y,double z){
        Vec3 foot=rotate(shin,new Vec3(x,(y-shin.rotationPointY)*shin.reachScale,z));
        return transform(hip,foot.addVector(0,shin.rotationPointY*shin.reachScale,0));
    }

    private static Vec3 hip(ModelRenderer leg,float mount){
        return new Vec3(leg.rotationPointX,leg.rotationPointY+(11.25f-leg.rotationPointY)*mount,
            leg.rotationPointZ*(1f-mount));
    }

    private void attach(ModelRenderer part,ModelRenderer wear,Vec3 anchor,Vec3 shift,float lift){
        move(part,wear,rotate(bipedBody,anchor).add(shift).subtract(pivot(part)),lift);
    }

    private static void move(ModelRenderer part,ModelRenderer wear,Vec3 delta,float lift){
        wear.rotationPointX=part.rotationPointX+=(float)delta.xCoord;
        wear.rotationPointY=part.rotationPointY+=16f*lift+(float)delta.yCoord;
        wear.rotationPointZ=part.rotationPointZ+=(float)delta.zCoord;
    }

    private static Vec3 pivot(ModelRenderer part){
        return new Vec3(part.rotationPointX,part.rotationPointY,part.rotationPointZ);
    }

    Vec3 skateHand(){
        Vec3 palm=transform(lowerArmR,new Vec3(0,5.3,0));
        Vec3 hand=transform(bipedRightArm,palm);
        // Same scale and inverted axes as RendererLivingEntity's player-model pass.
        double pixel=0.9375/16;
        return new Vec3(-hand.xCoord*pixel,(1.5078125-(isSneak?0.2:0))*0.9375-hand.yCoord*pixel,
            hand.zCoord*pixel);
    }

    private static Vec3 transform(ModelRenderer part,Vec3 point){
        return rotate(part,point).addVector(part.rotationPointX+16*part.offsetX,
            part.rotationPointY+16*part.offsetY,part.rotationPointZ+16*part.offsetZ);
    }

    private static Vec3 rotate(ModelRenderer part,Vec3 point){
        Vec3 v=point.rotatePitch(-part.rotateAngleX).rotateYaw(part.rotateAngleY);
        float c=(float)Math.cos(part.rotateAngleZ),s=(float)Math.sin(part.rotateAngleZ);
        return new Vec3(c*v.xCoord-s*v.yCoord,s*v.xCoord+c*v.yCoord,v.zCoord);
    }

    /** Vanilla defaults for the pose angles, so a stale frame can never leak into the next. */
    private void resetAngles(){
        for(int i=0;i<poseParts.length;i++){
            ModelRenderer r=poseParts[i];
            Vec3 rest=defaultPivots[i];
            r.setRotationPoint((float)rest.xCoord,(float)rest.yCoord,(float)rest.zCoord);
            r.rotateAngleX=0f;r.rotateAngleY=0f;r.rotateAngleZ=0f;
            r.offsetX=r.offsetY=r.offsetZ=0f;
        }
    }

    /**
     * The first-person hand renders the arm renderers directly, without going through
     * setRotationAngles, so it would inherit whatever the last third-person frame left behind -
     * including a bent elbow. Straighten the arm first so the held hand looks vanilla.
     */
    @Override
    public void renderRightArm(){
        straighten(bipedRightArm,bipedRightArmwear,lowerArmR);
        super.renderRightArm();
    }

    @Override
    public void renderLeftArm(){
        straighten(bipedLeftArm,bipedLeftArmwear,lowerArmL);
        super.renderLeftArm();
    }

    private static void straighten(ModelRenderer... parts){
        for(ModelRenderer r:parts){
            r.rotateAngleX=r.rotateAngleY=r.rotateAngleZ=0f;
            r.offsetX=r.offsetY=r.offsetZ=0f;
        }
    }
}
