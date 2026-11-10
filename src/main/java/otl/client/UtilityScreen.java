package otl.client;

import net.minecraft.client.gui.*;

/** Editable off-server; all module gameplay remains scoped to approved servers. */
public final class UtilityScreen extends GuiScreen {
    private final boolean chatSettings;
    private final GuiScreen parent;
    private int x,y,w,h,page,pageSize;
    public UtilityScreen(){this(false);}
    public UtilityScreen(boolean settings){this(settings,null);}
    public UtilityScreen(boolean settings,GuiScreen parent){chatSettings=settings;this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    private void button(int id,int row,String label){buttonList.add(new NeutralButton(id,x+14,y+64+row*21,w-28,19,label,false));}
    private String state(boolean on){return on?"ON":"OFF";}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);h=Math.min(280,height-20);x=(width-w)/2;y=(height-h)/2;buttonList.clear();
        if(chatSettings){
            int tabWidth=(w-36)/3;
            for(int i=0;i<3;i++)buttonList.add(new NeutralButton(10+i,x+14+i*(tabWidth+4),y+42,tabWidth,16,new String[]{"General","Team bubbles","Appearance"}[i],page==i));
            if(page==0){
                button(0,0,"Reliable Chat: "+state(UtilitySettings.chat));button(1,1,"Unread counts: "+state(UtilitySettings.counts));
                button(4,2,"Remember drafts: "+state(UtilitySettings.rememberDrafts));button(5,3,"Unlimited history: "+state(UtilitySettings.unlimitedHistory));button(6,4,"Shift + Tab cycling: "+state(UtilitySettings.cycleTabs));button(14,5,"Show Tab Chat: "+state(UtilitySettings.showTabChat));button(15,6,"Tab position: "+UtilitySettings.tabPositionLabel());
            }else if(page==1){
                button(7,0,"Team speech bubbles: "+state(UtilitySettings.bubbles));button(18,1,"Mode: "+(UtilitySettings.bubbleTeamAndChat?"Team & Team Chat":"Team Chat only"));button(8,2,"Text limit: "+UtilitySettings.bubbleCharacters+" characters");button(9,3,"Duration: "+UtilitySettings.bubbleSeconds+" seconds");
            }else{
                buttonList.add(new PresetSlider(19,x+14,y+64,w-28,"Chat background: ","%",PresetSlider.PERCENT,UtilitySettings.chatBackground,25,s->UtilitySettings.chatBackground=s.getValueInt()));
                buttonList.add(new PresetSlider(16,x+14,y+85,w-28,"Tabs - not typing: ","%",PresetSlider.PERCENT,UtilitySettings.tabBackgroundOpacity,25,s->UtilitySettings.tabBackgroundOpacity=s.getValueInt()));
                buttonList.add(new PresetSlider(17,x+14,y+106,w-28,"Tabs - typing: ","%",PresetSlider.PERCENT,UtilitySettings.typingTabBackgroundOpacity,25,s->UtilitySettings.typingTabBackgroundOpacity=s.getValueInt()));
            }
            buttonList.add(new NeutralButton(2,x+14,y+h-27,106,19,"Reset chat options",false));
        }else{
            pageSize=Math.max(1,1+(h-110)/42);page=Math.min(page,(OtlModule.ALL.size()-1)/pageSize);
            for(int i=0;i<pageSize&&page*pageSize+i<OtlModule.ALL.size();i++){
                int index=page*pageSize+i;OtlModule m=OtlModule.ALL.get(index);
                GuiButton toggle=new NeutralButton(20+index,x+14,y+64+i*42,w-112,20,m.name+": "+(m.available()?state(m.enabled()):"DISABLED"),false);
                toggle.enabled=m.available();buttonList.add(toggle);
                GuiButton options=new NeutralButton(40+index,x+w-94,y+64+i*42,80,20,"Settings",false);
                options.enabled=m.available()&&m.settings(this)!=null;buttonList.add(options);
            }
            if(OtlModule.ALL.size()>pageSize){
                GuiButton previous=new NeutralButton(60,x+14,y+h-27,48,19,"<",false);previous.enabled=page>0;buttonList.add(previous);
                GuiButton next=new NeutralButton(61,x+68,y+h-27,48,19,">",false);next.enabled=(page+1)*pageSize<OtlModule.ALL.size();buttonList.add(next);
            }
        }
        if(chatSettings)buttonList.add(new ModuleReset(90,x+w-27,y+6,"reliable_chat"));
        buttonList.add(new NeutralButton(3,x+w-94,y+h-27,80,19,chatSettings?"Back":"Done",false));
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(chatSettings&&(b.id==16||b.id==17||b.id==19))return;
        if(b.id==3){mc.displayGuiScreen(parent!=null?parent:chatSettings?new UtilityScreen():null);return;}
        if(!chatSettings){
            if(b.id==60||b.id==61){page+=b.id==60?-1:1;initGui();return;}
            int n=OtlModule.ALL.size();
            if(b.id>=20&&b.id<20+n){
                OtlModule m=OtlModule.ALL.get(b.id-20);
                m.setEnabled(!m.enabled());UtilitySettings.save();initGui();
            }else if(b.id>=40&&b.id<40+n){
                GuiScreen s=OtlModule.ALL.get(b.id-40).settings(this);
                if(s!=null)mc.displayGuiScreen(s);
            }
            return;
        }
        if(b.id>=10&&b.id<=12){page=b.id-10;initGui();return;}
        if(b.id==0)UtilitySettings.chat=!UtilitySettings.chat;
        if(b.id==1)UtilitySettings.counts=!UtilitySettings.counts;
        if(b.id==4)UtilitySettings.rememberDrafts=!UtilitySettings.rememberDrafts;
        if(b.id==5)UtilitySettings.unlimitedHistory=!UtilitySettings.unlimitedHistory;
        if(b.id==6)UtilitySettings.cycleTabs=!UtilitySettings.cycleTabs;
        if(b.id==14)UtilitySettings.showTabChat=!UtilitySettings.showTabChat;
        if(b.id==15){UtilitySettings.tabChatPosition=Math.floorMod(UtilitySettings.tabChatPosition+(isShiftKeyDown()?-1:1),4);UtilitySettings.hudPositions.remove(HudLayout.TABS);}
        if(b.id==7)UtilitySettings.bubbles=!UtilitySettings.bubbles;
        if(b.id==18)UtilitySettings.bubbleTeamAndChat=!UtilitySettings.bubbleTeamAndChat;
        if(b.id==8){int n=UtilitySettings.bubbleCharacters+(isShiftKeyDown()?-20:20);UtilitySettings.bubbleCharacters=n>200?20:n<20?200:n;}
        if(b.id==9)UtilitySettings.bubbleSeconds=1+Math.floorMod(UtilitySettings.bubbleSeconds-1+(isShiftKeyDown()?-1:1),15);
        if(b.id==2){UtilitySettings.chat=true;UtilitySettings.counts=true;UtilitySettings.rememberDrafts=false;UtilitySettings.unlimitedHistory=false;UtilitySettings.cycleTabs=true;UtilitySettings.showTabChat=true;UtilitySettings.tabChatPosition=0;UtilitySettings.bubbles=false;UtilitySettings.bubbleTeamAndChat=false;UtilitySettings.bubbleCharacters=80;UtilitySettings.bubbleSeconds=4;UtilitySettings.chatBackground=25;UtilitySettings.tabBackgroundOpacity=25;UtilitySettings.typingTabBackgroundOpacity=25;UtilitySettings.hudPositions.remove(HudLayout.TABS);}
        UtilitySettings.save();ReliableChat.applyPreferences();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,h);
         drawString(fontRendererObj,chatSettings?"Reliable Chat":"Over The Limit "+Otl.VERSION,x+14,y+12,0xFFEAEAEA);
        drawString(fontRendererObj,ServerScope.active()?ServerScope.current()+(ReliableChat.fullChat()?" | Full chat":" | All-only chat"):"Inactive here - settings remain editable",x+14,y+27,0xFFAAAAAA);
        if(chatSettings){
            if(page==0)drawString(fontRendererObj,UtilitySettings.unlimitedHistory?"Unlimited history uses more memory.":"100 messages per view. History is session-only.",x+14,y+222,0xFFAAAAAA);
            else if(page==1){
                drawString(fontRendererObj,"Global mode needs a colored join/team message.",x+14,y+155,0xFFAAAAAA);
                drawString(fontRendererObj,"Two lines per bubble; never through walls.",x+14,y+168,0xFFAAAAAA);
                drawString(fontRendererObj,"Shift-click reduces duration or text limit.",x+14,y+181,0xFFAAAAAA);
            }else{
                drawString(fontRendererObj,"White underline marks the selected channel.",x+14,y+132,0xFFAAAAAA);
                drawString(fontRendererObj,"Drag the chat scrollbar to browse history.",x+14,y+146,0xFFAAAAAA);
            }
        }
        super.drawScreen(mx,my,partial);
    }
}
