package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Checks added after the smoke driver ran out of room: OtlSmoke's own class file is at the JVM's
 * constant-pool limit, so a new check gets its own file rather than one more method there.
 */
final class OtlSmokeChecks {
    private final OtlSmoke smoke;
    OtlSmokeChecks(OtlSmoke smoke){this.smoke=smoke;}

    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private ItemStack[] skateArmor;
    private int armorFrame=-1,armorFrames;
    private boolean fallShot,landShot;
    private net.minecraft.util.ResourceLocation skateSkin;
    private static final String[] GEAR={"layers","leather","chain","iron","gold","diamond","head-block","sneak","boot-push"};

    void skateArmorFrame(Minecraft mc,int t,net.minecraftforge.fml.common.gameevent.TickEvent.Phase phase){
        if(mc.thePlayer==null)return;
        ItemStack[] armor=mc.thePlayer.inventory.armorInventory;
        if(phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START){
            if(skateSkin!=null){
                net.minecraft.client.network.NetworkPlayerInfo info=mc.thePlayer.sendQueue.getPlayerInfo(mc.thePlayer.getUniqueID());
                ReflectionHelper.setPrivateValue(net.minecraft.client.network.NetworkPlayerInfo.class,info,
                    skateSkin,"locationSkin","field_178865_e");
                mc.thePlayer.getDataWatcher().updateObject(10,(byte)127);
            }
            if(t<174)return;
            int done=174+14*GEAR.length;
            boolean flight=t>=done&&t<done+35;
            int frame=flight?GEAR.length-2:(t-174)/14;
            if(!flight&&(frame>=GEAR.length||(armorFrames&(1<<frame))!=0))return;
            boolean sneak=frame==GEAR.length-2;
            if(!flight&&sneak&&(!mc.thePlayer.isSneaking()||(t-174)%14<6))return;
            armorFrame=flight?-1:frame;
            skateArmor=armor.clone();
            Item[][] gear={
                {null,null,null,null},
                {Items.leather_boots,Items.leather_leggings,Items.leather_chestplate,Items.leather_helmet},
                {Items.chainmail_boots,Items.chainmail_leggings,Items.chainmail_chestplate,Items.chainmail_helmet},
                {Items.iron_boots,Items.iron_leggings,Items.iron_chestplate,Items.iron_helmet},
                {Items.golden_boots,Items.golden_leggings,Items.golden_chestplate,Items.golden_helmet},
                {Items.diamond_boots,Items.diamond_leggings,Items.diamond_chestplate,Items.diamond_helmet},
                {null,null,null,Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin)},
                {Items.leather_boots,Items.chainmail_leggings,Items.leather_chestplate,Items.leather_helmet},
                {Items.leather_boots,Items.chainmail_leggings,Items.leather_chestplate,Items.leather_helmet}
            };
            for(int slot=0;slot<armor.length;slot++){
                Item item=gear[frame][slot];
                armor[slot]=item==null?null:new ItemStack(item);
                if(frame==1)((net.minecraft.item.ItemArmor)item).setColor(armor[slot],0xA926C5);
                if(frame>=GEAR.length-2&&slot!=1)((net.minecraft.item.ItemArmor)item).setColor(armor[slot],0xBB2020);
            }
        }else if(skateArmor!=null){
            try{
                if(armorFrame>=0){
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,
                        "otl-skate-"+GEAR[armorFrame]+".png",
                        mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    armorFrames|=1<<armorFrame;
                }
            }finally{
                System.arraycopy(skateArmor,0,armor,0,armor.length);
                skateArmor=null;
            }
        }
    }

    boolean skateShots(Minecraft mc,int t) throws ReflectiveOperationException,java.io.IOException {
        net.minecraft.client.entity.EntityPlayerSP p=mc.thePlayer;
        if(t==0){
            mc.setIngameFocus();
            mc.displayGuiScreen(null);
            UtilitySettings.load();
            UtilitySettings.playerAnimation=true;
            p.rotationPitch=p.prevRotationPitch=12f;
            if(!PlayerAnim.localWantsBoard())PlayerAnim.skateKey(mc);
            require(PlayerAnim.localWantsBoard(),"Could not deploy the board: screen="+mc.currentScreen
                +", riding="+p.isRiding()+", health="+p.getHealth()+", enabled="+PlayerAnim.enabled());
            java.awt.image.BufferedImage skin;
            try(java.io.InputStream in=mc.getResourceManager().getResource(
                new net.minecraft.util.ResourceLocation("textures/entity/steve.png")).getInputStream()){
                skin=javax.imageio.ImageIO.read(in);
            }
            java.awt.Graphics2D g=skin.createGraphics();
            g.setColor(new java.awt.Color(0xEB6954));g.fillRect(0,32,16,16);g.fillRect(0,48,16,16);
            g.setColor(new java.awt.Color(0xFFE044));g.fillRect(40,32,16,16);g.fillRect(48,48,16,16);
            g.setColor(new java.awt.Color(0x42CFEE));g.fillRect(16,32,24,16);
            g.setColor(new java.awt.Color(0x8AD35D));g.fillRect(40,0,16,4);
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(40,44,16,4);g.fillRect(48,60,16,4);
            g.dispose();
            net.minecraft.client.network.NetworkPlayerInfo info=p.sendQueue.getPlayerInfo(p.getUniqueID());
            skateSkin=mc.getTextureManager().getDynamicTextureLocation("otl-skate-test",
                new net.minecraft.client.renderer.texture.DynamicTexture(skin));
            ReflectionHelper.setPrivateValue(net.minecraft.client.network.NetworkPlayerInfo.class,info,
                "default","skinType","field_178863_g");
            p.getDataWatcher().updateObject(10,(byte)127);
        }
        if(t==27||t==48||t==69){
            java.util.Map<Integer,Object> states=ReflectionHelper.getPrivateValue(PlayerAnim.class,null,"STATES");
            Object state=states.get(p.getEntityId());
            for(String name:new String[]{"ollieAt","trick"}){
                java.lang.reflect.Field field=state.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(state,"trick".equals(name)?(t-27)/21:p.ticksExisted);
            }
            p.motionY=0.42;
        }
        if(t==94){
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
        }
        if(t==106)net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
        if(t>=110&&t<174){
            float value=new float[]{0.12f,0.22f,0.32f,0.42f,0.52f,0.62f,0.78f,1f}[(t-110)/8];
            java.util.Map<Integer,Object> states=ReflectionHelper.getPrivateValue(PlayerAnim.class,null,"STATES");
            Object state=states.get(p.getEntityId());
            for(String name:new String[]{"board","prevBoard"}){
                java.lang.reflect.Field field=state.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setFloat(state,value);
            }
        }
        if(t<=24&&t%2==0||t>=28&&t<=82&&t%2==0||t>=96&&t<=106&&t%2==0
            ||t>=110&&t<174&&(t-110)%8==7){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,String.format("otl-skate-%03d.png",t),
                mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        }
        p.renderYawOffset=p.prevRenderYawOffset=p.rotationYaw+(t<27||t>=110&&t<174?-45f:65f);
        int done=174+14*GEAR.length;
        if(t==done-30)net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);
        if(t==done-14)net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        if(t>=done-14&&t<=done){
            java.util.Map<Integer,Object> states=ReflectionHelper.getPrivateValue(PlayerAnim.class,null,"STATES");
            Object state=states.get(p.getEntityId());
            java.lang.reflect.Field pushAt=state.getClass().getDeclaredField("pushAt");
            pushAt.setAccessible(true);
            pushAt.setInt(state,t==done?-1:p.ticksExisted-5);
        }
        if(t==done){
            require(armorFrames==(1<<GEAR.length)-1,"Missing skate gear captures: "+armorFrames);
            require(PlayerAnim.localWantsBoard(),"The board was not out before the drop");
            p.capabilities.isFlying=false;
            p.setPosition(p.posX,p.posY+2.6,p.posZ);
            p.motionY=0;
            p.onGround=false;
            p.fallDistance=0;
        }
        int stowAt=done+35;
        if(t>done&&t<stowAt&&(!fallShot||!landShot)){
            java.util.Map<Integer,Object> states=ReflectionHelper.getPrivateValue(PlayerAnim.class,null,"STATES");
            Object state=states.get(p.getEntityId());
            java.lang.reflect.Field fall=state.getClass().getDeclaredField("fall");
            java.lang.reflect.Field land=state.getClass().getDeclaredField("landAt");
            fall.setAccessible(true);land.setAccessible(true);
            int landed=land.getInt(state);
            if(!fallShot&&!p.onGround&&fall.getFloat(state)>0.25f){
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-skate-fall.png",
                    mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                fallShot=true;
            }else if(fallShot&&!landShot&&landed>=0&&p.ticksExisted-landed>=2&&p.ticksExisted-landed<=8){
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-skate-land.png",
                    mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                landShot=true;
            }
        }
        if(t==stowAt){
            require(fallShot&&landShot,"Missing skate captures: fall="+fallShot+", landing="+landShot
                +", grounded="+p.onGround+", board="+PlayerAnim.localWantsBoard());
            PlayerAnim.skateKey(mc);
            require(!PlayerAnim.localWantsBoard()&&PlayerAnim.posing(p),"Stowing snapped directly to vanilla");
        }
        if(t>stowAt&&t<=stowAt+23&&(t-stowAt)%4==0)
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,String.format("otl-skate-stow-%02d.png",t-stowAt),
                mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        if(t==stowAt+26)require(!PlayerAnim.posing(p),"The rider never returned to the vanilla model after stowing");
        return t==stowAt+26;
    }

    /**
     * The animated model is installed by reflection, so a wrong mapping would only show up as a
     * logged warning and a game that looks unchanged. Assert the swap really landed.
     */
    void animatedModel(Minecraft mc){
        railContact(mc);
        int labelWidth=mc.fontRendererObj.getStringWidth("Online \u00b7 999");
        require(ModulesScreen.serviceLeft(260,labelWidth)+10+labelWidth<=252,
            "The status label overlapped the search control");
        java.util.Map<String,net.minecraft.client.renderer.entity.RenderPlayer> skin=mc.getRenderManager().getSkinMap();
        require(skin.size()>=2,"Skin map lost a player renderer");
        for(net.minecraft.client.renderer.entity.RenderPlayer renderer:skin.values()){
            require(renderer.getMainModel() instanceof net.minecraft.client.model.ModelPlayer,
                "Player renderer has no player model");
        }
        net.minecraft.client.entity.EntityPlayerSP p=mc.thePlayer;
        java.util.Map<Integer,Object> states=ReflectionHelper.getPrivateValue(PlayerAnim.class,null,"STATES");
        Object previous=states.remove(p.getEntityId());
        boolean enabled=UtilitySettings.playerAnimation;
        try{
            UtilitySettings.playerAnimation=true;
            java.lang.reflect.Constructor<?> ctor=Class.forName("otl.client.PlayerAnim$State").getDeclaredConstructor();
            ctor.setAccessible(true);
            Object state=ctor.newInstance();
            states.put(p.getEntityId(),state);
            for(String name:new String[]{"board","prevBoard"}){
                java.lang.reflect.Field field=state.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setFloat(state,1f);
            }
            net.minecraft.util.AxisAlignedBB hitbox=p.getEntityBoundingBox();
            for(net.minecraft.client.renderer.entity.RenderPlayer renderer:skin.values()){
                net.minecraft.client.model.ModelPlayer vanilla=renderer.getMainModel();
                byte oldWear=p.getDataWatcher().getWatchableObjectByte(10);
                int oldMask=SkateSkin.mask(p);
                boolean cape=p.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.CAPE);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.client.event.RenderPlayerEvent.Pre(p,renderer,0.5f,0,-100,0));
                try{
                    require(renderer.getMainModel() instanceof AnimatedModelPlayer,"Skater did not get a split-limb rig");
                    AnimatedModelPlayer model=(AnimatedModelPlayer)renderer.getMainModel();
                    model.bipedRightArmwear.showModel=true;
                    model.setRotationAngles(0f,0f,p.ticksExisted+0.5f,20f,5f,1f/16f,p);
                    require(!model.bipedRightArmwear.cubeList.isEmpty()&&model.bipedRightArmwear.isHidden,
                        "The sleeve layer was not safely hidden");
                    require(!model.bipedLeftLegwear.cubeList.isEmpty()&&model.bipedLeftLegwear.isHidden,
                        "The baked left pant leg was not safely hidden");
                    require(model.bipedLeftLeg.cubeList.isEmpty()&&model.bipedLeftLeg.childModels.size()==1
                        &&model.bipedLeftLeg.childModels.get(0)==model.lowerLegL,"The rider drew two left-leg meshes");
                    require(SkateSkin.mask(p)==oldMask,"Baking lost the original skin toggles");
                    require(p.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.RIGHT_SLEEVE)
                        ==(((oldWear&255)&net.minecraft.entity.player.EnumPlayerModelParts.RIGHT_SLEEVE.getPartMask())!=0)
                        &&!p.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.LEFT_PANTS_LEG)
                        &&p.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.CAPE)==cape,
                        "Only the duplicated pants layers should be masked");
                    require(model.lowerLegR.rotateAngleX>0.2f&&model.bipedHead.rotateAngleY>0.3f,
                        "Skate stance did not bend its knee or preserve head aim");
                    require(model.bipedHead.offsetY==0f&&model.bipedHead.rotationPointY<0f
                        &&model.bipedHeadwear.rotationPointX==model.bipedHead.rotationPointX
                        &&model.bipedHeadwear.rotationPointY==model.bipedHead.rotationPointY
                        &&model.bipedHeadwear.rotationPointZ==model.bipedHead.rotationPointZ,
                        "The animated head, outer skin and attachment pivot did not move together");
                    require(model.bipedLeftLegwear.rotationPointZ==model.bipedLeftLeg.rotationPointZ,
                        "Skate stance left its wear pivot behind");
                    require(model.frontHipAttached(),"The skate thigh started below the waist");
                    require(model.bipedLeftLeg.rotationPointZ!=model.bipedRightLeg.rotationPointZ,
                        "The hips did not follow the torso turn");
                    if(p.getCurrentArmor(0)==null)require(model.footDeckError(p)<0.65,
                        "Both standing feet must sit on the deck");
                    if(p.getCurrentArmor(0)==null){
                        net.minecraft.util.Vec3 front=model.frontFoot();
                        float knee=model.lowerLegL.rotateAngleX;
                        float bodyRoll=model.bipedBody.rotateAngleZ,bodyY=model.bipedBody.rotationPointY;
                        java.lang.reflect.Field pushAt=state.getClass().getDeclaredField("pushAt");
                        pushAt.setAccessible(true);
                        pushAt.setInt(state,p.ticksExisted-5);
                        model.setRotationAngles(0f,0f,p.ticksExisted+0.5f,20f,5f,1f/16f,p);
                        require(model.pushFootOnGround(p),"The push foot did not reach beside the deck and onto the ground");
                        net.minecraft.util.Vec3 planted=model.frontFoot();
                        require(Math.abs(front.xCoord-planted.xCoord)<0.001&&Math.abs(front.zCoord-planted.zCoord)<0.001,
                            "The left boot slid across the deck");
                        require(model.lowerLegL.rotateAngleX>knee&&model.bipedBody.rotateAngleZ<bodyRoll-0.10f
                            &&model.bipedBody.rotationPointY>bodyY+0.10f,
                            "The pushing rider did not lean right and dip over the planted boot");
                        armorFollows(renderer,model,p);
                        pushAt.setInt(state,-1);
                    }
                    for(String phase:new String[]{"fall","grind"}){
                        java.lang.reflect.Field now=state.getClass().getDeclaredField(phase);
                        java.lang.reflect.Field prev=state.getClass().getDeclaredField("prev"+Character.toUpperCase(phase.charAt(0))+phase.substring(1));
                        now.setAccessible(true);prev.setAccessible(true);
                        now.setFloat(state,1f);prev.setFloat(state,1f);
                        model.setRotationAngles(0f,0f,p.ticksExisted+0.5f,20f,5f,1f/16f,p);
                        armorFollows(renderer,model,p);
                        now.setFloat(state,0f);prev.setFloat(state,0f);
                    }
                }finally{
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.minecraftforge.client.event.RenderPlayerEvent.Post(p,renderer,0.5f,0,-100,0));
                }
                require(renderer.getMainModel()==vanilla,"Skate renderer did not restore the shared model");
                require(p.getDataWatcher().getWatchableObjectByte(10)==oldWear,
                    "The skate renderer did not restore the original skin settings");
            }
            require(p.getEntityBoundingBox()==hitbox,"Skate visuals changed the player hitbox");
        }catch(ReflectiveOperationException e){throw new AssertionError("Could not simulate a skate pose",e);}
        finally{
            if(previous==null)states.remove(p.getEntityId());else states.put(p.getEntityId(),previous);
            UtilitySettings.playerAnimation=enabled;
        }
    }

    private void armorFollows(net.minecraft.client.renderer.entity.RenderPlayer renderer,AnimatedModelPlayer rider,
        net.minecraft.client.entity.EntityPlayerSP p){
        java.util.List<?> layers=ReflectionHelper.getPrivateValue(
            net.minecraft.client.renderer.entity.RendererLivingEntity.class,renderer,"layerRenderers","field_177097_h");
        SkateArmorLayer layer=(SkateArmorLayer)layers.stream().filter(SkateArmorLayer.class::isInstance).findFirst().orElse(null);
        require(layer!=null,"Animated armor layer was not installed");
        Item[] gear={Items.leather_boots,Items.chainmail_leggings,Items.iron_chestplate,Items.diamond_helmet};
        for(int slot=1;slot<=gear.length;slot++){
            net.minecraft.client.model.ModelBiped rest=layer.getArmorModel(slot);
            net.minecraft.client.model.ModelBiped posed=layer.getArmorModelHook(p,new ItemStack(gear[slot-1]),slot,rest);
            require(rest!=posed,"Armor slot "+slot+" bypassed the skate rig");
            posed.setRotationAngles(0f,0f,p.ticksExisted+0.5f,20f,5f,1f/16f,p);
            require(posed.bipedHead.rotationPointY==rider.bipedHead.rotationPointY
                &&posed.bipedRightArm.rotationPointZ==rider.bipedRightArm.rotationPointZ
                &&posed.bipedLeftLeg.rotationPointY==rider.bipedLeftLeg.rotationPointY,
                "Armor slot "+slot+" did not follow the rider's pivots");
            SkateLimb shin=(SkateLimb)posed.bipedRightLeg.childModels.get(0);
            require(shin.rotateAngleX==rider.lowerLegR.rotateAngleX&&shin.reachScale==rider.lowerLegR.reachScale,
                "Armor slot "+slot+" did not follow the moving leg");
            SkateLimb left=(SkateLimb)posed.bipedLeftLeg.childModels.get(0);
            require(posed.bipedLeftLeg.childModels.size()==1&&left.rotateAngleX==rider.lowerLegL.rotateAngleX
                &&left.rotateAngleZ==rider.lowerLegL.rotateAngleZ,"Armor drew a different left knee than the rider");
        }
    }

    private void railContact(Minecraft mc){
        net.minecraft.client.entity.EntityPlayerSP p=mc.thePlayer;
        net.minecraft.util.BlockPos pos=new net.minecraft.util.BlockPos(p.posX,p.posY+2,p.posZ);
        net.minecraft.block.state.IBlockState original=mc.theWorld.getBlockState(pos);
        double x=p.posX,y=p.posY,z=p.posZ,prevX=p.prevPosX,prevZ=p.prevPosZ;
        boolean grounded=p.onGround;
        try{
            for(net.minecraft.block.Block rail:new net.minecraft.block.Block[]{net.minecraft.init.Blocks.iron_bars,
                net.minecraft.init.Blocks.oak_fence,net.minecraft.init.Blocks.nether_brick_fence}){
                mc.theWorld.setBlockState(pos,rail.getDefaultState(),2);
                double top=rail==net.minecraft.init.Blocks.iron_bars?1:1.5;
                p.setPosition(pos.getX()+0.5,pos.getY()+top,pos.getZ()+0.5);
                p.onGround=true;p.prevPosX=p.posX-0.10;p.prevPosZ=p.posZ;
                require(PlayerAnim.onRail(p),"Did not detect a moving rider on "+rail);
                p.prevPosX=p.posX;
                require(!PlayerAnim.onRail(p),"A stationary rider was grinding");
                p.prevPosX=p.posX-0.1;p.onGround=false;
                require(!PlayerAnim.onRail(p),"An airborne rider was grinding");
            }
        }finally{
            mc.theWorld.setBlockState(pos,original,2);
            p.setPosition(x,y,z);
            p.prevPosX=prevX;p.prevPosZ=prevZ;p.onGround=grounded;
        }
    }

    /**
     * The sidebar toggle: it hides the vanilla sidebar only while the bar is up, and the mod puts
     * the sidebar back itself, so turning the option off can never leave the screen empty.
     */
    void scoreboardToggle(Minecraft mc){
        UtilitySettings.teamBar=true;
        UtilitySettings.teamBarHideScoreboard=false;
        require(!TeamBar.hidesScoreboard(mc),"The sidebar was hidden with its option off");
        UtilitySettings.teamBarHideScoreboard=true;
        require(TeamBar.hidesScoreboard(mc),"The sidebar stayed visible with its option on");
        // The real render flag is driven from the top of the frame, before vanilla draws the sidebar.
        net.minecraftforge.client.GuiIngameForge.renderObjective=true;
        net.minecraftforge.client.event.RenderGameOverlayEvent frame=
            new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre all=
            new net.minecraftforge.client.event.RenderGameOverlayEvent.Pre(frame,
                net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL);
        TeamBar.INSTANCE.scoreboard(all);
        require(!net.minecraftforge.client.GuiIngameForge.renderObjective,"The sidebar was still drawn with its option on");
        UtilitySettings.teamBarHideScoreboard=false;
        TeamBar.INSTANCE.scoreboard(all);
        require(net.minecraftforge.client.GuiIngameForge.renderObjective,"The sidebar did not come back when the option was turned off");
        UtilitySettings.teamBar=false;
        require(!TeamBar.hidesScoreboard(mc),"The sidebar was hidden with Team Bar disabled");
        UtilitySettings.teamBar=true;
    }

    /** The pencil opens Edit Profile; removal there asks before deleting. */
    void profileDeleteDialog(Minecraft mc,ModulesScreen menu){
        String home=UtilitySettings.profile,doomed="zz delete me";
        try{UtilitySettings.write(Profiles.file(doomed),UtilitySettings.DEFAULTS);}
        catch(Exception e){throw new AssertionError("Could not create the test profile: "+e.getMessage());}
        menu.initGui();
        java.util.List<String> rail=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"profiles");
        int index=rail.indexOf(doomed);
        require(index>=0,"The test profile never reached the rail");
        int x=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"x");
        int y=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"y");
        int h=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"h");
        int rows=Math.max(1,(h-113)/19);
        // The rail shows only the first rows; scroll the new profile into view before clicking it.
        int scroll=Math.max(0,Math.min(index,rail.size()-rows));
        ReflectionHelper.setPrivateValue(ModulesScreen.class,menu,scroll,"profileScroll");
        int row=index-scroll;
        require(row>=0&&row<rows,"The test profile could not be scrolled into the rail's view");
        try{menu.mouseClicked(x+82,y+60+row*19+5,0);}
        catch(java.io.IOException e){throw new AssertionError("Rail click failed: "+e.getMessage());}
        require(mc.currentScreen instanceof ProfileEditScreen,"Pencil did not open Edit Profile");
        ProfileEditScreen editor=(ProfileEditScreen)mc.currentScreen;
        editor.actionPerformed(new net.minecraft.client.gui.GuiButton(2,0,0,"Remove"));
        require(mc.currentScreen instanceof net.minecraft.client.gui.GuiYesNo,"Deleting a profile did not ask first");
        net.minecraft.client.gui.GuiYesNo dialog=(net.minecraft.client.gui.GuiYesNo)mc.currentScreen;
        net.minecraft.client.gui.GuiYesNoCallback callback=
            ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiYesNo.class,dialog,"parentScreen");
        callback.confirmClicked(true,0);
        require(mc.currentScreen instanceof ModulesScreen,"The delete confirmation stayed on screen after Yes");
        require(!Profiles.profileNames().contains(doomed),"The profile was not deleted");
        require(home.equals(UtilitySettings.profile),"Deleting a profile changed the active profile");
    }

    /**
     * A filtered tab must be the only chat on screen. The All tab is the client's own chat object,
     * so if that object still holds its lines the client paints them behind the filtered tab and
     * the chat reads as doubled.
     */
    void chatDoubling(Minecraft mc,int state){
        if(state==155){
            UtilitySettings.chat=true;UtilitySettings.showTabChat=true;
            ReliableChat.session().select("team");
            // Cancelling the Chat event also skips Forge's post(CHAT), where the Team Bar is drawn,
            // so a filtered tab must never cancel it: blank the client's lines instead. The tab is
            // still empty here, so this posts the handler without drawing anything.
            net.minecraftforge.client.event.RenderGameOverlayEvent frame=
                new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
            net.minecraftforge.client.event.RenderGameOverlayEvent.Chat chat=
                new net.minecraftforge.client.event.RenderGameOverlayEvent.Chat(frame,0,mc.currentScreen==null?0:mc.currentScreen.height-48);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(chat);
            require(!chat.isCanceled(),"A filtered tab cancelled the Chat event, which also hides the Team Bar");
            smoke.line("Created by MapMaker");
            smoke.line("\u00a79(Blue Team) Alex: team hello");
            smoke.line("Bob: global hello");
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
            require(mc.currentScreen instanceof ReliableChatScreen,"The chat screen was not replaced");
            ((ReliableChatScreen)mc.currentScreen).select("team");
            require(ReliableChat.session().selected.equals("team"),"The Team tab was not selected");
            require(ReliableChat.session().views.get("team").totalLines()>0,"The team line never reached the Team tab");
        }else if(state==156){
            // A screenshot taken in the same tick as a change captures the previous frame, so the
            // blanked chat is only visible one tick later.
            require(clientLines(mc).isEmpty(),"The client's own chat still paints All behind the Team tab");
        }else if(state==157){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-team-open.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            require(clientLines(mc).isEmpty(),"The client's own chat repopulated behind the Team tab");
            ((ReliableChatScreen)mc.currentScreen).select("all");
        }else if(state==158){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-all-open.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            require(!clientLines(mc).isEmpty(),"Switching back to All did not restore the client's own chat");
        }
    }

    /**
     * A death line must still reach the kill feed while a filtered chat tab is on screen. The feed
     * draws on the frame's own chat stage, which an earlier chat fix skipped entirely.
     */
    void killFeedWithFilteredTab(Minecraft mc,int state){
        if(state==159){
            UtilitySettings.chat=true;UtilitySettings.killFeed=true;
            UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.ALL;UtilitySettings.showTabChat=true;
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
            ((ReliableChatScreen)mc.currentScreen).select("team");
            require(ReliableChat.session().selected.equals("team"),"The Team tab was not selected");
            KillFeed.history.clear();
            smoke.line("\u00a7cAlex\u00a7r was slain by \u00a7cSteve\u00a7r's Iron Sword");
        }else if(state==160){
            require(!KillFeed.history.entries.isEmpty(),"A death line never reached the kill feed with the Team tab on");
            KillFeedRules.Death death=KillFeed.history.entries.get(0);
            require("Alex".equals(death.victim)&&"Steve".equals(death.killer),"The death was read wrong: "+death.victim+" / "+death.killer);
            // The feed draws from the frame's chat stage: post the real event and check it is not cancelled.
            net.minecraftforge.client.event.RenderGameOverlayEvent frame=
                new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
            net.minecraftforge.client.event.RenderGameOverlayEvent.Chat chat=
                new net.minecraftforge.client.event.RenderGameOverlayEvent.Chat(frame,0,mc.currentScreen.height-48);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(chat);
            require(!chat.isCanceled(),"The chat stage was cancelled, so the kill feed could not draw");
        }
    }
    private java.util.List<?> clientLines(Minecraft mc){
        return ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiNewChat.class,mc.ingameGUI.getChatGUI(),"drawnChatLines","field_146253_i");
    }

    /** Stands in for the mod that was cancelling the chat stage on the user's client. */
    static final class ChatCanceller{
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void cancel(net.minecraftforge.client.event.RenderGameOverlayEvent.Chat event){event.setCanceled(true);}
    }
    private ChatCanceller canceller;
    private long fallbacksBefore;

    /**
     * Another mod cancelling the chat stage skips post(CHAT), and every HUD widget used to draw
     * there: the bar and the feed kept running with nothing on screen. The end of the frame has to
     * take over, so this registers a canceller and checks the HUD keeps drawing.
     */
    void hudSurvivesACancelledChatStage(Minecraft mc,int state){
        if(state==161){
            UtilitySettings.chat=true;UtilitySettings.killFeed=true;
            UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.ALL;
            if(mc.currentScreen!=null)mc.displayGuiScreen(null);
            KillFeed.history.clear();
            smoke.line("\u00a7cAlex\u00a7r was slain by \u00a7cSteve\u00a7r's Iron Sword");
            fallbacksBefore=HudStage.fallbacks;
            canceller=new ChatCanceller();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(canceller);
        }else if(state==162){
            require(HudStage.fallbacks>fallbacksBefore,"The HUD stopped drawing when the chat stage was cancelled");
            require(!KillFeed.history.entries.isEmpty(),"The death line never reached the feed");
        }else if(state==163){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-chat-cancelled.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(canceller);canceller=null;
        }
    }

    /**
     * The end-of-match sound, through Forge's own event object and the real handlers. On a client
     * whose chat PGM translates, this sound is the only end signal left, so it is checked with the
     * English lines out of the picture: no chat line is sent here, only the sound.
     */
    void matchEndSound(){
        // The event only reads the sound's own name, so it needs no live sound manager.
        net.minecraft.client.Minecraft mc=MinecraftAccess.get();
        // PGM plays its end sound at the player's own position, not at a mob.
        net.minecraftforge.client.event.sound.PlaySoundEvent event=
            new net.minecraftforge.client.event.sound.PlaySoundEvent(null,
                net.minecraft.client.audio.PositionedSoundRecord.create(
                    new net.minecraft.util.ResourceLocation("minecraft","mob.wither.death"),
                    (float)mc.thePlayer.posX,(float)mc.thePlayer.posY,(float)mc.thePlayer.posZ),
                net.minecraft.client.audio.SoundCategory.MASTER);
        // On 1.8 the client names the sound without a namespace; modern servers send it prefixed.
        require(MatchEnd.endedBySound(event),"The event did not carry the wither sound: "+event.name);
        require(!(boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The bar was already stopped before the sound");
        UtilitySettings.autoGgDelay=0;UtilitySettings.autoGgRandom=false;
        smoke.line("The match has started!");
        ReflectionHelper.setPrivateValue(AutoGg.class,AutoGg.INSTANCE,
            System.nanoTime()-10_000_000_000L,"startedAt");
        AutoGg.INSTANCE.sound(event);
        TeamBar.INSTANCE.sound(event);
        require((boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The end-of-match sound did not stop the bar's clock");
        require(!ReflectionHelper.getPrivateValue(AutoGg.class,AutoGg.INSTANCE,"sendAt").equals(0L),
            "The end-of-match sound did not arm a gg");
        // A second copy of the sound must not say gg twice.
        AutoGg.INSTANCE.sound(event);
        int armed=AutoGg.INSTANCE.sentCount;
        smoke.line("The match has started!");
        require(ReflectionHelper.getPrivateValue(AutoGg.class,AutoGg.INSTANCE,"sendAt").equals(0L),
            "The next match did not cancel the gg the sound armed");
        require(AutoGg.INSTANCE.sentCount==armed,"The sound said gg more than once for one match");
        require(!(boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The next match did not restart the bar's clock");
    }
}
