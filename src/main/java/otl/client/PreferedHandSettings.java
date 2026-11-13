package otl.client;

import net.minecraft.client.gui.*;

public final class PreferedHandSettings extends SettingsScreen {
    private final GuiScreen parent;
    public PreferedHandSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Preferred Hand";}
    protected String subtitle(){return "Choose the hand and which held items it applies to.";}
    protected void buildPanel(){
        resetButton("left_hand");
        buttonList.add(new ChoiceButton(this,0,panel.left(),panel.choice(),panel.width(),"Main Hand",new String[]{"Right","Left"},UtilitySettings.mainHandLeft?1:0,v->UtilitySettings.mainHandLeft=v==1));
        buttonList.add(new ChoiceButton(this,1,panel.left(),panel.choice(),panel.width(),"Type",java.util.Arrays.stream(LeftHand.Type.values()).map(v->v.label).toArray(String[]::new),UtilitySettings.handType.ordinal(),v->UtilitySettings.handType=LeftHand.Type.values()[v]));
        panel.note("Other types use the opposite hand.");
        panel.note("Two-handed maps stay readable.");
        back(2);
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==90){((ModuleReset)button).reset();initGui();return;}
        if(button.id==2){mc.displayGuiScreen(parent);return;}
        if(button.id==0)UtilitySettings.mainHandLeft=!UtilitySettings.mainHandLeft;
        if(button.id==1)UtilitySettings.handType=UtilitySettings.handType.next();
        UtilitySettings.save();initGui();
    }
}
