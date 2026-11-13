package otl.client;

import net.minecraft.client.gui.*;
import java.io.IOException;

public final class KillFeedSettings extends SettingsScreen {
    private final GuiScreen parent;
    private int page,killY,deathY,assistY;
    private GuiTextField killColor,deathColor,assistColor;
    public KillFeedSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Kill Feed";}
    protected String subtitle(){
        return page==1?"Background = black fill; outline = colored stroke.":page==2?"Hex colors, or pick a swatch.":"Entries and chat routing.";
    }
    protected void buildPanel(){
        applyColors();killColor=null;deathColor=null;assistColor=null;
        resetButton("kill_feed");
        int tabs=panel.row(18);
        for(int i=0;i<3;i++)buttonList.add(new NeutralButton(10+i,panel.columnX(3,i),tabs,panel.columnWidth(3),18,new String[]{"General","Appearance","Colors"}[i],page==i));
        if(page==0){
            panel.section("ENTRIES & CHAT");
            buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Kill Feed: "+(UtilitySettings.killFeed?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Hide deaths in chat: "+(UtilitySettings.hideKillChat?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(14,panel.left(),panel.row(),panel.width(),"Visible entries: ","",PresetSlider.ROWS,UtilitySettings.killFeedRows,5,s->UtilitySettings.killFeedRows=s.getValueInt()));
            buttonList.add(new PresetSlider(15,panel.left(),panel.row(),panel.width(),"Fade out after: "," seconds",PresetSlider.SECONDS,UtilitySettings.killFeedSeconds,10,s->UtilitySettings.killFeedSeconds=s.getValueInt()));
            buttonList.add(new NeutralButton(18,panel.left(),panel.row(),panel.width(),20,"Slide animation: "+(UtilitySettings.killFeedSlide?"ON":"OFF"),false));
            buttonList.add(new ChoiceButton(this,25,panel.left(),panel.choice(),panel.width(),"Show entries",java.util.Arrays.stream(KillFeedRules.FilterMode.values()).map(v->v.label).toArray(String[]::new),UtilitySettings.killFeedFilter.ordinal(),v->UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.values()[v]));
            pin(new NeutralButton(2,actionX(),footerY(),110,20,"Clear kill history",false));
        }else if(page==1){
            panel.section("APPEARANCE");
            buttonList.add(new PresetSlider(4,panel.left(),panel.row(),panel.width(),"Size: ","%",PresetSlider.SIZE,UtilitySettings.killFeedSize,75,s->UtilitySettings.killFeedSize=s.getValueInt()));
            buttonList.add(new PresetSlider(5,panel.left(),panel.row(),panel.width(),"Background opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killBackgroundOpacity,25,s->UtilitySettings.killBackgroundOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(16,panel.left(),panel.row(),panel.width(),"Outline opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killBorderOpacity,100,s->UtilitySettings.killBorderOpacity=s.getValueInt()));
            buttonList.add(new ChoiceButton(this,22,panel.left(),panel.choice(),panel.width(),"Player heads",java.util.Arrays.stream(KillFeedRules.HeadMode.values()).map(v->v.label).toArray(String[]::new),UtilitySettings.killFeedHeads.ordinal(),v->UtilitySettings.killFeedHeads=KillFeedRules.HeadMode.values()[v]));
        }else{
            panel.section("BORDER COLORS");
            killY=panel.label("Your kills");
            deathY=panel.label("Your deaths");
            assistY=panel.label("Your assists");
            killColor=colorField(20,killY,UtilitySettings.killBorderColor);
            deathColor=colorField(21,deathY,UtilitySettings.deathBorderColor);
            assistColor=colorField(23,assistY,UtilitySettings.assistBorderColor);
            buttonList.add(new NeutralButton(19,panelX()+panelW()-178,killY,42,18,"Pick",false));
            buttonList.add(new NeutralButton(13,panelX()+panelW()-178,deathY,42,18,"Pick",false));
            buttonList.add(new NeutralButton(24,panelX()+panelW()-178,assistY,42,18,"Pick",false));
            buttonList.add(new NeutralButton(8,panel.left(),panel.row(),panel.width(),18,"Reset colors",false));
        }
        back(3);
    }
    private GuiTextField colorField(int id,int top,int rgb){
        GuiTextField field=new GuiTextField(id,fontRendererObj,panelX()+panelW()-115,top+1,101,18);
        field.setMaxStringLength(6);field.setText(String.format("%06X",rgb));return field;
    }
    private void applyColors(){
        if(killColor!=null&&killColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.killBorderColor=Integer.parseInt(killColor.getText(),16);
        if(deathColor!=null&&deathColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.deathBorderColor=Integer.parseInt(deathColor.getText(),16);
        if(assistColor!=null&&assistColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.assistBorderColor=Integer.parseInt(assistColor.getText(),16);
    }
    void applySwatch(int kind,int color){
        if(kind==0){UtilitySettings.killBorderColor=color;if(killColor!=null)killColor.setText(String.format("%06X",color));}
        else if(kind==1){UtilitySettings.deathBorderColor=color;if(deathColor!=null)deathColor.setText(String.format("%06X",color));}
        else {UtilitySettings.assistBorderColor=color;if(assistColor!=null)assistColor.setText(String.format("%06X",color));}
        UtilitySettings.save();
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4||b.id==5||b.id==14||b.id==15||b.id==16)return;
        applyColors();
        if(b.id==3){mc.displayGuiScreen(parent);return;}
        if(b.id==19||b.id==13||b.id==24){mc.displayGuiScreen(new ColorSwatches(this,b.id==19?0:b.id==13?1:2));return;}
        if(b.id>=10&&b.id<=12)page=b.id-10;
        if(b.id==0)UtilitySettings.killFeed=!UtilitySettings.killFeed;
        if(b.id==1)UtilitySettings.hideKillChat=!UtilitySettings.hideKillChat;
        if(b.id==2)KillFeed.history.clear();
        if(b.id==18)UtilitySettings.killFeedSlide=!UtilitySettings.killFeedSlide;
        if(b.id==22)UtilitySettings.killFeedHeads=KillFeedRules.HeadMode.values()[(UtilitySettings.killFeedHeads.ordinal()+1)%3];
        if(b.id==25)UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.values()[(UtilitySettings.killFeedFilter.ordinal()+1)%3];
        if(b.id==8){UtilitySettings.killBorderColor=0xFFBD45;UtilitySettings.deathBorderColor=0xFF5555;UtilitySettings.assistBorderColor=0x55FFFF;killColor=deathColor=assistColor=null;}
        UtilitySettings.save();initGui();
    }
    protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(killColor!=null){killColor.mouseClicked(mx,my,button);deathColor.mouseClicked(mx,my,button);assistColor.mouseClicked(mx,my,button);}super.mouseClicked(mx,my,button);
    }
    protected void keyTyped(char typed,int key)throws IOException{
        if(killColor!=null&&(killColor.isFocused()||deathColor.isFocused()||assistColor.isFocused())&&key!=org.lwjgl.input.Keyboard.KEY_ESCAPE){
            if(key==org.lwjgl.input.Keyboard.KEY_TAB){GuiTextField next=killColor.isFocused()?deathColor:deathColor.isFocused()?assistColor:killColor;killColor.setFocused(next==killColor);deathColor.setFocused(next==deathColor);assistColor.setFocused(next==assistColor);}
            else{killColor.textboxKeyTyped(typed,key);deathColor.textboxKeyTyped(typed,key);assistColor.textboxKeyTyped(typed,key);applyColors();}
        }else super.keyTyped(typed,key);
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);applyColors();UtilitySettings.save();}
    public void onGuiClosed(){applyColors();UtilitySettings.save();}
    protected void drawOverlay(int mx,int my,float partial){
        if(page!=2||killColor==null)return;
        killColor.setTextColor(killColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
        deathColor.setTextColor(deathColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
        assistColor.setTextColor(assistColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
        killColor.drawTextBox();deathColor.drawTextBox();assistColor.drawTextBox();
        int sx=panelX()+panelW()-128;
        drawRect(sx,killY+2,sx+9,killY+16,0xFF000000|UtilitySettings.killBorderColor);
        drawRect(sx,deathY+2,sx+9,deathY+16,0xFF000000|UtilitySettings.deathBorderColor);
        drawRect(sx,assistY+2,sx+9,assistY+16,0xFF000000|UtilitySettings.assistBorderColor);
    }
}
