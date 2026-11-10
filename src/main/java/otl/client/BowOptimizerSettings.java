package otl.client;

import net.minecraft.client.gui.*;

public final class BowOptimizerSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,page;
    public BowOptimizerSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-(page==1?256:230))/2;buttonList.clear();
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"bow_optimizer"));
        buttonList.add(new NeutralButton(0,x+14,y+40,w-28,20,"Bow Optimizer: "+(UtilitySettings.bowOptimizer?"ON":"OFF"),false));
        int tabWidth=(w-32)/2;
        buttonList.add(new NeutralButton(20,x+14,y+64,tabWidth,18,"General",page==0));
        buttonList.add(new NeutralButton(21,x+18+tabWidth,y+64,tabWidth,18,"Bow Accuracy",page==1));
        if(page==0){
            BedrockUi.section(fontRendererObj,"FEEDBACK",x+14,y+88,w-28);
            buttonList.add(new NeutralButton(1,x+14,y+104,w-28,20,"Optimization: "+(UtilitySettings.bowOptimization?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(8,x+14,y+128,tabWidth,20,"Bow Hit Sound: "+(UtilitySettings.bowHitSound?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(14,x+18+tabWidth,y+128,tabWidth,20,"Bow-Kill Sound: "+(UtilitySettings.bowKillSound?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(9,x+14,y+152,w-28,20,"Hit Marker: "+(UtilitySettings.bowHitMarker?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(10,x+14,y+176,w-28,"Hit marker size: ","%",PresetSlider.HIT_SIZE,UtilitySettings.bowHitMarkerSize,100,s->UtilitySettings.bowHitMarkerSize=s.getValueInt()));
        }else{
            BedrockUi.section(fontRendererObj,"ACCURACY READOUT",x+14,y+90,w-28);
            buttonList.add(new NeutralButton(2,x+14,y+104,w-28,20,"Live Bow Accuracy: "+(UtilitySettings.bowAccuracy?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(3,x+14,y+128,tabWidth,"Background: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyOpacity,25,s->UtilitySettings.bowAccuracyOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(5,x+18+tabWidth,y+128,tabWidth,"Size: ","%",PresetSlider.SIZE,UtilitySettings.bowAccuracySize,100,s->UtilitySettings.bowAccuracySize=s.getValueInt()));
            buttonList.add(new NeutralButton(4,x+14,y+152,tabWidth,20,"Decimal places: "+UtilitySettings.bowAccuracyDecimals,false));
            buttonList.add(new NeutralButton(11,x+18+tabWidth,y+152,tabWidth,20,"Prefix: "+(UtilitySettings.bowAccuracyPrefix?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(15,x+14,y+176,tabWidth,"Fade In: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyFadeIn,0,s->UtilitySettings.bowAccuracyFadeIn=s.getValueInt()));
            buttonList.add(new PresetSlider(16,x+18+tabWidth,y+176,tabWidth,"Fade Out: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyFadeOut,50,s->UtilitySettings.bowAccuracyFadeOut=s.getValueInt()));
            buttonList.add(new NeutralButton(12,x+14,y+200,tabWidth,20,"Mode: "+UtilitySettings.bowAccuracyMode.label,false));
            buttonList.add(new NeutralButton(13,x+18+tabWidth,y+200,tabWidth,20,"Animate Percentage: "+(UtilitySettings.bowAccuracyAnimate?"ON":"OFF"),false));
        }
        int footer=y+(page==1?228:202);
        buttonList.add(new NeutralButton(6,x+14,footer,110,20,"Reset HUD position",false));
        buttonList.add(new NeutralButton(7,x+w-94,footer,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==90){((ModuleReset)button).reset();initGui();return;}
        if(button.id==3||button.id==5||button.id==10||button.id==15||button.id==16)return;
        if(button.id==20||button.id==21){page=button.id-20;initGui();return;}
        if(button.id==7){mc.displayGuiScreen(parent);return;}
        if(button.id==0){UtilitySettings.bowOptimizer=!UtilitySettings.bowOptimizer;BowOptimizer.INSTANCE.echoes.clear();BowAccuracy.INSTANCE.reset();}
        if(button.id==1){UtilitySettings.bowOptimization=!UtilitySettings.bowOptimization;BowOptimizer.INSTANCE.echoes.clear();}
        if(button.id==2){UtilitySettings.bowAccuracy=!UtilitySettings.bowAccuracy;BowAccuracy.INSTANCE.reset();}
        if(button.id==4)UtilitySettings.bowAccuracyDecimals=(UtilitySettings.bowAccuracyDecimals+1)%3;
        if(button.id==11)UtilitySettings.bowAccuracyPrefix=!UtilitySettings.bowAccuracyPrefix;
        if(button.id==12)UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.values()[(UtilitySettings.bowAccuracyMode.ordinal()+1)%BowAccuracy.Mode.values().length];
        if(button.id==13)UtilitySettings.bowAccuracyAnimate=!UtilitySettings.bowAccuracyAnimate;
        if(button.id==6)UtilitySettings.hudPositions.remove(HudLayout.BOW);
        if(button.id==8){UtilitySettings.bowHitSound=!UtilitySettings.bowHitSound;BowAccuracy.INSTANCE.feedback.clear();}
        if(button.id==14)UtilitySettings.bowKillSound=!UtilitySettings.bowKillSound;
        if(button.id==9){UtilitySettings.bowHitMarker=!UtilitySettings.bowHitMarker;BowAccuracy.INSTANCE.feedback.clear();}
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,page==1?256:230);
        drawString(fontRendererObj,"Bow Optimizer",x+14,y+10,0xFFFFFFFF);
        drawString(fontRendererObj,"Accuracy = server-reported player hits / shots.",x+14,y+25,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
