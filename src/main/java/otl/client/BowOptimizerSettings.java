package otl.client;

import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;

final class BowOptimizerSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,page;
    BowOptimizerSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-218)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+40,w-28,20,"Bow Optimizer: "+(UtilitySettings.bowOptimizer?"ON":"OFF"),false));
        int tabWidth=(w-32)/2;
        buttonList.add(new NeutralButton(20,x+14,y+64,tabWidth,18,"General",page==0));
        buttonList.add(new NeutralButton(21,x+18+tabWidth,y+64,tabWidth,18,"Bow Accuracy",page==1));
        if(page==0){
            buttonList.add(new NeutralButton(1,x+14,y+90,w-28,20,"Optimization: "+(UtilitySettings.bowOptimization?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(8,x+14,y+114,w-28,20,"Bow Hit Sound: "+(UtilitySettings.bowHitSound?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(9,x+14,y+138,w-28,20,"Hit Marker: "+(UtilitySettings.bowHitMarker?"ON":"OFF"),false));
            buttonList.add(new GuiSlider(10,x+14,y+162,w-28,20,"Hit marker size: ","%",50,200,UtilitySettings.bowHitMarkerSize,false,true,s->UtilitySettings.bowHitMarkerSize=s.getValueInt()));
        }else{
            buttonList.add(new NeutralButton(2,x+14,y+90,w-28,20,"Live Bow Accuracy: "+(UtilitySettings.bowAccuracy?"ON":"OFF"),false));
            buttonList.add(new GuiSlider(3,x+14,y+114,w-28,20,"Accuracy background: ","%",0,100,UtilitySettings.bowAccuracyOpacity,false,true,s->UtilitySettings.bowAccuracyOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(4,x+14,y+138,tabWidth,20,"Decimal places: "+UtilitySettings.bowAccuracyDecimals,false));
            buttonList.add(new NeutralButton(11,x+18+tabWidth,y+138,tabWidth,20,"Prefix: "+(UtilitySettings.bowAccuracyPrefix?"ON":"OFF"),false));
            buttonList.add(new GuiSlider(5,x+14,y+162,w-28,20,"Accuracy size: ","%",50,150,UtilitySettings.bowAccuracySize,false,true,s->UtilitySettings.bowAccuracySize=s.getValueInt()));
        }
        buttonList.add(new NeutralButton(6,x+14,y+190,110,20,"Reset HUD position",false));
        buttonList.add(new NeutralButton(7,x+w-94,y+190,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==3||button.id==5||button.id==10)return;
        if(button.id==20||button.id==21){page=button.id-20;initGui();return;}
        if(button.id==7){mc.displayGuiScreen(parent);return;}
        if(button.id==0){UtilitySettings.bowOptimizer=!UtilitySettings.bowOptimizer;BowOptimizer.INSTANCE.echoes.clear();BowAccuracy.INSTANCE.reset();}
        if(button.id==1){UtilitySettings.bowOptimization=!UtilitySettings.bowOptimization;BowOptimizer.INSTANCE.echoes.clear();}
        if(button.id==2){UtilitySettings.bowAccuracy=!UtilitySettings.bowAccuracy;BowAccuracy.INSTANCE.reset();}
        if(button.id==4)UtilitySettings.bowAccuracyDecimals=(UtilitySettings.bowAccuracyDecimals+1)%3;
        if(button.id==11)UtilitySettings.bowAccuracyPrefix=!UtilitySettings.bowAccuracyPrefix;
        if(button.id==6)UtilitySettings.hudPositions.remove(HudLayout.BOW);
        if(button.id==8){UtilitySettings.bowHitSound=!UtilitySettings.bowHitSound;BowAccuracy.INSTANCE.feedback.clear();}
        if(button.id==9){UtilitySettings.bowHitMarker=!UtilitySettings.bowHitMarker;BowAccuracy.INSTANCE.feedback.clear();}
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x,y,x+w,y+218,0xF0202020);
        drawString(fontRendererObj,"Bow Optimizer",x+14,y+10,0xFFFFFFFF);
        drawString(fontRendererObj,"Accuracy = server-reported player hits / shots.",x+14,y+25,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
