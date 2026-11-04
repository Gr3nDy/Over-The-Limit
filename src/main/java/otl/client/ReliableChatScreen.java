package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Mouse;

/** Vanilla input, completion, history, clicks and hovers with a channel-tab strip. */
final class ReliableChatScreen extends GuiChat {
    private final String initial;
    private boolean overflow;
    private String context,confirm;
    private int menuX,menuScroll;
    private final List<Tab> tabs=new ArrayList<>();
    private final List<MenuItem> items=new ArrayList<>();
    private final List<String> overflowKeys=new ArrayList<>();
    private ReliableChat.View boundView;
    private long boundContext=-1;
    private static final class Tab {String key;int x,w;Tab(String k,int x,int w){key=k;this.x=x;this.w=w;}}
    private static final class MenuItem {String label;Runnable action;int y;MenuItem(String l,Runnable a,int y){label=l;action=a;this.y=y;}}
    ReliableChatScreen(String initial){super(initial);this.initial=initial;}
    String text(){return inputField==null?initial:inputField.getText();}
    @Override public void updateScreen(){
        super.updateScreen();
        if(ReliableChat.active()){
            syncView();
            String key=ReliableChat.session().selected;
            inputField.setMaxStringLength(key.startsWith("pm:")&&!text().startsWith("/")?100-6-key.substring(3).length():100);
        }
    }
    @Override public void initGui(){String typing=inputField==null?null:inputField.getText();super.initGui();boundView=ReliableChat.currentView();boundContext=ReliableChat.contextVersion();if(typing!=null)inputField.setText(typing);else if(initial.isEmpty()&&UtilitySettings.rememberDrafts)inputField.setText(boundView.draft);boundView.unread=0;}
    private void storeDraft(){ReliableChat.View view=boundView==null?ReliableChat.currentView():boundView;view.draft=UtilitySettings.rememberDrafts?text():"";}
    private void restoreDraft(){boundView=ReliableChat.currentView();boundContext=ReliableChat.contextVersion();inputField.setText(UtilitySettings.rememberDrafts?boundView.draft:"");inputField.setFocused(true);}
    private boolean syncView(){
        long version=ReliableChat.contextVersion();ReliableChat.View view=ReliableChat.currentView();
        if(version!=boundContext){overflow=false;context=null;confirm=null;tabs.clear();items.clear();overflowKeys.clear();boundContext=version;
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(GuiChat.class,this,false,"waitingOnAutocomplete","field_146414_r");
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(GuiChat.class,this,false,"playerNamesFound","field_146417_i");}
        if(boundView==view)return false;
        if(boundView!=null)storeDraft();restoreDraft();view.unread=0;return true;
    }
    @Override public void onGuiClosed(){if(inputField!=null&&ReliableChat.active())storeDraft();super.onGuiClosed();}
    @Override public void sendChatMessage(String msg,boolean addToChat){
        if(!ReliableChat.active()){super.sendChatMessage(msg,addToChat);return;}
        // A transfer may happen between reading the input and sending it. Never publish a stale PM draft.
        if(syncView())return;
        long sendingContext=ReliableChat.contextVersion();
        ReliableChat.View before=ReliableChat.currentView();before.draft=addToChat||!UtilitySettings.rememberDrafts?"":text();
        if(addToChat)mc.ingameGUI.getChatGUI().addToSentMessages(msg);
        String routed=ReliableChat.send(msg);
        if(sendingContext!=ReliableChat.contextVersion()){syncView();return;}
        if(net.minecraftforge.client.ClientCommandHandler.instance.executeCommand(mc.thePlayer,routed)==0)mc.thePlayer.sendChatMessage(routed);
        if(inputField!=null){if(addToChat){ReliableChat.currentView().draft="";inputField.setText("");}else if(before!=ReliableChat.currentView())inputField.setText(ReliableChat.currentView().draft);boundView=ReliableChat.currentView();boundContext=ReliableChat.contextVersion();}
    }
    private void select(String key){
        if(!ReliableChat.tabKeys().contains(key))return;
        boolean different=!ReliableChat.session().selected.equals(key);
        if(different)storeDraft();ReliableChat.session().select(key);if(different)restoreDraft();inputField.setFocused(true);
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(GuiChat.class,this,false,"waitingOnAutocomplete","field_146414_r");
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(GuiChat.class,this,false,"playerNamesFound","field_146417_i");
        String command=ReliableChat.channelCommand(key);
        if(command!=null){ReliableChat.noteSend(key);mc.thePlayer.sendChatMessage(command);}
        overflow=false;context=null;confirm=null;
    }
    @Override protected void keyTyped(char typed,int key)throws IOException{
        if(key==org.lwjgl.input.Keyboard.KEY_TAB&&isShiftKeyDown()&&UtilitySettings.cycleTabs){
            cycleNext();return;
        }
        super.keyTyped(typed,key);
    }
    void cycleNext(){List<String> keys=ReliableChat.tabKeys();if(keys.size()<2)return;int i=keys.indexOf(ReliableChat.session().selected);select(keys.get((i+1)%keys.size()));}
    @Override public void drawScreen(int x,int y,float partial){
        if(ReliableChat.active())syncView();super.drawScreen(x,y,partial);if(!ReliableChat.active())return;
        ReliableChat.Session s=ReliableChat.session();tabs.clear();int left=2,top=height-34;
        List<String> keys=ReliableChat.tabKeys();
        int channelWidth=52,fixed=channelWidth*3+48,pmWidth=Math.max(30,Math.min(96,(width-fixed-10)/Math.max(1,s.visible.size())));
        for(String key:keys){
            ReliableChat.View v=s.views.get(key);if(v==null)continue;boolean pm=key.startsWith("pm:");int w=pm?pmWidth:channelWidth;
            Tab tab=new Tab(key,left,w);tabs.add(tab);
            drawRect(left,top,left+w-2,top+17,key.equals(s.selected)?0xEE404040:0xCC202020);
            String count=UtilitySettings.counts&&v.unread>0?" "+v.unread:"";
            String name=fontRendererObj.trimStringToWidth(v.name,Math.max(8,w-8-fontRendererObj.getStringWidth(count)-(pm?10:0)));
            drawString(fontRendererObj,name+count,left+4,top+5,pm?0xFFD5A6FF:0xFFFFFFFF);
            if(pm)drawString(fontRendererObj,"x",left+w-12,top+5,FaceUi.MUTED);left+=w;
        }
        if(ReliableChat.fullChat()){
        int hiddenUnread=0;for(ReliableChat.View v:s.views.values())if(v.key.startsWith("pm:")&&!s.visible.contains(v.key))hiddenUnread+=v.unread;
        String more=UtilitySettings.counts&&hiddenUnread>0?"... "+hiddenUnread:"...";
        int moreW=Math.min(width-left-2,Math.max(26,fontRendererObj.getStringWidth(more)+8));
        tabs.add(new Tab("overflow",left,moreW));drawRect(left,top,left+moreW,top+17,0xDD252525);drawString(fontRendererObj,more,left+4,top+5,FaceUi.TEXT);
        }
        ReliableChat.View current=s.view();
        if(current.scroll()>0&&current.newBelow>0){String label="New messages \u2193";int w=fontRendererObj.getStringWidth(label)+12;drawRect(4,height-51,4+w,height-36,0xEE303030);drawString(fontRendererObj,label,10,height-47,FaceUi.TEXT);}
        items.clear();if(overflow||context!=null)drawMenu(s);
    }
    private void drawMenu(ReliableChat.Session s){
        int w=Math.min(210,width-8),x=Math.max(4,Math.min(menuX,width-w-4));menuX=x;
        List<String> hidden=new ArrayList<>();
        if(overflow)for(String key:overflowKeys)if(s.views.containsKey(key))hidden.add(key);
        int visible=Math.max(1,Math.min(8,(height-65)/18));menuScroll=Math.max(0,Math.min(menuScroll,Math.max(0,hidden.size()-visible)));
        int count=overflow?Math.min(visible,hidden.size())+1:2;
        int start=height-36-count*18;drawRect(x-1,start-1,x+w+1,start+count*18+1,0xFF666666);drawRect(x,start,x+w,start+count*18,0xF0202020);
        if(overflow){
            int row=0;for(int i=menuScroll;i<hidden.size()&&row<visible;i++,row++){
                String key=hidden.get(i);ReliableChat.View v=s.views.get(key);
                String label=v.name+(v.closed?" (closed)":"")+(UtilitySettings.counts&&v.unread>0?" ["+v.unread+"]":"");
                items.add(new MenuItem(label,()->select(key),start+row*18));
            }
            items.add(new MenuItem("clearAll".equals(confirm)?"Confirm: keep latest open PM":"Clear all Private Messages",()->{
                if(!"clearAll".equals(confirm)){confirm="clearAll";return;}String previous=s.selected;s.clearPrivateExceptLatest();if(!previous.equals(s.selected))restoreDraft();overflow=false;confirm=null;
            },start+row*18));
        }else{
            String key=context;
            items.add(new MenuItem("Close tab (keep history)",()->{boolean selected=s.selected.equals(key);storeDraft();s.close(key);context=null;if(selected)restoreDraft();},start));
            items.add(new MenuItem(key.equals(confirm)?"Confirm delete conversation":"Clear conversation history",()->{if(!key.equals(confirm)){confirm=key;return;}boolean selected=s.selected.equals(key);s.clear(key);if(selected)restoreDraft();context=null;confirm=null;},start+18));
        }
        for(MenuItem item:items)drawString(fontRendererObj,fontRendererObj.trimStringToWidth(item.label,w-12),x+6,item.y+5,FaceUi.TEXT);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(!ReliableChat.active()){super.mouseClicked(x,y,button);return;}
        syncView();
        if((overflow||context!=null)&&button==0)for(MenuItem item:new ArrayList<>(items))if(x>=menuX&&x<menuX+Math.min(210,width-8)&&y>=item.y&&y<item.y+18){item.action.run();return;}
        if(y>=height-34&&y<height-17){
            for(Tab tab:tabs)if(x>=tab.x&&x<tab.x+tab.w){
                if(tab.key.equals("overflow")){overflow=!overflow;context=null;menuX=Math.min(tab.x,width-214);menuScroll=0;confirm=null;overflowKeys.clear();for(ReliableChat.View view:ReliableChat.session().views.values())if(view.key.startsWith("pm:")&&!ReliableChat.session().visible.contains(view.key))overflowKeys.add(view.key);return;}
                if(tab.key.startsWith("pm:")&&(button==1||(button==0&&x>=tab.x+tab.w-14))){
                    if(button==1){context=tab.key;overflow=false;menuX=tab.x;confirm=null;}
                    else{boolean selected=ReliableChat.session().selected.equals(tab.key);storeDraft();ReliableChat.session().close(tab.key);if(selected)restoreDraft();}return;
                }
                if(button==0)select(tab.key);return;
            }
        }
        ReliableChat.View v=ReliableChat.currentView();
        if(button==0&&v.scroll()>0&&v.newBelow>0&&x>=4&&x<=fontRendererObj.getStringWidth("New messages \u2193")+16&&y>=height-51&&y<height-36){v.bottom();return;}
        if(overflow||context!=null){overflow=false;context=null;confirm=null;return;}
        super.mouseClicked(x,y,button);
    }
    @Override public void handleMouseInput()throws IOException{
        if(overflow){int wheel=Mouse.getEventDWheel();superHandleMouse();if(wheel!=0)menuScroll=Math.max(0,menuScroll+(wheel>0?-1:1));}
        else super.handleMouseInput();
    }
    private void superHandleMouse()throws IOException{
        // GuiChat's mouse-wheel handler is deliberately bypassed while browsing the popup.
        int button=Mouse.getEventButton();if(button>=0&&Mouse.getEventButtonState()){
            int x=Mouse.getEventX()*width/mc.displayWidth,y=height-Mouse.getEventY()*height/mc.displayHeight-1;mouseClicked(x,y,button);
        }
    }
}
