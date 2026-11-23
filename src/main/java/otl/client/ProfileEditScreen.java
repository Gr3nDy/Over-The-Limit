package otl.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

/** The pencil opens one place to rename or remove a custom profile. */
final class ProfileEditScreen extends GuiScreen {
    private final GuiScreen parent;
    private final String name;
    private GuiTextField field;
    private int x,y;
    private String status="";

    ProfileEditScreen(GuiScreen parent,String name){this.parent=parent;this.name=name;}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui(){
        x=(width-260)/2;y=(height-130)/2;
        buttonList.clear();
        field=new GuiTextField(1,fontRendererObj,x+14,y+49,232,18);
        field.setMaxStringLength(24);
        field.setText(name);
        field.setFocused(true);
        buttonList.add(new NeutralButton(2,x+14,y+89,68,20,"Remove",false));
        buttonList.add(new NeutralButton(0,x+96,y+89,68,20,"Cancel",false));
        buttonList.add(new NeutralButton(1,x+178,y+89,68,20,"Save",false));
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==0){mc.displayGuiScreen(parent);return;}
        if(button.id==1){
            String next=field.getText().trim();
            if(next.isEmpty()){status="Enter a profile name.";return;}
            String result=Profiles.renameProfile(name,next);
            if(result.equals(name)&&!Profiles.safeName(next).equals(name)){
                status="Could not rename profile.";
                return;
            }
            mc.displayGuiScreen(parent);
            return;
        }
        mc.displayGuiScreen(new GuiYesNo((yes,id)->{
            if(yes){
                if(name.equals(UtilitySettings.profile))Profiles.switchProfile("Default");
                if(Profiles.deleteProfile(name)){mc.displayGuiScreen(parent);return;}
                status="Could not remove profile.";
            }
            mc.displayGuiScreen(this);
        },"Remove profile \""+name+"\"?","This cannot be undone.",0));
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(key==Keyboard.KEY_RETURN){actionPerformed(buttonList.get(2));return;}
        field.textboxKeyTyped(character,key);
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        super.mouseClicked(mx,my,button);
        field.mouseClicked(mx,my,button);
    }
    @Override public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,0x30000000);
        BedrockUi.menuSkin(0,x,y,260,130);
        BedrockUi.menuSkin(6,x+8,y+8,244,26);
        drawString(fontRendererObj,"EDIT PROFILE",x+16,y+17,0xFFFFBA74);
        drawString(fontRendererObj,"Profile name",x+14,y+39,0xFFF4F1E8);
        BedrockUi.menuSkin(2,x+12,y+46,236,24);
        field.drawTextBox();
        drawString(fontRendererObj,status,x+14,y+75,0xFFFFB8B0);
        super.drawScreen(mx,my,partial);
    }
}
