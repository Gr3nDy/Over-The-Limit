package otl.client;

import net.minecraft.client.gui.*;

public final class BowOptimizerSettings extends SettingsScreen {
    private final GuiScreen parent;
    private int page;
    public BowOptimizerSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Bow Optimizer";}
    protected String subtitle(){return "Accuracy = server-reported player hits / shots.";}
    protected void buildPanel(){
        resetButton("bow_optimizer");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Bow Optimizer: "+(UtilitySettings.bowOptimizer?"ON":"OFF"),false));
        int tabs=panel.row(18);
        buttonList.add(new NeutralButton(20,panel.columnX(2,0),tabs,panel.columnWidth(2),18,"General",page==0));
        buttonList.add(new NeutralButton(21,panel.columnX(2,1),tabs,panel.columnWidth(2),18,"Bow Accuracy",page==1));
        if(page==0){
            panel.section("FEEDBACK");
            buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Optimization: "+(UtilitySettings.bowOptimization?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(8,panel.left(),panel.row(),panel.width(),20,"Bow Hit Sound: "+(UtilitySettings.bowHitSound?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(14,panel.left(),panel.row(),panel.width(),20,"Bow-Kill Sound: "+(UtilitySettings.bowKillSound?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(9,panel.left(),panel.row(),panel.width(),20,"Hit Marker: "+(UtilitySettings.bowHitMarker?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(10,panel.left(),panel.row(),panel.width(),"Hit marker size: ","%",PresetSlider.HIT_SIZE,UtilitySettings.bowHitMarkerSize,100,s->UtilitySettings.bowHitMarkerSize=s.getValueInt()));
        }else{
            panel.section("ACCURACY READOUT");
            buttonList.add(new NeutralButton(2,panel.left(),panel.row(),panel.width(),20,"Live Bow Accuracy: "+(UtilitySettings.bowAccuracy?"ON":"OFF"),false));
            int row=panel.row();
            buttonList.add(new PresetSlider(3,panel.columnX(2,0),row,panel.columnWidth(2),"Background: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyOpacity,25,s->UtilitySettings.bowAccuracyOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(5,panel.columnX(2,1),row,panel.columnWidth(2),"Size: ","%",PresetSlider.SIZE,UtilitySettings.bowAccuracySize,100,s->UtilitySettings.bowAccuracySize=s.getValueInt()));
            row=panel.choice();
            buttonList.add(new ChoiceButton(this,4,panel.columnX(2,0),row,panel.columnWidth(2),"Decimal places",new String[]{"0","1","2"},UtilitySettings.bowAccuracyDecimals,v->UtilitySettings.bowAccuracyDecimals=v));
            buttonList.add(new NeutralButton(11,panel.columnX(2,1),row,panel.columnWidth(2),20,"Prefix: "+(UtilitySettings.bowAccuracyPrefix?"ON":"OFF"),false));
            row=panel.row();
            buttonList.add(new PresetSlider(15,panel.columnX(2,0),row,panel.columnWidth(2),"Fade In: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyFadeIn,0,s->UtilitySettings.bowAccuracyFadeIn=s.getValueInt()));
            buttonList.add(new PresetSlider(16,panel.columnX(2,1),row,panel.columnWidth(2),"Fade Out: ","%",PresetSlider.PERCENT,UtilitySettings.bowAccuracyFadeOut,50,s->UtilitySettings.bowAccuracyFadeOut=s.getValueInt()));
            buttonList.add(new ChoiceButton(this,12,panel.left(),panel.choice(),panel.width(),"Mode",java.util.Arrays.stream(BowAccuracy.Mode.values()).map(v->v.label).toArray(String[]::new),UtilitySettings.bowAccuracyMode.ordinal(),v->UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.values()[v]));
            buttonList.add(new NeutralButton(13,panel.left(),panel.row(),panel.width(),20,"Animate Percentage: "+(UtilitySettings.bowAccuracyAnimate?"ON":"OFF"),false));
        }
        pin(new NeutralButton(6,actionX(),footerY(),110,20,"Reset HUD position",false));
        back(7);
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
}
