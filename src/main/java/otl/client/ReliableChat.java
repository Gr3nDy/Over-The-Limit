package otl.client;

import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.event.world.WorldEvent;

/** Routes copies into bounded vanilla chat views; the original All feed is never filtered. */
final class ReliableChat {
    static final ReliableChat INSTANCE=new ReliableChat();
    static final Map<String,Session> sessions=new HashMap<>();
    static RoutingHud installed;
    private static final ChatWelcomeGate welcomeGate=new ChatWelcomeGate();
    private static Object observedWorld,observedConnection;
    private static String observedHost="";
    private static volatile boolean contextDirty=true;
    private static long contextVersion;
    static boolean active(){UtilitySettings.load();observeContext();return UtilitySettings.chat&&ServerScope.active();}
    static boolean fullChat(){observeContext();return ServerScope.active()&&welcomeGate.enabled();}
    static long contextVersion(){observeContext();return contextVersion;}
    private static void observeContext(){
        Minecraft mc=MinecraftAccess.get();String host=mc.getCurrentServerData()==null?"":ServerScope.host(mc.getCurrentServerData().serverIP);
        Object connection=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        if(!contextDirty&&observedWorld==mc.theWorld&&observedConnection==connection&&observedHost.equals(host))return;
        boolean changed=observedConnection!=connection||!observedHost.equals(host);
        if(changed)welcomeGate.reset();else welcomeGate.worldChanged();
        contextDirty=false;contextVersion++;pending.clear();recentSend=null;
        observedWorld=mc.theWorld;observedConnection=connection;observedHost=host;
        Session next=sessions.get(host);if(next!=null&&changed)next.selected=ChatRules.ALL;
    }
    private static void observeWelcome(IChatComponent message){
        observeContext();if(!ServerScope.active()||!welcomeGate.accept(message.getFormattedText()))return;
        contextVersion++;Session s=session();
        s.selected=ChatRules.ALL;s.view().unread=0;
    }
    static List<String> tabKeys(){List<String> keys=new ArrayList<>();keys.add(ChatRules.ALL);if(fullChat()){keys.add(ChatRules.TEAM);keys.add(ChatRules.PARTY);keys.addAll(session().visible);}return keys;}
    static String channelCommand(String key){return null;}
    @SubscribeEvent public void connect(FMLNetworkEvent.ClientConnectedToServerEvent e){contextDirty=true;}
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e){contextDirty=true;}
    @SubscribeEvent public void worldLoad(WorldEvent.Load e){if(e.world.isRemote)contextDirty=true;}
    @SubscribeEvent public void worldUnload(WorldEvent.Unload e){if(e.world.isRemote)contextDirty=true;}
    static final class View {
        final String key,name;final GuiNewChat hud=new QuietChat();
        int unread,newBelow;long used,activity;boolean closed;String draft="";
        View(String key,String name){this.key=key;this.name=name;}
        void append(IChatComponent text,int id,boolean own){
            // The selected channel is already the user's active channel even when the
            // chat screen is closed, so messages in it must not create an unread badge.
            boolean reading=active()&&session().selected.equals(key);
            if(!own&&!reading)unread=Math.min(100,unread+1);
            if(scroll()>0&&!own)newBelow++;
            hud.printChatMessageWithOptionalDeletion(text.createCopy(),id);activity=++clock;
        }
        int scroll(){return ReflectionHelper.getPrivateValue(GuiNewChat.class,hud,"scrollPos","field_146250_j");}
        void bottom(){hud.resetScroll();newBelow=0;unread=0;}
    }
    static final class TimedLine extends ChatLine {
        final long receivedAt;
        TimedLine(int ticks,IChatComponent text,int id,long receivedAt){super(ticks,text,id);this.receivedAt=receivedAt;}
    }
    /** Isolated vanilla-rendered buffers: other channels cannot reset this view's scroll or ages. */
    private static final class QuietChat extends GuiNewChat {
        final List<ChatLine> history=ReflectionHelper.getPrivateValue(GuiNewChat.class,this,"chatLines","field_146252_h");
        final List<ChatLine> drawn=ReflectionHelper.getPrivateValue(GuiNewChat.class,this,"drawnChatLines","field_146253_i");
        QuietChat(){super(MinecraftAccess.get());}
        private int position(){return ReflectionHelper.getPrivateValue(GuiNewChat.class,this,"scrollPos","field_146250_j");}
        private void position(int n){
            n=Math.max(0,Math.min(n,Math.max(0,drawn.size()-getLineCount())));
            ReflectionHelper.setPrivateValue(GuiNewChat.class,this,n,"scrollPos","field_146250_j");
            ReflectionHelper.setPrivateValue(GuiNewChat.class,this,n>0,"isScrolled","field_146251_k");
        }
        private List<IChatComponent> split(IChatComponent text){return GuiUtilRenderComponents.splitText(text,Math.max(1,(int)(getChatWidth()/getChatScale())),MinecraftAccess.get().fontRendererObj,false,false);}
        private void trim(){if(!UtilitySettings.unlimitedHistory){while(history.size()>100)history.remove(history.size()-1);while(drawn.size()>100)drawn.remove(drawn.size()-1);}}
        @Override public boolean getChatOpen(){return active()&&MinecraftAccess.get().currentScreen instanceof ReliableChatScreen&&currentView().hud==this;}
        /** Do not inherit another client's global "new chat" animation/fade state. */
        @Override public void drawChat(int ticks){drawAt(System.nanoTime());}
        void drawAt(long now){
            Minecraft mc=MinecraftAccess.get();
            if(mc.gameSettings.chatVisibility==EntityPlayer.EnumChatVisibility.HIDDEN||drawn.isEmpty())return;
            final boolean open=getChatOpen();final int offset=position(),limit=getLineCount();
            final float scale=getChatScale(),opacity=mc.gameSettings.chatOpacity*.9F+.1F;
            final int width=MathHelper.ceiling_float_int(getChatWidth()/scale);
            int shown=0;
            GlStateManager.pushMatrix();
            try{
                GlStateManager.translate(2,20,0);GlStateManager.scale(scale,scale,1);
                for(int i=0;i+offset<drawn.size()&&i<limit;i++){
                    ChatLine line=drawn.get(i+offset);
                    double age=line instanceof TimedLine?Math.max(0,(now-((TimedLine)line).receivedAt)/50000000.0):200;
                    if(!open&&age>=200)continue;
                    double fade=MathHelper.clamp_double((1-age/200.0)*10,0,1);fade*=fade;
                    int alpha=(int)((open?255:(int)(255*fade))*opacity);shown++;
                    if(alpha<=3)continue;
                    int y=-i*9;
                    int background=ChatAppearance.backgroundAlpha(alpha,UtilitySettings.chatBackground);
                    if(background>0)Gui.drawRect(0,y-9,width+4,y,background<<24);
                    GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
                    mc.fontRendererObj.drawStringWithShadow(line.getChatComponent().getFormattedText(),0,y-8,0xFFFFFF|(alpha<<24));
                    GlStateManager.disableAlpha();GlStateManager.disableBlend();
                }
                if(open&&shown>0){
                    GlStateManager.translate(-3,0,0);
                    int total=drawn.size()*(mc.fontRendererObj.FONT_HEIGHT+1),visible=shown*(mc.fontRendererObj.FONT_HEIGHT+1);
                    int top=offset*visible/drawn.size(),thumb=visible*visible/total;
                    if(total!=visible){int color=offset>0?0xCCCCCC:0x777777,alpha=top>0?170:96;
                        Gui.drawRect(0,-top,2,-top-thumb,color|(alpha<<24));Gui.drawRect(2,-top,1,-top-thumb,0xDDDDDD|(alpha<<24));}
                }
            }finally{GlStateManager.popMatrix();}
        }
        @Override public void refreshChat(){
            int old=position();trim();drawn.clear();
            for(int i=history.size()-1;i>=0;i--){ChatLine line=history.get(i);long time=line instanceof TimedLine?((TimedLine)line).receivedAt:System.nanoTime();for(IChatComponent part:split(line.getChatComponent()))drawn.add(0,new TimedLine(line.getUpdatedCounter(),part,line.getChatLineID(),time));}
            trim();position(old);
        }
        @Override public void printChatMessageWithOptionalDeletion(IChatComponent text,int id){
            int old=position();if(id!=0)deleteChatLine(id);
            int ticks=MinecraftAccess.get().ingameGUI.getUpdateCounter();List<IChatComponent> parts=split(text);
            long now=System.nanoTime();history.add(0,new TimedLine(ticks,text,id,now));for(IChatComponent part:parts)drawn.add(0,new TimedLine(ticks,part,id,now));
            trim();position(old>0?old+parts.size():0);
        }
    }
    static long clock;
    static final class Session {
        final LinkedHashMap<String,View> views=new LinkedHashMap<>();final List<String> visible=new ArrayList<>();
        String selected,restoreSelection;
        String temporaryReturn;
        Session(String host){
            views.put(ChatRules.ALL,new View(ChatRules.ALL,"All"));views.put(ChatRules.TEAM,new View(ChatRules.TEAM,"Team"));views.put(ChatRules.PARTY,new View(ChatRules.PARTY,"Party"));
            restoreSelection=UtilitySettings.selected.getOrDefault(host,ChatRules.ALL);
            selected=views.containsKey(restoreSelection)?restoreSelection:ChatRules.ALL;
        }
        View view(){return views.get(selected);}
        View pm(String key,String name){
            View v=views.get(key);if(v==null){v=new View(key,name);views.put(key,v);}v.closed=false;
            if(!visible.contains(key)&&visible.size()<3)visible.add(key);return v;
        }
        void select(String key){
            if(!fullChat()){selected=ChatRules.ALL;views.get(ChatRules.ALL).unread=0;return;}
            View v=views.get(key);if(v==null)return;v.closed=false;
            if(key.startsWith("pm:")&&!visible.contains(key)){
                if(visible.size()==3){String replace=null;long oldest=Long.MAX_VALUE;for(String k:visible)if(!k.equals(selected)&&views.get(k).used<oldest){oldest=views.get(k).used;replace=k;}if(replace!=null)visible.remove(replace);}
                visible.add(key);
            }
            selected=key;v.used=++clock;v.unread=0;
            restoreSelection=key;
            UtilitySettings.selected.put(ServerScope.current(),ChatRules.channel(key)==null?ChatRules.ALL:key);UtilitySettings.save();
        }
        void close(String key){View v=views.get(key);if(v==null||!key.startsWith("pm:"))return;v.closed=true;visible.remove(key);if(selected.equals(key))select(ChatRules.ALL);}
        void clear(String key){
            View v=views.get(key);if(v==null)return;
            if(key.startsWith("pm:")){v.unread=0;v.newBelow=0;v.draft="";visible.remove(key);views.remove(key);pending.remove(ServerScope.current()+"|"+key);if(selected.equals(key))select(ChatRules.ALL);}
            else{v.hud.clearChatMessages();v.bottom();}
        }
        void clearPrivateExceptLatest(){
            View newest=null;for(View v:views.values())if(v.key.startsWith("pm:")&&!v.closed&&(newest==null||v.activity>newest.activity))newest=v;
            for(String key:new ArrayList<>(views.keySet()))if(key.startsWith("pm:")&&(newest==null||!key.equals(newest.key)))clear(key);
        }
    }
    static Session session(){UtilitySettings.load();observeContext();return sessions.computeIfAbsent(ServerScope.current(),Session::new);}
    static View currentView(){return session().view();}
    static void openChannel(String key){
        if(!active()||!fullChat())return;
        Session s=session();
        if(ChatRules.TEAM.equals(key)&&!ChatRules.TEAM.equals(s.selected))s.temporaryReturn=s.selected;
        s.select(key);
        Minecraft mc=MinecraftAccess.get();
        if(mc.currentScreen==null)mc.displayGuiScreen(new ReliableChatScreen(""));
    }
    static void restoreTemporaryChannel(){
        Session s=session();String key=s.temporaryReturn;s.temporaryReturn=null;
        if(key!=null&&s.views.containsKey(key))s.select(key);
    }
    static void applyPreferences(){
        for(Session s:sessions.values())for(View v:s.views.values()){if(!UtilitySettings.rememberDrafts)v.draft="";v.hud.refreshChat();}
        if(!UtilitySettings.bubbles)TeamBubbles.clearBubbles();
    }
    static void receive(IChatComponent text,int id){
        Session s=session();boolean own=ChatRules.authoredBy(text.getFormattedText(),MinecraftAccess.get().thePlayer.getName());s.views.get(ChatRules.ALL).append(text,id,own);
        if(!fullChat())return;
        TeamBubbles.receive(text);
        ChatRules.Route r=ChatRules.classify(text.getFormattedText());
        if(r.key.startsWith("pm:")){
            View v=s.pm(r.key,r.name);
            if(r.outgoing){
                Deque<Pending> sent=pending.get(ServerScope.current()+"|"+r.key);
                if(sent!=null){Iterator<Pending> it=sent.iterator();while(it.hasNext()){Pending p=it.next();if(System.nanoTime()-p.time>30000000000L){it.remove();continue;}if(p.body.equals(ChatRules.privateBody(text.getFormattedText()))){it.remove();break;}}
                    if(sent.isEmpty()){pending.remove(ServerScope.current()+"|"+r.key);if(recentSend!=null&&recentSend.key.equals(r.key))recentSend=null;}}
            }
            v.append(text,id,r.outgoing);
        }else if(!r.key.equals(ChatRules.ALL))s.views.get(r.key).append(text,id,own);
        else if(ChatWarnings.party(text.getFormattedText())){
            s.views.get(ChatRules.PARTY).append(text,id,true);
            if(recentSend!=null&&ChatRules.PARTY.equals(recentSend.key))recentSend=null;
        }
        else if(recentSend!=null&&recentSend.host.equals(ServerScope.current())&&System.nanoTime()-recentSend.time<8000000000L){
            SendContext sent=recentSend;
            if(sent.key.startsWith("pm:")&&ChatWarnings.privateMessage(text.getFormattedText())){
                View v=s.views.get(sent.key);Deque<Pending> queue=pending.get(sent.host+"|"+sent.key);Pending failed=queue==null?null:queue.pollLast();
                if(v==null&&failed!=null)v=s.pm(sent.key,failed.name);
                if(v!=null){
                    if(failed!=null)v.hud.printChatMessageWithOptionalDeletion(new ChatComponentText("\u00a77[Failed] To: "+v.name+" "+failed.body),failed.id);
                    v.append(text,id,true);
                }
                recentSend=null;
            }
        }
    }
    static final class SendContext {final String host=ServerScope.current(),key;final long time=System.nanoTime();SendContext(String key){this.key=key;}}
    static SendContext recentSend;
    static void noteSend(String key){recentSend=key==null?null:new SendContext(key);}
    static final class Pending {final int id;final String body,name;final long time=System.nanoTime();Pending(int id,String body,String name){this.id=id;this.body=body;this.name=name;}}
    static final Map<String,Deque<Pending>> pending=new HashMap<>();
    static int localId=-10000;
    private static final Pattern SEND_PM=Pattern.compile("^/(?:msg|message|tell|w)\\s+([A-Za-z0-9_]{1,16})\\s+(.+)$",Pattern.CASE_INSENSITIVE);
    static String send(String text){
        if(!fullChat()){noteSend(null);return text;}
        Session s=session();String result=ChatRules.outgoing(s.selected,text);
        String channel=ChatRules.commandChannel(result);if(channel!=null)s.select(channel);
        Matcher m=SEND_PM.matcher(result);
        noteSend(channel!=null?channel:!text.startsWith("/")?s.selected:null);
        if(m.matches()){
            String key="pm:"+m.group(1).toLowerCase(Locale.ROOT);int id=localId--;
            noteSend(key);
            // Do not invent a successful send or infer /r's target. The server's To line is authoritative.
            Deque<Pending> sent=pending.computeIfAbsent(ServerScope.current()+"|"+key,k->new ArrayDeque<>());sent.addLast(new Pending(id,m.group(2).trim(),m.group(1)));while(sent.size()>100)sent.removeFirst();
        }
        return result;
    }
    /** Server packets have one authoritative ingress, not the inherited GUI's print hooks. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public void serverChat(ClientChatReceivedEvent event){
        if(event.type==2)return;
        observeWelcome(event.message);
        if(!active())return;
        event.setCanceled(true);
        Minecraft mc=MinecraftAccess.get();GuiNewChat hud=mc.ingameGUI.getChatGUI();
        GuiNewChat fallback=hud instanceof RoutingHud?((RoutingHud)hud).original:hud;
        storeFallback(fallback,event.message);
        org.apache.logging.log4j.LogManager.getLogger(GuiNewChat.class).info("[CHAT] "+event.message.getUnformattedText());
        receive(event.message,0);
    }
    private static void storeFallback(GuiNewChat hud,IChatComponent message){
        // Keep vanilla OFF/fallback history, without invoking external/global print side effects.
        List<ChatLine> history=ReflectionHelper.getPrivateValue(GuiNewChat.class,hud,"chatLines","field_146252_h");
        List<ChatLine> drawn=ReflectionHelper.getPrivateValue(GuiNewChat.class,hud,"drawnChatLines","field_146253_i");
        int ticks=MinecraftAccess.get().ingameGUI.getUpdateCounter();IChatComponent copy=message.createCopy();
        history.add(0,new ChatLine(ticks,copy,0));
        for(IChatComponent part:GuiUtilRenderComponents.splitText(copy,Math.max(1,(int)(hud.getChatWidth()/hud.getChatScale())),MinecraftAccess.get().fontRendererObj,false,false))drawn.add(0,new ChatLine(ticks,part,0));
        while(history.size()>100)history.remove(history.size()-1);while(drawn.size()>100)drawn.remove(drawn.size()-1);
    }
    @SubscribeEvent public void open(GuiOpenEvent e){
        if(active()&&e.gui!=null&&e.gui.getClass()==GuiChat.class){
            String initial=ReflectionHelper.getPrivateValue(GuiChat.class,(GuiChat)e.gui,"defaultInputFieldText","field_146409_v");e.gui=new ReliableChatScreen(initial);
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=MinecraftAccess.get();
        if(mc.ingameGUI!=null&&!(mc.ingameGUI.getChatGUI() instanceof RoutingHud)){
            installed=new RoutingHud(mc,mc.ingameGUI.getChatGUI());
            ReflectionHelper.setPrivateValue(GuiIngame.class,mc.ingameGUI,installed,"persistantChatGUI","field_73840_e");
        }
        if(mc.currentScreen instanceof ReliableChatScreen&&!active())mc.displayGuiScreen(new GuiChat(((ReliableChatScreen)mc.currentScreen).text()));
    }
    /** Draw the channel strip as a real HUD overlay; GuiNewChat is not rendered on every launcher. */
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e){
        // HOTBAR is emitted reliably by vanilla and Lunar, unlike the aggregate ALL event.
        // CHAT is posted after the chat lines, keeping the strip visible instead of
        // letting the chat renderer paint over it.
        if(e.type!=RenderGameOverlayEvent.ElementType.CHAT||installed==null||!active()||!UtilitySettings.showTabChat)return;
        if(MinecraftAccess.get().currentScreen instanceof ReliableChatScreen)return;
        installed.drawTabs();
    }
    static final class RoutingHud extends GuiNewChat {
        final GuiNewChat original;
        RoutingHud(Minecraft mc,GuiNewChat original){super(mc);this.original=original;}
        GuiNewChat target(){return active()?currentView().hud:original;}
        @Override public void drawChat(int ticks){
            if(active()){
                drawAt(System.nanoTime());
            }else original.drawChat(ticks);
        }
        void drawTabs(){
            Minecraft mc=MinecraftAccess.get();ScaledResolution sr=new ScaledResolution(mc);Session s=session();
            int width=52,total=0;
            for(String key:tabKeys()){
                View v=s.views.get(key);if(v==null)continue;
                String label=v.name+(v.unread>0?" "+v.unread:"");
                total+=key.startsWith("pm:")?Math.min(110,Math.max(width,font(mc,label)+12)):width;
            }
            boolean right=UtilitySettings.tabChatPosition==1||UtilitySettings.tabChatPosition==3;
            boolean top=UtilitySettings.tabChatPosition==2||UtilitySettings.tabChatPosition==3;
            int left=right?Math.max(2,sr.getScaledWidth()-total-2):2;
            // Gameplay tabs use the very bottom edge; chat-screen tabs have their own layout.
            int topY=top?2:sr.getScaledHeight()-20;
            for(String key:tabKeys()){
                View v=s.views.get(key);if(v==null)continue;
                String label=v.name+(v.unread>0?" "+v.unread:"");
                int w=key.startsWith("pm:")?Math.min(110,Math.max(width,font(mc,label)+12)):width;
                Gui.drawRect(left,topY,left+w-2,topY+16,key.equals(s.selected)?0xEE404040:0xCC202020);
                mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(label,w-8),left+4,topY+4,0xFFFFFFFF);
                left+=w;if(left>=sr.getScaledWidth()-20)break;
            }
            View current=s.view();
            if(current.newBelow>0)mc.fontRendererObj.drawStringWithShadow("New messages",right?Math.max(2,sr.getScaledWidth()-font(mc,"New messages")-2):2,top?topY+19:topY-12,0xFFFFFF55);
        }
        private int font(Minecraft mc,String s){return mc.fontRendererObj.getStringWidth(s);}
        void drawAt(long now){
            GlStateManager.pushMatrix();
            try{if(MinecraftAccess.get().currentScreen instanceof ReliableChatScreen)GlStateManager.translate(0,-18,0);((QuietChat)currentView().hud).drawAt(now);}
            finally{GlStateManager.popMatrix();}
        }
        @Override public void printChatMessageWithOptionalDeletion(IChatComponent text,int id){
            original.printChatMessageWithOptionalDeletion(text,id);
            if(active())receive(text,id);
            else if(ServerScope.active())session().views.get(ChatRules.ALL).append(text,id,ChatRules.authoredBy(text.getFormattedText(),MinecraftAccess.get().thePlayer.getName()));
        }
        @Override public void clearChatMessages(){
            original.clearChatMessages();
            if(ServerScope.active()&&org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_F3)&&org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_D))for(View v:session().views.values()){v.hud.clearChatMessages();v.bottom();}
        }
        @Override public void deleteChatLine(int id){original.deleteChatLine(id);if(active())for(View v:session().views.values())v.hud.deleteChatLine(id);}
        @Override public void refreshChat(){original.refreshChat();for(Session s:sessions.values())for(View v:s.views.values())v.hud.refreshChat();}
        @Override public java.util.List<String> getSentMessages(){return original.getSentMessages();}
        @Override public void addToSentMessages(String text){original.addToSentMessages(text);}
        @Override public void scroll(int n){target().scroll(n);if(active()&&currentView().scroll()==0)currentView().newBelow=0;}
        @Override public void resetScroll(){if(!active())original.resetScroll();}
        @Override public IChatComponent getChatComponent(int x,int y){
            if(active()&&MinecraftAccess.get().currentScreen instanceof ReliableChatScreen)y-=18*new ScaledResolution(MinecraftAccess.get()).getScaleFactor();
            return target().getChatComponent(x,y);
        }
    }
}
