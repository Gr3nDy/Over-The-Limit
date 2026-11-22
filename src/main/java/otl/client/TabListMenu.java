package otl.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The player list made clickable in place: middle-clicking while it is up frees the cursor, a
 * player under the cursor is highlighted, and clicking one opens the same four-action popup as a
 * team-bar head. The list itself is left alone - it keeps drawing exactly as the client draws it.
 *
 * <p>Nothing here is a screen: the world keeps running, the cursor is only released, and mouse
 * events are swallowed while the list is open.
 */
final class TabListMenu {
    static final TabListMenu INSTANCE=new TabListMenu();
    /** LWJGL's middle mouse button, which vanilla binds to pick-block. */
    private static final int MIDDLE=2;

    /**
     * Vanilla's own tab order, read off the overlay instead of copied. A copy drifts: PGM sorts its
     * padding and team rows through the same comparator, and one difference there puts every name on
     * the row above the one it is drawn on - which looks like "some rows just do not work".
     */
    private static Object vanillaOrdering;
    private static boolean vanillaOrderingResolved;

    /**
     * PGM builds its tab list from `TabView` teams named `TabView000`..`TabView079`, and the number
     * is the slot the server intends that row to occupy. Sorting by it is authoritative: a client
     * that orders the list its own way cannot shift the names off their rows. Lists without those
     * teams keep the client's own order.
     */
    private static final java.util.regex.Pattern TAB_SLOT=java.util.regex.Pattern.compile("TabView(\\d+)");

    private static int slot(NetworkPlayerInfo info){
        ScorePlayerTeam team=info.getPlayerTeam();
        if(team==null)return Integer.MAX_VALUE;
        java.util.regex.Matcher found=TAB_SLOT.matcher(team.getRegisteredName());
        if(!found.find())return Integer.MAX_VALUE;
        try{
            return Integer.parseInt(found.group(1));
        }catch(NumberFormatException notASlot){
            return Integer.MAX_VALUE;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<NetworkPlayerInfo> sorted(Collection<NetworkPlayerInfo> map){
        List<NetworkPlayerInfo> all=new ArrayList<>(map);
        boolean serverSlots=false;
        for(NetworkPlayerInfo info:all)
            if(slot(info)!=Integer.MAX_VALUE){serverSlots=true;break;}
        if(serverSlots){
            all.sort((a,b)->{
                int sa=slot(a),sb=slot(b);
                if(sa!=sb)return sa<sb?-1:1;
                return ORDER.compare(a,b);
            });
            return all;
        }
        if(!vanillaOrderingResolved){
            vanillaOrderingResolved=true;
            for(Field field:GuiPlayerTabOverlay.class.getDeclaredFields()){
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(!com.google.common.collect.Ordering.class.isAssignableFrom(field.getType()))continue;
                try{
                    field.setAccessible(true);
                    vanillaOrdering=field.get(null);
                    break;
                }catch(ReflectiveOperationException|RuntimeException ignored){
                }
            }
        }
        if(vanillaOrdering instanceof com.google.common.collect.Ordering)
            return ((com.google.common.collect.Ordering<NetworkPlayerInfo>)vanillaOrdering).sortedCopy(all);
        Collections.sort(all,ORDER);
        return all;
    }

    /** Whether vanilla's ordering was found; false means the fallback comparator is in use. */
    static boolean usingVanillaOrder(){return vanillaOrdering instanceof com.google.common.collect.Ordering;}

    /** Whether the rows came from PGM's own TabView slot numbers, which is authoritative. */
    static boolean usingServerSlots(Minecraft mc){
        net.minecraft.client.network.NetHandlerPlayClient handler=mc.getNetHandler();
        if(handler==null)return false;
        for(NetworkPlayerInfo info:handler.getPlayerInfoMap())
            if(slot(info)!=Integer.MAX_VALUE)return true;
        return false;
    }

    /** Fallback only: the comparator vanilla uses, if its ordering field cannot be read. */
    private static final Comparator<NetworkPlayerInfo> ORDER=(a,b)->{
        boolean pa=a.getGameType()!=WorldSettings.GameType.SPECTATOR,pb=b.getGameType()!=WorldSettings.GameType.SPECTATOR;
        if(pa!=pb)return pa?-1:1;
        ScorePlayerTeam ta=a.getPlayerTeam(),tb=b.getPlayerTeam();
        int team=(ta==null?"":ta.getRegisteredName()).compareTo(tb==null?"":tb.getRegisteredName());
        return team!=0?team:a.getGameProfile().getName().compareTo(b.getGameProfile().getName());
    };

    boolean browsing;
    TeamBar.HeadMenu menu;
    private boolean lastLeft,lastMiddle;
    /** Whether the list was held open by the key (true) or toggled on (false) when browsing began. */
    boolean mustHold;
    boolean keyWasDown;

    /** Vanilla's grid: at most twenty rows, extra players continue in columns to the right. */
    static int[] grid(int players){
        int rows=players,columns=1;
        while(rows>20){columns++;rows=(players+columns-1)/columns;}
        return new int[]{columns,Math.max(0,rows)};
    }

    /**
     * The account name a row is showing, or null when the row is decoration. A server is free to
     * draw its list from fake entries whose display text is the real name (PGM does exactly that),
     * so the drawn text - not the profile name - is what a click has to resolve.
     */
    static String target(NetworkPlayerInfo info){
        if(info==null)return null;
        return target(info.getDisplayName()==null?null:info.getDisplayName().getFormattedText(),
            info.getGameProfile().getName());
    }

    /** The same decision on the raw strings, so it can be checked without a client. */
    static String target(String shown,String profileName){
        return target(shown,profileName,java.util.Collections.<String>emptySet());
    }

    /**
     * The account name a row is showing, or null when the row is decoration.
     *
     * <p>Servers put things around a name: a rank tag (`[MOD] Name`), a guild tag, a prestige star
     * or a score column. Requiring the whole drawn text to be an account name dropped every ranked
     * player, so a name is also looked for inside the text - but only a token that is a name this
     * client actually knows, or a row like `0/75 Red` would answer as a player called Red.
     */
    static String target(String shown,String profileName,java.util.Set<String> known){
        if(shown==null)return validName(profileName)?profileName:null;
        String plain=ChatRules.plain(shown).trim();
        if(validName(plain))return plain;
        // Bracketed rank and guild tags first, so "[MOD] Notch" needs no lookup at all.
        String bare=plain.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)|<[^>]*>|\\{[^}]*\\}"," ").trim();
        if(validName(bare))return bare;
        // The row's own profile name, when it is painted among the decoration: that is what a real
        // player row shows, and it needs no guessing at all.
        if(validName(profileName)&&containsToken(bare,profileName))return profileName;
        // Otherwise the last known account name in the text, for a server that draws its list from
        // fake entries (PGM does) and decorates the name it paints.
        String found=null;
        for(String token:bare.split("[^A-Za-z0-9_]+"))
            if(known!=null&&known.contains(token))found=token;
        return found;
    }

    /** Whether a token appears in the text on its own, not as part of a longer word. */
    private static boolean containsToken(String text,String token){
        return java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(token)
            +"(?![A-Za-z0-9_])").matcher(text).find();
    }

    /** An account name: anything else on the list is padding a server drew, not a player. */
    static boolean validName(String player){
        return player!=null&&player.matches("[A-Za-z0-9_]{1,16}");
    }

    /**
     * The text the client actually paints for a row, straight from the overlay. Using the client's
     * own name instead of rebuilding it from the display name and team keeps the width and the
     * click target identical to what is on screen, whatever a server puts in a row.
     */
    static String drawnName(Minecraft mc,NetworkPlayerInfo info){
        try{
            GuiPlayerTabOverlay tab=mc.ingameGUI.getTabList();
            String drawn=tab.getPlayerName(info);
            if(drawn!=null)return drawn;
        }catch(RuntimeException unavailable){
            // Fall through to the plain form rather than lose the list.
        }
        return info.getDisplayName()!=null?info.getDisplayName().getFormattedText()
            :ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(),info.getGameProfile().getName());
    }

    Layout layout(Minecraft mc){return layout(mc,new ScaledResolution(mc).getScaledWidth());}

    /**
     * The geometry vanilla's GuiPlayerTabOverlay draws with: one cell per column, nine pixels per
     * row, the server's header pushing every row down.
     */
    static Layout layout(Minecraft mc,int width){
        net.minecraft.client.network.NetHandlerPlayClient handler=mc.getNetHandler();
        if(handler==null||mc.theWorld==null)
            return new Layout(new ArrayList<NetworkPlayerInfo>(),new ArrayList<String>(),width/2,10,1,1,1,false);
        // PGM pads its tab list with fake entries (names like "|95-80f34791e431") to align its
        // columns. Those rows are drawn as blank space, so they must still count here or every
        // real player below them would be hit-tested one row too high; they are only made
        // unclickable, which is decided per row below.
        List<NetworkPlayerInfo> all=sorted(handler.getPlayerInfoMap());
        ScoreObjective objective=mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0);
        int nameW=0,scoreW=0;
        for(NetworkPlayerInfo info:all){
            nameW=Math.max(nameW,mc.fontRendererObj.getStringWidth(drawnName(mc,info)));
            if(objective!=null&&objective.getRenderType()!=IScoreObjectiveCriteria.EnumRenderType.HEARTS)
                scoreW=Math.max(scoreW,mc.fontRendererObj.getStringWidth(" "
                    +mc.theWorld.getScoreboard().getValueFromObjective(info.getGameProfile().getName(),objective).getScorePoints()));
        }
        List<NetworkPlayerInfo> players=all.size()>80?new ArrayList<>(all.subList(0,80)):all;
        // Every account name the client actually knows: the real profiles, and the players in the
        // world. A rank tag or a guild tag around a name is only stripped when what is left is one
        // of these, so a score row like "0/75 Red" cannot pass itself off as a player called Red.
        java.util.Set<String> known=new java.util.HashSet<>();
        for(NetworkPlayerInfo info:all)
            if(validName(info.getGameProfile().getName()))known.add(info.getGameProfile().getName());
        if(mc.theWorld!=null)
            for(net.minecraft.entity.player.EntityPlayer online:mc.theWorld.playerEntities)
                if(validName(online.getName()))known.add(online.getName());
        // The clickable name of each row, resolved once from what the client paints there.
        List<String> targets=new ArrayList<>(players.size());
        for(NetworkPlayerInfo info:players)
            targets.add(target(drawnName(mc,info),info.getGameProfile().getName(),known));
        int[] grid=grid(players.size());
        int columns=grid[0],rows=grid[1];
        boolean heads=mc.isIntegratedServerRunning()
            ||(handler.getNetworkManager()!=null&&handler.getNetworkManager().getIsencrypted());
        int score=objective==null?0:objective.getRenderType()==IScoreObjectiveCriteria.EnumRenderType.HEARTS?90:scoreW;
        int cell=Math.max(1,Math.min(columns*((heads?9:0)+nameW+score+13),width-50)/columns);
        int left=width/2-(cell*columns+(columns-1)*5)/2;
        return new Layout(players,targets,left,10+headerHeight(mc,width),cell,columns,rows,heads);
    }

    /** The server's list header pushes every row down; its text lives only on the overlay object. */
    private static int headerHeight(Minecraft mc,int width){
        IChatComponent header=headerOf(mc);
        if(header==null)return 0;
        return lines(mc,header,width)*mc.fontRendererObj.FONT_HEIGHT+1;
    }

    /** Vanilla wraps the header to the screen width minus fifty, exactly as it draws it. */
    static int lines(Minecraft mc,IChatComponent text,int width){
        return mc.fontRendererObj.listFormattedStringToWidth(text.getFormattedText(),width-50).size();
    }

    static IChatComponent headerOf(Minecraft mc){
        return mc.ingameGUI==null?null:overlayText(mc.ingameGUI.getTabList(),0,"header","field_175256_i");
    }

    static IChatComponent footerOf(Minecraft mc){
        return mc.ingameGUI==null?null:overlayText(mc.ingameGUI.getTabList(),1,"footer","field_175255_h");
    }

    /**
     * A chat component held by the tab overlay. The field is looked up on the object's real class and
     * its parents, then - for a client that renames or replaces the overlay - by declaration order
     * among its chat-component fields, since header and footer are the only two. Reading null here
     * would silently drop the header, and the header is what pushes every row down: one wrong line
     * count puts every name on the row above the one it is drawn on.
     */
    private static IChatComponent overlayText(GuiPlayerTabOverlay tab,int index,String...names){
        if(tab==null)return null;
        for(String name:names){
            Field field=named(tab.getClass(),name);
            if(field==null)continue;
            try{
                Object value=field.get(tab);
                return value instanceof IChatComponent?(IChatComponent)value:null;
            }catch(IllegalAccessException|RuntimeException unavailable){
                return null;
            }
        }
        java.util.List<Field> text=textFields(tab.getClass());
        if(index>=text.size())return null;
        try{
            Object value=text.get(index).get(tab);
            return value instanceof IChatComponent?(IChatComponent)value:null;
        }catch(IllegalAccessException|RuntimeException unavailable){
            return null;
        }
    }

    private static final java.util.Map<String,Field> FOUND_FIELDS=new java.util.HashMap<>();
    private static final java.util.Set<String> MISSING_FIELDS=new java.util.HashSet<>();

    /** A declared field by name, walking up the hierarchy so a client's subclass is covered. */
    private static Field named(Class<?> type,String name){
        String key=type.getName()+"#"+name;
        if(MISSING_FIELDS.contains(key))return null;
        Field cached=FOUND_FIELDS.get(key);
        if(cached!=null)return cached;
        for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass()){
            try{
                Field found=current.getDeclaredField(name);
                found.setAccessible(true);
                FOUND_FIELDS.put(key,found);
                return found;
            }catch(Exception absent){
                // Try the parent; the field may be declared there.
            }
        }
        MISSING_FIELDS.add(key);
        return null;
    }

    private static final java.util.Map<Class<?>,java.util.List<Field>> TEXT_FIELDS=new java.util.HashMap<>();

    private static java.util.List<Field> textFields(Class<?> type){
        java.util.List<Field> cached=TEXT_FIELDS.get(type);
        if(cached!=null)return cached;
        java.util.List<Field> found=new java.util.ArrayList<>();
        for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass()){
            for(Field field:current.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(!IChatComponent.class.isAssignableFrom(field.getType()))continue;
                try{
                    field.setAccessible(true);
                    found.add(field);
                }catch(RuntimeException unreadable){
                    // A field this client will not open is simply not a candidate.
                }
            }
        }
        TEXT_FIELDS.put(type,found);
        return found;
    }

    static final class Layout {
        final List<NetworkPlayerInfo> players;
        /** The account name each row shows, or null for a row that is decoration. */
        final List<String> targets;
        final int left,top,cell,columns,rows;
        /** Whether the client draws a head in front of each name, which shifts the name right. */
        final boolean heads;

        Layout(List<NetworkPlayerInfo> players,List<String> targets,int left,int top,int cell,
               int columns,int rows,boolean heads){
            this.players=players;this.targets=targets;this.left=left;this.top=top;this.cell=cell;
            this.columns=columns;this.rows=Math.max(1,rows);this.heads=heads;
        }

        int x(int index){return left+(index/rows)*(cell+5);}
        int y(int index){return top+(index%rows)*9;}

        /** The account name a row shows, or null when the row is decoration. */
        String target(int index){
            return index>=0&&index<targets.size()?targets.get(index):null;
        }

        /**
         * How far off a name's own line a click still counts as that name. Rows are nine pixels
         * tall, so four is exactly half: the bands tile without a gap and without two rows
         * claiming the same pixel.
         */
        static final int SNAP=4;

        /**
         * The named row nearest a point, or -1. Exact cell geometry is not used: a client draws its
         * own list with its own column width and row spacing, so the nearest name wins rather than
         * an exact 9px band. Decoration rows (PGM's team headers, scores and blank padding) are
         * skipped: they occupy a row but show nothing, so snapping to one would answer a click on a
         * visible name with silence.
         *
         * <p>A click within one row of a name still opens that name. A client that draws its header
         * or row pitch slightly differently shifts every row by a pixel or two, and without this a
         * name would simply not respond - which reads as a broken row rather than a moved one.
         */
        int indexAt(int mx,int my){
            if(players.isEmpty()||rows<=0)return -1;
            int best=nearest(mx,my,SNAP);
            if(best>=0)return best;
            return nearest(mx,my,SNAP+9);
        }

        /** The named row nearest a point within a vertical tolerance, or -1. */
        private int nearest(int mx,int my,int tolerance){
            if(my<top-tolerance||my>top+rows*9+tolerance)return -1;
            int best=-1,bestScore=Integer.MAX_VALUE;
            for(int i=0;i<players.size();i++){
                if(target(i)==null)continue;
                int dy=Math.abs(my-(y(i)+4));
                if(dy>tolerance)continue;
                int dx=columns>1?Math.abs(mx-(x(i)+cell/2)):0;
                int score=dy*4+dx;
                if(score<bestScore){bestScore=score;best=i;}
            }
            return best;
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void mouse(MouseEvent event){
        Minecraft mc=MinecraftAccess.get();
        if(!browsing){
            if(event.button==MIDDLE&&event.buttonstate&&canBegin(mc)){
                begin(mc);
                event.setCanceled(true);
            }
            return;
        }
        // The list owns the mouse: no attack, no block placement, and no re-grab on the next click.
        event.setCanceled(true);
        // Clicked here for immediacy, and again from tick() so a press the event loop skips still
        // lands. Both share one edge detector, so a click can never fire twice.
        ScaledResolution sr=new ScaledResolution(mc);
        poll(mc,event.x*sr.getScaledWidth()/mc.displayWidth,
            sr.getScaledHeight()-event.y*sr.getScaledHeight()/mc.displayHeight-1,
            Mouse.isButtonDown(0),Mouse.isButtonDown(MIDDLE));
    }

    private boolean canBegin(Minecraft mc){
        return UtilitySettings.tabPlayerMenu&&mc.thePlayer!=null&&mc.theWorld!=null
            &&mc.currentScreen==null&&!mc.gameSettings.hideGUI&&TeamBar.playerListVisible(mc);
    }

    /**
     * The keys held when the list opened. The list takes the keyboard, not the player's momentum:
     * these are re-applied every tick, so speed, sprint and a jump arc carry on, while anything
     * pressed while browsing is ignored.
     */
    private float heldForward,heldStrafe;
    private boolean heldJump,heldSneak,heldSprint;

    void begin(Minecraft mc){
        browsing=true;
        menu=null;
        // Read before the grab releases the keys: a list held open by the key must close when the
        // key is let go, while a toggled list closes on the next press instead.
        mustHold=Keyboard.isKeyDown(mc.gameSettings.keyBindPlayerList.getKeyCode());
        keyWasDown=mustHold;
        // Seeded from the buttons that opened the list, so it does not close on its own press.
        lastLeft=Mouse.isButtonDown(0);
        lastMiddle=true;
        if(mc.thePlayer!=null){
            heldForward=mc.thePlayer.movementInput.moveForward;
            heldStrafe=mc.thePlayer.movementInput.moveStrafe;
            heldJump=mc.thePlayer.movementInput.jump;
            heldSneak=mc.thePlayer.movementInput.sneak;
            heldSprint=mc.thePlayer.isSprinting();
        }
        mc.setIngameNotInFocus();
        // setIngameNotInFocus only unpressed keys when it had focus to give up, so put the held
        // walk back here too: the cursor is free, but the player keeps moving as they were.
        KeyBinding.unPressAllKeys();
        hold(mc);
    }

    /** Keeps the player doing exactly what they were doing when the list opened. */
    void hold(Minecraft mc){
        if(mc.thePlayer==null)return;
        mc.thePlayer.movementInput.moveForward=heldForward;
        mc.thePlayer.movementInput.moveStrafe=heldStrafe;
        mc.thePlayer.movementInput.jump=heldJump;
        mc.thePlayer.movementInput.sneak=heldSneak;
        if(heldSprint)mc.thePlayer.setSprinting(true);
    }

    /** Hands the keyboard back as it is really held, so walking resumes without a re-press. */
    static void syncKeys(Minecraft mc){
        for(KeyBinding kb:mc.gameSettings.keyBindings){
            int code=kb.getKeyCode();
            if(code>0&&code<Keyboard.KEYBOARD_SIZE)KeyBinding.setKeyBindState(code,Keyboard.isKeyDown(code));
        }
    }

    /**
     * One poll of the pointer. The tick and the smoke test both call this, so what is tested is
     * exactly what runs in game.
     */
    void poll(Minecraft mc,int mx,int my,boolean left,boolean middle){
        if(middle&&!lastMiddle){lastLeft=left;lastMiddle=middle;end(mc);return;}
        if(left&&!lastLeft)click(mc,mx,my);
        lastLeft=left;
        lastMiddle=middle;
    }

    /** Runs a left click at a point. */
    void click(Minecraft mc,int mx,int my){
        if(menu!=null){
            int action=menu.actionAt(mx,my);
            String target=menu.player;
            menu=null;
            if(action>=0)run(mc,action,target);
            else end(mc);
            return;
        }
        Layout layout=layout(mc);
        int index=layout.indexAt(mx,my);
        String target=layout.target(index);
        if(target!=null){
            // At the cursor, like any context menu, so it covers the least of the list.
            ScaledResolution sr=new ScaledResolution(mc);
            menu=new TeamBar.HeadMenu(target,mx,my,0,
                TeamBar.HeadMenu.width(mc,target),sr.getScaledWidth(),sr.getScaledHeight());
            return;
        }
        end(mc);
    }

    /**
     * Every action is guarded: a null command or a half-left server must close the menu quietly,
     * never take the game down mid-fight. Anything that cannot run says so, so a dead option is
     * never mistaken for a dead click.
     */
    private void run(Minecraft mc,int action,String player){
        end(mc);
        try{
            if(action==1){TeamBar.INSTANCE.confirmProfile(player);return;}
            if(mc.thePlayer==null)return;
            String command=action==0?TeamBar.teleportCommand(player)
                :action==2?TeamBar.friendCommand(player):null;
            if(action==3)ReliableChat.openPrivate(player);
            else if(command!=null)mc.thePlayer.sendChatMessage(command);
            else fail(mc);
        }catch(RuntimeException unavailable){
            fail(mc);
        }
    }

    private static void fail(Minecraft mc){
        if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(
            new ChatComponentText("\u00a7cThat action could not be completed."));
    }

    /**
     * Leaves browse mode. The cursor goes back to the game unless a screen already took it, so the
     * state can never be half-closed with a free pointer and no menu to click.
     */
    private void stop(Minecraft mc,boolean grab){
        browsing=false;
        menu=null;
        int key=mc.gameSettings.keyBindPlayerList.getKeyCode();
        if(key>0&&key<Keyboard.KEYBOARD_SIZE)KeyBinding.setKeyBindState(key,Keyboard.isKeyDown(key));
        if(grab&&mc.currentScreen==null&&mc.theWorld!=null&&!mc.inGameHasFocus)mc.setIngameFocus();
        // Closing must not need a re-press: the held keys are re-read from the keyboard here.
        syncKeys(mc);
    }

    void end(Minecraft mc){stop(mc,true);}

    /**
     * Whether the list key says the list was left. A held list closes when its key is let go; a
     * toggled list closes on the next press. Either way the caller re-grabs the mouse.
     */
    boolean keyLeft(boolean down){
        boolean left=down!=keyWasDown&&(mustHold?!down:down);
        keyWasDown=down;
        return left;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!browsing)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null||mc.theWorld==null||mc.currentScreen!=null||!UtilitySettings.tabPlayerMenu){
            stop(mc,false);
            return;
        }
        if(!org.lwjgl.opengl.Display.isActive()||Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)){end(mc);return;}
        int listKey=mc.gameSettings.keyBindPlayerList.getKeyCode();
        if(listKey>0&&listKey<Keyboard.KEYBOARD_SIZE){
            // Leaving the list hands the mouse straight back.
            if(keyLeft(Keyboard.isKeyDown(listKey))){end(mc);return;}
            // The cursor is free, so the list must be held up by hand or it would vanish with the key.
            KeyBinding.setKeyBindState(listKey,true);
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),false);
        // Keys pressed while browsing would otherwise start a different walk under the cursor.
        hold(mc);
        ScaledResolution sr=new ScaledResolution(mc);
        poll(mc,Mouse.getX()*sr.getScaledWidth()/mc.displayWidth,
            sr.getScaledHeight()-Mouse.getY()*sr.getScaledHeight()/mc.displayHeight-1,
            Mouse.isButtonDown(0),Mouse.isButtonDown(MIDDLE));
    }

    @SubscribeEvent public void screen(net.minecraftforge.client.event.GuiOpenEvent event){
        // The screen takes the mouse; re-grabbing here would fight it.
        if(event.gui!=null&&browsing)stop(MinecraftAccess.get(),false);
    }

    /**
     * Drawn after every other overlay element, including the player list itself: Forge fires
     * renderChat, then renderPlayerList, then post(ALL), so hooking CHAT put the popup under the
     * list. ALL is the only point after the list has been drawn.
     */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void draw(RenderGameOverlayEvent.Post event){
        if(!browsing||event.type!=RenderGameOverlayEvent.ElementType.ALL)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null||mc.theWorld==null)return;
        try{
            paint(mc,event.resolution.getScaledWidth(),event.resolution.getScaledHeight());
        }catch(RuntimeException broken){
            // A HUD element must never take the game down; drop the menu instead.
            browsing=false;
            menu=null;
        }
    }

    private void paint(Minecraft mc,int sw,int sh){
        int mx=Mouse.getX()*sw/mc.displayWidth,my=sh-Mouse.getY()*sh/mc.displayHeight-1;
        if(menu==null){
            Layout layout=layout(mc,sw);
            int index=layout.indexAt(mx,my);
            if(index>=0){
                // The whole cell, not a name-width sliver from a guessed name start: whether the
                // client draws a head in front of the name is not something to bet the highlight on.
                int x=layout.x(index),y=layout.y(index);
                Gui.drawRect(x,y,x+layout.cell-1,y+8,0x45FFFFFF);
            }
            // Short lists close under their own last row; tall ones stop above the hotbar.
            String hint="Click a name \u00b7 middle-click to close";
            int hintY=Math.min(layout.top+layout.rows*9+4,sh-60);
            int hx=(sw-mc.fontRendererObj.getStringWidth(hint))/2;
            Gui.drawRect(hx-4,hintY-2,hx+mc.fontRendererObj.getStringWidth(hint)+4,hintY+10,0x90000000);
            mc.fontRendererObj.drawStringWithShadow(hint,hx,hintY,BedrockUi.DIM);
            return;
        }
        TeamBar.drawHeadMenu(mc,menu,mx,my);
    }
}
