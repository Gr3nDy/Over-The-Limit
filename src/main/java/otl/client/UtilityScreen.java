package otl.client;

import net.minecraft.client.gui.*;

/** Editable off-server; all module gameplay remains scoped to approved servers. */
final class UtilityScreen extends GuiScreen {
    private final boolean chatSettings;
    private int x,y,w,h,page;
    UtilityScreen(){this(false);}
    UtilityScreen(boolean settings){chatSettings=settings;}
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
                button(7,0,"Team speech bubbles: "+state(UtilitySettings.bubbles));button(8,1,"Text limit: "+UtilitySettings.bubbleCharacters+" characters");button(9,2,"Duration: "+UtilitySettings.bubbleSeconds+" seconds");
            }else{
                button(13,0,"Chat background: "+(UtilitySettings.chatBackground==0?"transparent":UtilitySettings.chatBackground+"%"));
            }
            buttonList.add(new NeutralButton(2,x+14,y+h-27,106,19,"Reset chat options",false));
        }else{
            // shortcut: 4 rows fit above Done, add paging when ALL grows past that
            for(int i=0;i<OtlModule.ALL.size()&&i<4;i++){
                OtlModule m=OtlModule.ALL.get(i);
                buttonList.add(new NeutralButton(20+i,x+14,y+64+i*42,w-112,20,m.name+": "+state(m.enabled()),false));
                buttonList.add(new NeutralButton(40+i,x+w-94,y+64+i*42,80,20,"Settings",false));
            }
        }
        buttonList.add(new NeutralButton(3,x+w-94,y+h-27,80,19,chatSettings?"Back":"Done",false));
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==3){mc.displayGuiScreen(chatSettings?new UtilityScreen():null);return;}
        if(!chatSettings){
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
        if(b.id==15)UtilitySettings.tabChatPosition=Math.floorMod(UtilitySettings.tabChatPosition+(isShiftKeyDown()?-1:1),4);
        if(b.id==7)UtilitySettings.bubbles=!UtilitySettings.bubbles;
        if(b.id==8){int n=UtilitySettings.bubbleCharacters+(isShiftKeyDown()?-20:20);UtilitySettings.bubbleCharacters=n>200?20:n<20?200:n;}
        if(b.id==9)UtilitySettings.bubbleSeconds=1+Math.floorMod(UtilitySettings.bubbleSeconds-1+(isShiftKeyDown()?-1:1),15);
        if(b.id==13){int n=UtilitySettings.chatBackground+(isShiftKeyDown()?-10:10);UtilitySettings.chatBackground=n>100?0:n<0?100:n;}
        if(b.id==2){UtilitySettings.chat=true;UtilitySettings.counts=true;UtilitySettings.rememberDrafts=false;UtilitySettings.unlimitedHistory=false;UtilitySettings.cycleTabs=true;UtilitySettings.showTabChat=true;UtilitySettings.tabChatPosition=0;UtilitySettings.bubbles=false;UtilitySettings.bubbleCharacters=80;UtilitySettings.bubbleSeconds=4;UtilitySettings.chatBackground=0;}
        UtilitySettings.save();ReliableChat.applyPreferences();initGui();
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x-1,y-1,x+w+1,y+h+1,0xFF555555);drawRect(x,y,x+w,y+h,0xF0181818);
         drawString(fontRendererObj,chatSettings?"Reliable Chat":"Over The Limit 0.3.1",x+14,y+12,0xFFEAEAEA);
        drawString(fontRendererObj,ServerScope.active()?ServerScope.current()+(ReliableChat.fullChat()?" | Full chat":" | All-only chat"):"Inactive here - settings remain editable",x+14,y+27,0xFFAAAAAA);
        if(chatSettings){
            if(page==0)drawString(fontRendererObj,UtilitySettings.unlimitedHistory?"Unlimited history uses more memory.":"100 messages per view. History is session-only.",x+14,y+222,0xFFAAAAAA);
            else if(page==1){
                drawString(fontRendererObj,"Two lines, above names, never through walls.",x+14,y+132,0xFFAAAAAA);
                drawString(fontRendererObj,"Shift-click reduces duration or text limit.",x+14,y+145,0xFFAAAAAA);
                drawString(fontRendererObj,fontRendererObj.trimStringToWidth("Team: "+(TeamBubbles.team()==null?"not detected yet":TeamBubbles.team()),w-28),x+14,y+162,0xFFAAAAAA);
            }else{
                drawString(fontRendererObj,"Default: transparent. Text opacity is unchanged.",x+14,y+103,0xFFAAAAAA);
                drawString(fontRendererObj,"Click +10%. Shift-click -10%.",x+14,y+117,0xFFAAAAAA);
            }
        }
        super.drawScreen(mx,my,partial);
    }
}
