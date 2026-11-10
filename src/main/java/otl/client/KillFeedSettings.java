package otl.client;

import net.minecraft.client.gui.*;
import java.io.IOException;

public final class KillFeedSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,h,page;
    private GuiTextField killColor,deathColor,assistColor;
    public KillFeedSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);h=Math.min(250,height-20);x=(width-w)/2;y=(height-h)/2;buttonList.clear();killColor=null;deathColor=null;assistColor=null;
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"kill_feed"));
        for(int i=0;i<3;i++)buttonList.add(new NeutralButton(10+i,x+14+i*(w-28)/3,y+34,(w-34)/3,18,new String[]{"General","Appearance","Colors"}[i],page==i));
        if(page==0){
            BedrockUi.section(fontRendererObj,"DROPS",x+14,y+56,w-28);
            buttonList.add(new NeutralButton(0,x+14,y+70,w-28,20,"Kill Feed: "+(UtilitySettings.killFeed?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(1,x+14,y+95,w-28,20,"Hide deaths in chat: "+(UtilitySettings.hideKillChat?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(14,x+14,y+120,w-28,"Visible entries: ","",PresetSlider.ROWS,UtilitySettings.killFeedRows,5,s->UtilitySettings.killFeedRows=s.getValueInt()));
            buttonList.add(new PresetSlider(15,x+14,y+145,w-28,"Fade out after: "," seconds",PresetSlider.SECONDS,UtilitySettings.killFeedSeconds,10,s->UtilitySettings.killFeedSeconds=s.getValueInt()));
            buttonList.add(new NeutralButton(18,x+14,y+170,w-28,20,"Slide animation: "+(UtilitySettings.killFeedSlide?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(25,x+14,y+195,w-28,20,"Show entries: "+UtilitySettings.killFeedFilter.label,false));
            buttonList.add(new NeutralButton(2,x+14,y+h-27,110,19,"Clear kill history",false));
        }else if(page==1){
            BedrockUi.section(fontRendererObj,"APPEARANCE",x+14,y+56,w-28);
            buttonList.add(new PresetSlider(4,x+14,y+70,w-28,"Size: ","%",PresetSlider.SIZE,UtilitySettings.killFeedSize,75,s->UtilitySettings.killFeedSize=s.getValueInt()));
            buttonList.add(new PresetSlider(5,x+14,y+95,w-28,"Background opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killBackgroundOpacity,25,s->UtilitySettings.killBackgroundOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(16,x+14,y+120,w-28,"Outline opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killBorderOpacity,100,s->UtilitySettings.killBorderOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(22,x+14,y+175,w-28,20,"Player heads: "+UtilitySettings.killFeedHeads.label,false));
        }else{
            BedrockUi.section(fontRendererObj,"BORDER COLORS",x+14,y+56,w-28);
            killColor=colorField(20,y+70,UtilitySettings.killBorderColor);
            deathColor=colorField(21,y+95,UtilitySettings.deathBorderColor);
            assistColor=colorField(23,y+120,UtilitySettings.assistBorderColor);
            buttonList.add(new NeutralButton(19,x+w-178,y+70,42,18,"Pick",false));
            buttonList.add(new NeutralButton(13,x+w-178,y+95,42,18,"Pick",false));
            buttonList.add(new NeutralButton(24,x+w-178,y+120,42,18,"Pick",false));
            buttonList.add(new NeutralButton(8,x+w-94,y+152,80,18,"Reset colors",false));
        }
        buttonList.add(new NeutralButton(3,x+w-94,y+h-27,80,19,"Back",false));
    }
    private GuiTextField colorField(int id,int top,int rgb){
        GuiTextField field=new GuiTextField(id,fontRendererObj,x+w-115,top,101,18);
        field.setMaxStringLength(6);field.setText(String.format("%06X",rgb));return field;
    }
    private void applyColors(){
        if(killColor!=null&&killColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.killBorderColor=Integer.parseInt(killColor.getText(),16);
        if(deathColor!=null&&deathColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.deathBorderColor=Integer.parseInt(deathColor.getText(),16);
        if(assistColor!=null&&assistColor.getText().matches("[0-9a-fA-F]{6}"))UtilitySettings.assistBorderColor=Integer.parseInt(assistColor.getText(),16);
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
        if(b.id==8){UtilitySettings.killBorderColor=0xFFBD45;UtilitySettings.deathBorderColor=0xFF5555;UtilitySettings.assistBorderColor=0x55FFFF;}
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
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x-1,y-1,x+w+1,y+h+1,0xFF555555);BedrockUi.panel(x,y,w,h);
        drawString(fontRendererObj,"Kill Feed",x+14,y+12,0xFFEAEAEA);
        if(page==1){
            drawString(fontRendererObj,"Background = black fill; outline = colored stroke.",x+14,y+153,0xFFAAAAAA);
        }else if(page==2){
            drawString(fontRendererObj,"Your kills",x+14,y+75,0xFFFFFFFF);
            drawString(fontRendererObj,"Your deaths",x+14,y+100,0xFFFFFFFF);
            drawString(fontRendererObj,"Your assists",x+14,y+125,0xFFFFFFFF);
            killColor.setTextColor(killColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
            deathColor.setTextColor(deathColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
            assistColor.setTextColor(assistColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
            killColor.drawTextBox();deathColor.drawTextBox();assistColor.drawTextBox();
            drawRect(x+w-128,y+72,x+w-119,y+86,0xFF000000|UtilitySettings.killBorderColor);
            drawRect(x+w-128,y+97,x+w-119,y+111,0xFF000000|UtilitySettings.deathBorderColor);
            drawRect(x+w-128,y+122,x+w-119,y+136,0xFF000000|UtilitySettings.assistBorderColor);
        }
        super.drawScreen(mx,my,partial);
    }
}
