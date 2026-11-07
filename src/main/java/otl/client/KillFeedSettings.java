package otl.client;

import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;
import java.io.IOException;

final class KillFeedSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,h,page;
    private GuiTextField killColor,deathColor;
    KillFeedSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);h=Math.min(250,height-20);x=(width-w)/2;y=(height-h)/2;buttonList.clear();killColor=null;deathColor=null;
        for(int i=0;i<3;i++)buttonList.add(new NeutralButton(10+i,x+14+i*(w-28)/3,y+34,(w-34)/3,18,new String[]{"General","Appearance","Colors"}[i],page==i));
        if(page==0){
            buttonList.add(new NeutralButton(0,x+14,y+64,w-28,20,"Kill Feed: "+(UtilitySettings.killFeed?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(1,x+14,y+89,w-28,20,"Hide deaths in chat: "+(UtilitySettings.hideKillChat?"ON":"OFF"),false));
            buttonList.add(new GuiSlider(14,x+14,y+114,w-28,20,"Visible entries: ","",1,5,UtilitySettings.killFeedRows,false,true,s->UtilitySettings.killFeedRows=s.getValueInt()));
            buttonList.add(new GuiSlider(15,x+14,y+139,w-28,20,"Fade out after: "," seconds",1,30,UtilitySettings.killFeedSeconds,false,true,s->UtilitySettings.killFeedSeconds=s.getValueInt()));
            buttonList.add(new NeutralButton(18,x+14,y+164,w-28,20,"Slide animation: "+(UtilitySettings.killFeedSlide?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(2,x+14,y+h-27,110,19,"Clear kill history",false));
        }else if(page==1){
            buttonList.add(new GuiSlider(4,x+14,y+64,w-99,20,"Size: ","%",50,150,UtilitySettings.killFeedSize,false,true,s->UtilitySettings.killFeedSize=s.getValueInt()));
            buttonList.add(new NeutralButton(6,x+w-79,y+64,65,20,"Reset size",false));
            buttonList.add(new GuiSlider(5,x+14,y+89,w-99,20,"Background opacity: ","%",0,100,UtilitySettings.killBackgroundOpacity,false,true,s->UtilitySettings.killBackgroundOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(7,x+w-79,y+89,65,20,"Reset",false));
            buttonList.add(new GuiSlider(16,x+14,y+114,w-99,20,"Outline opacity: ","%",0,100,UtilitySettings.killBorderOpacity,false,true,s->UtilitySettings.killBorderOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(17,x+w-79,y+114,65,20,"Reset line",false));
        }else{
            killColor=colorField(20,y+64,UtilitySettings.killBorderColor);
            deathColor=colorField(21,y+89,UtilitySettings.deathBorderColor);
            buttonList.add(new NeutralButton(19,x+w-178,y+64,42,18,"Pick",false));
            buttonList.add(new NeutralButton(13,x+w-178,y+89,42,18,"Pick",false));
            buttonList.add(new NeutralButton(8,x+w-94,y+119,80,18,"Reset colors",false));
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
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==4||b.id==5||b.id==14||b.id==15||b.id==16)return;
        applyColors();
        if(b.id==3){mc.displayGuiScreen(parent);return;}
        if(b.id==19||b.id==13){mc.displayGuiScreen(new ColorSwatches(this,b.id==19));return;}
        if(b.id>=10&&b.id<=12)page=b.id-10;
        if(b.id==0)UtilitySettings.killFeed=!UtilitySettings.killFeed;
        if(b.id==1)UtilitySettings.hideKillChat=!UtilitySettings.hideKillChat;
        if(b.id==2)KillFeed.history.clear();
        if(b.id==6)UtilitySettings.killFeedSize=75;
        if(b.id==7)UtilitySettings.killBackgroundOpacity=100;
        if(b.id==17)UtilitySettings.killBorderOpacity=100;
        if(b.id==18)UtilitySettings.killFeedSlide=!UtilitySettings.killFeedSlide;
        if(b.id==8){UtilitySettings.killBorderColor=0xFFBD45;UtilitySettings.deathBorderColor=0xFF5555;}
        UtilitySettings.save();initGui();
    }
    protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(killColor!=null){killColor.mouseClicked(mx,my,button);deathColor.mouseClicked(mx,my,button);}super.mouseClicked(mx,my,button);
    }
    protected void keyTyped(char typed,int key)throws IOException{
        if(killColor!=null&&(killColor.isFocused()||deathColor.isFocused())&&key!=org.lwjgl.input.Keyboard.KEY_ESCAPE){
            if(key==org.lwjgl.input.Keyboard.KEY_TAB){boolean focused=killColor.isFocused();killColor.setFocused(!focused);deathColor.setFocused(focused);}
            else{killColor.textboxKeyTyped(typed,key);deathColor.textboxKeyTyped(typed,key);applyColors();}
        }else super.keyTyped(typed,key);
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);applyColors();UtilitySettings.save();}
    public void onGuiClosed(){applyColors();UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x-1,y-1,x+w+1,y+h+1,0xFF555555);drawRect(x,y,x+w,y+h,0xF0181818);
        drawString(fontRendererObj,"Kill Feed",x+14,y+12,0xFFEAEAEA);
        if(page==1){
            drawString(fontRendererObj,"Background = black fill; outline = colored stroke.",x+14,y+147,0xFFAAAAAA);
        }else if(page==2){
            drawString(fontRendererObj,"Your kills",x+14,y+69,0xFFFFFFFF);
            drawString(fontRendererObj,"Your deaths",x+14,y+94,0xFFFFFFFF);
            killColor.setTextColor(killColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
            deathColor.setTextColor(deathColor.getText().matches("[0-9a-fA-F]{6}")?0xFFFFFF:0xFF5555);
            killColor.drawTextBox();deathColor.drawTextBox();
            drawRect(x+w-128,y+66,x+w-119,y+80,0xFF000000|UtilitySettings.killBorderColor);
            drawRect(x+w-128,y+91,x+w-119,y+105,0xFF000000|UtilitySettings.deathBorderColor);
        }
        super.drawScreen(mx,my,partial);
    }
}
