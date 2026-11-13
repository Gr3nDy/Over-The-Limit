package otl.client;

import net.minecraft.client.gui.*;

/** Editable off-server; all module gameplay remains scoped to approved servers. */
public final class UtilityScreen extends SettingsScreen {
    private final GuiScreen parent;
    private int page;
    public UtilityScreen(){this(null);}
    public UtilityScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Reliable Chat";}
    protected String subtitle(){
        return ServerScope.active()?(ReliableChat.fullChat()?"Full chat enabled":"All-only chat mode"):"Inactive here - settings remain editable";
    }
    protected void buildPanel(){
        resetButton("reliable_chat");
        int tabs=panel.row(18);
        for(int i=0;i<3;i++)buttonList.add(new NeutralButton(10+i,panel.columnX(3,i),tabs,panel.columnWidth(3),18,new String[]{"General","Team bubbles","Appearance"}[i],page==i));
        if(page==0){
            panel.section("CHAT");
            toggle(0,"Reliable Chat",UtilitySettings.chat);
            toggle(1,"Unread counts",UtilitySettings.counts);
            toggle(4,"Remember drafts",UtilitySettings.rememberDrafts);
            toggle(5,"Unlimited history",UtilitySettings.unlimitedHistory);
            toggle(6,"Shift + Tab cycling",UtilitySettings.cycleTabs);
            toggle(14,"Show Tab Chat",UtilitySettings.showTabChat);
            buttonList.add(new ChoiceButton(this,15,panel.left(),panel.choice(),panel.width(),"Tab position",new String[]{"Bottom Left","Bottom Right","Top Left","Top Right"},UtilitySettings.tabChatPosition,v->{UtilitySettings.tabChatPosition=v;UtilitySettings.hudPositions.remove(HudLayout.TABS);ReliableChat.applyPreferences();}));
            panel.note(UtilitySettings.unlimitedHistory?"Unlimited history uses more memory.":"100 messages per view. History is session-only.");
        }else if(page==1){
            panel.section("TEAM BUBBLES");
            toggle(7,"Team speech bubbles",UtilitySettings.bubbles);
            buttonList.add(new ChoiceButton(this,18,panel.left(),panel.choice(),panel.width(),"Mode",new String[]{"Team Chat only","Team & Team Chat"},UtilitySettings.bubbleTeamAndChat?1:0,v->{UtilitySettings.bubbleTeamAndChat=v==1;ReliableChat.applyPreferences();}));
            buttonList.add(new PresetSlider(8,panel.left(),panel.row(),panel.width(),"Text limit: "," characters",new int[]{20,40,60,80,100,120,140,160,180,200},UtilitySettings.bubbleCharacters,80,s->UtilitySettings.bubbleCharacters=s.getValueInt()));
            buttonList.add(new PresetSlider(9,panel.left(),panel.row(),panel.width(),"Duration: "," seconds",new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},UtilitySettings.bubbleSeconds,4,s->UtilitySettings.bubbleSeconds=s.getValueInt()));
            panel.note("Global mode needs a colored join/team message.");
            panel.note("Two lines per bubble; never through walls.");
        }else{
            panel.section("APPEARANCE");
            buttonList.add(new PresetSlider(16,panel.left(),panel.row(),panel.width(),"Tabs - not typing: ","%",PresetSlider.PERCENT,UtilitySettings.tabBackgroundOpacity,25,s->UtilitySettings.tabBackgroundOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(17,panel.left(),panel.row(),panel.width(),"Tabs - typing: ","%",PresetSlider.PERCENT,UtilitySettings.typingTabBackgroundOpacity,25,s->UtilitySettings.typingTabBackgroundOpacity=s.getValueInt()));
            toggle(19,"Chat scrollbar",UtilitySettings.chatScrollbar);
            panel.note("White underline marks the selected channel.");
            panel.note("Drag the chat scrollbar to browse history.");
        }
        pin(new NeutralButton(2,actionX(),footerY(),110,20,"Reset chat options",false));
        back(3);
    }
    private void toggle(int id,String label,boolean on){
        buttonList.add(new NeutralButton(id,panel.left(),panel.row(),panel.width(),20,label+": "+(on?"ON":"OFF"),false));
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==16||b.id==17||b.id==8||b.id==9)return;
        if(b.id==3){mc.displayGuiScreen(parent);return;}
        if(b.id>=10&&b.id<=12){page=b.id-10;initGui();return;}
        if(b.id==0)UtilitySettings.chat=!UtilitySettings.chat;
        if(b.id==1)UtilitySettings.counts=!UtilitySettings.counts;
        if(b.id==4)UtilitySettings.rememberDrafts=!UtilitySettings.rememberDrafts;
        if(b.id==5)UtilitySettings.unlimitedHistory=!UtilitySettings.unlimitedHistory;
        if(b.id==6)UtilitySettings.cycleTabs=!UtilitySettings.cycleTabs;
        if(b.id==14)UtilitySettings.showTabChat=!UtilitySettings.showTabChat;
        if(b.id==7)UtilitySettings.bubbles=!UtilitySettings.bubbles;
        if(b.id==18)UtilitySettings.bubbleTeamAndChat=!UtilitySettings.bubbleTeamAndChat;
        if(b.id==19)UtilitySettings.chatScrollbar=!UtilitySettings.chatScrollbar;
        if(b.id==8){int n=UtilitySettings.bubbleCharacters+(isShiftKeyDown()?-20:20);UtilitySettings.bubbleCharacters=n>200?20:n<20?200:n;}
        if(b.id==9)UtilitySettings.bubbleSeconds=1+Math.floorMod(UtilitySettings.bubbleSeconds-1+(isShiftKeyDown()?-1:1),15);
        if(b.id==2){UtilitySettings.chat=true;UtilitySettings.counts=true;UtilitySettings.rememberDrafts=false;UtilitySettings.unlimitedHistory=false;UtilitySettings.cycleTabs=true;UtilitySettings.showTabChat=true;UtilitySettings.tabChatPosition=0;UtilitySettings.bubbles=false;UtilitySettings.bubbleTeamAndChat=false;UtilitySettings.bubbleCharacters=80;UtilitySettings.bubbleSeconds=4;UtilitySettings.tabBackgroundOpacity=25;UtilitySettings.typingTabBackgroundOpacity=25;UtilitySettings.chatScrollbar=true;UtilitySettings.hudPositions.remove(HudLayout.TABS);}
        UtilitySettings.save();ReliableChat.applyPreferences();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
