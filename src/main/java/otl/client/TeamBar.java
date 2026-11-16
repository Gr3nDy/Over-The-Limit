package otl.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

/**
 * Team bar: one head row per side with the match clock between them.
 *
 * Teams come from the scoreboard, minus PGM's tab-sorting and observer teams: one row of heads per
 * team, stacked, with the single clock locked to the centre of the bar. The clock is read
 * from the live PGM line (boss bar, then sidebar, then tab footer) and then ticks locally, so it
 * keeps counting between server updates, freezes when the match ends and re-syncs on the next one.
 * Heads shrink as the shown amount grows so a 75-player team still fits on one row.
 */
final class TeamBar {
    static final TeamBar INSTANCE=new TeamBar();
    private static final int BASE_HEAD=16,MIN_HEAD=2,GAP=8;
    /** Space between stacked team rows, so two teams never read as one block of heads. */
    static final int ROW_GAP=4;
    /** Gap between the team-colour line and its heads. */
    private static final int LINE_GAP=2;
    /** Gap between an objective line and the clock it sits beside. */
    static final int OBJECTIVE_GAP=4;
    /** Height of one objective line; the stack is this tall per sidebar row. */
    static final int STRIP_LINE=10;
    /** The sixteen chat colour codes as RGB, for the team-colour line. */
    private static final int[] COLOURS={0xFF000000,0xFF0000AA,0xFF00AA00,0xFF00AAAA,0xFFAA0000,0xFFAA00AA,0xFFFFAA00,0xFFAAAAAA,
        0xFF555555,0xFF5555FF,0xFF55FF55,0xFF55FFFF,0xFFFF5555,0xFFFF55FF,0xFFFFFF55,0xFFFFFFFF};
    static final int WHITE=0xFFFFFFFF,YELLOW=0xFFFFD34F,RED=0xFFFF5555;
    /** A time limit turns red in its last stretch, so the end of the match reads at a glance. */
    static final int LOW_TIME=25;
    private static final int FALLBACK=0xFF8A8A8A,DEAD_FALLBACK=0xFF5A5A5A;
    private static final Pattern TIME=Pattern.compile("(\\d{1,3}):(\\d{2})");
    /** PGM counts the players in before the match: "Match starting in 10 seconds". */
    private static final Pattern STARTING=Pattern.compile("(?i)(?:match\\s+)?(?:starting|starts|begins)\\s+in\\D{0,10}(\\d{1,3})");
    private static final Pattern COLOUR=Pattern.compile("\u00a7([0-9a-fA-F])");
    /** PGM puts observers in their own team; it must never become one of the two sides. */
    private static final Pattern OBSERVERS=Pattern.compile("(?i).*\\b(observers?|spectators?|spec)\\b.*");
    /**
     * Dead state for drawing only. It changes on a death or respawn, not between frames, and each
     * lookup is a tab-list read plus a regex, so it is refreshed a few times a second.
     */
    private static final java.util.Map<String,Boolean> deadCache=new java.util.HashMap<>();
    private static long deadAt;
    private static String hovered;
    private static long hoverSince;
    private static final long DEAD_TTL=250_000_000L;
    private String actionBar;
    private long actionBarAt;
    private Object world;
    /** Last server clock value and when it arrived; the display ticks on from there. */
    private int baseSeconds=-1;
    private long baseNanos;
    private int direction;              // -1 countdown, +1 count up, 0 a static label
    private boolean ended;
    /** A match-start announcement has already started this match's local clock. */
    private boolean started;
    /** Overtime can be held by PGM while the match is tied. */
    private boolean overtime;
    /** This match has an explicit remaining-time clock, not just elapsed time. */
    private boolean timeLimitSeen;
    /** True while the middle slot counts the players in instead of showing the match clock. */
    private boolean preMatch=false;
    /** Display value captured when the match ended, so the stopped clock cannot drift. */
    private int frozenSeconds=-1;
    /**
     * Which source supplied this match's clock, locked so two sources cannot fight over the
     * display: the boss bar, the sidebar and the tab footer can each be a second or two apart,
     * and switching between them made the clock jump for no reason.
     */
    private int clockSource;
    /** Last line each source reported, when it last changed and how often, so a static line is
     *  not mistaken for a clock. */
    private final String[] lastLine=new String[5];
    private final long[] changedAt=new long[5];
    private final int[] changes=new int[5];
    /** The m:ss each source last reported, and which way that source's number is going. */
    private final int[] lastSeconds=new int[5];
    private final int[] hint=new int[5];
    /** Direction inferred from the chosen source's own change; 0 when it cannot be told. */
    private int directionHint;
    /** True once a source has been seen ticking in this match. Until then a line that never
     *  moves is still trusted; afterwards it is not, so losing the clock's source cannot freeze
     *  the bar or drag it onto a static "Time Limit" line. */
    private boolean observed;
    /**
     * How long a source may report the same value before it is treated as not counting. PGM
     * updates its clock about once a second, so a live source changes well inside this window.
     */
    static final long STALE=2_000_000_000L;
    private static Field footerField;
    private static boolean footerBroken;
    private static Field subtitleField,titleTimerField;
    private static boolean titleFieldsBroken;
    /** Last complete read of the teams, used when a team packet lands mid-frame. */
    private static List<Side> lastSides=new ArrayList<>();
    /** FFA slots retain their left/right assignment until a player leaves. */
    private static final List<String> ffaSlots=new ArrayList<>();
    private List<String> teamOrder=Collections.emptyList();
    private int mapLookup;
    private String teamMap="",welcomeTitle;
    /** A map has been announced and its match has not ended: outside that, the hub's teams mean nothing. */
    private boolean announced;
    /** The last match line was an end, so the next world unload is the way back to the hub. */
    private boolean matchOver;
    /** Keep briefly missing players, and keep confirmed dead online players until they respawn. */
    static final long KEEP_DEAD=10_000_000_000L,LEFT_GRACE=3_000_000_000L;
    private static final java.util.Map<String,List<String>> lastMembers=new java.util.HashMap<>();
    private static final java.util.Map<String,Long> goneAt=new java.util.HashMap<>(),tabSeen=new java.util.HashMap<>();
    private TeamBar(){}

    static final class Side {
        final String name;
        final List<String> members;
        Side(String name,List<String> members){this.name=name;this.members=members;}
    }

    static final class Timer {
        final String label;
        final int seconds;
        final boolean countdown;
        /** Match-start countdown: shown in red, before the real clock exists. */
        final boolean starting;
        Timer(String label,int seconds,boolean countdown,boolean starting){
            this.label=label;this.seconds=seconds;this.countdown=countdown;this.starting=starting;
        }
    }

    /** One frame of the bar: a stack of team rows per column plus the clock, already sized to fit. */
    static final class Bar {
        final List<List<String>> left,right;
        /** Space reserved on the outer edge of each column for the team-colour line; 0 when off. */
        final int line;
        final int head,advance,column,leftEnd,rightX,textX,textW,width,height,band;
        /** Objectives beside the clock, one line each, in sidebar order; empty when none. */
        final List<Objectives.Strip> leftStrips,rightStrips;
        final int leftStripX,rightStripX,leftStripW;
        /** Top of the objective stack and its total height, both inside the bar. */
        final int stripY,stripH;
        final Timer timer;
        final String label;
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label){this(left,right,head,textW,timer,label,0,null,null);}
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line){this(left,right,head,textW,timer,label,line,null,null);}
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips){
            this.left=left;this.right=right;this.head=head;this.advance=advance(head);this.timer=timer;
            this.line=line;
            this.leftStrips=leftStrips==null?Collections.<Objectives.Strip>emptyList():leftStrips;
            this.rightStrips=rightStrips==null?Collections.<Objectives.Strip>emptyList():rightStrips;
            // Both columns take the widest row, so the clock keeps the exact centre of the bar
            // and does not shift when players join or leave one side.
            int w=0;
            for(List<String> members:left)w=Math.max(w,row(members,head,advance));
            for(List<String> members:right)w=Math.max(w,row(members,head,advance));
            // The colour line sits on the outer edge, so the widest row plus the line is the column.
            this.column=w+line;
            this.textW=textW;
            // A side is as wide as its longest line, and each side's heads hug their own strip.
            // The bar is sized for the wider side and the clock sits on its exact centre, so the
            // narrower side just leaves its spare room on the outside instead of a gap by the clock.
            int leftW=TeamBar.stripWidth(this.leftStrips),rightW=TeamBar.stripWidth(this.rightStrips);
            this.leftStripW=leftW;
            int leftRoom=column+GAP+(leftW==0?0:leftW+OBJECTIVE_GAP);
            int rightRoom=column+GAP+(rightW==0?0:rightW+OBJECTIVE_GAP);
            int half=Math.max(leftRoom,rightRoom);
            this.width=2*half+textW;
            this.textX=half;
            this.leftEnd=textX-GAP-(leftW==0?0:leftW+OBJECTIVE_GAP);
            this.rightX=textX+textW+GAP+(rightW==0?0:rightW+OBJECTIVE_GAP);
            // Each line sits against the clock on its own side, right-aligned on the left.
            this.rightStripX=textX+textW+OBJECTIVE_GAP;
            this.leftStripX=textX-OBJECTIVE_GAP-leftW;
            // Each row is drawn in a band at least nine pixels tall so the clock still fits.
            this.band=Math.max(head,9);
            int rows=Math.max(left.size(),right.size());
            int stack=rows>0?rows*band+(rows-1)*ROW_GAP:band;
            // The objective stack is centred on the clock's own row, so it never reads as
            // lopsided, and the bar is tall enough to hold whichever of the two is taller.
            int lines=Math.max(this.leftStrips.size(),this.rightStrips.size());
            this.stripH=lines*STRIP_LINE;
            this.height=Math.max(stack,stripH);
            this.stripY=(height-stripH)/2;
            this.label=label;
        }
        /** Left edge of one objective line, against the clock on its own side. */
        int stripX(int side,Objectives.Strip one){return side==0?textX-OBJECTIVE_GAP-one.width:rightStripX;}
        /**
         * Where a right-column row starts: flush against the clock, growing outward to the right,
         * so an empty tail always falls on the bar's outside and never next to the clock.
         */
        int rightStart(List<String> members){return rightX;}
        /** Where a left-column row starts: flush against the clock, growing outward to the left. */
        int leftStart(List<String> members){return leftEnd-row(members,head,advance);}
        /** The team-colour line for a left row: at the row's outer end, not stranded in the gap. */
        int leftLine(List<String> members){return leftEnd-line-row(members,head,advance);}
        /** The team-colour line for a right row: just past the row's outer end. */
        int rightLine(List<String> members){return rightX+row(members,head,advance)+LINE_GAP;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void chat(ClientChatReceivedEvent event){
        if(event.type==2){ // action bar only; chat lines carry other players' times
            actionBar=ChatRules.plain(event.message.getUnformattedText()).trim();
            actionBarAt=System.nanoTime();
            return;
        }
        String formatted=event.message.getFormattedText();
        if(formatted.contains("\u00a7m")&&formatted.contains("\u00a7l")&&!ChatRules.playerMessage(formatted)){
            String title=ChatRules.plain(formatted).trim();
            if(!title.isEmpty()&&!title.contains("\n"))welcomeTitle=title;
        }
        for(String line:ChatRules.plain(event.message.getUnformattedText()).split("[\\r\\n]+")){
            String text=line.trim();
            if(MatchEnd.is(text)){ended=true;frozenSeconds=-1;matchOver=true;return;}
            if(text.startsWith("Created by ")&&welcomeTitle!=null&&!ChatRules.playerMessage(formatted)){
                String query=welcomeTitle;welcomeTitle=null;
                if(!MapInfo.norm(query).equals(MapInfo.norm(teamMap))){
                    announced=true;matchOver=false;reset(System.nanoTime());resolveTeamOrder(query);
                }
                continue;
            }
            // "Playing <map>" is the map change itself: the last match's clock must not linger.
            // shortcut: a player typing "Playing x" in chat also resets it; the next server clock line repairs it
            int playing=text.indexOf("Playing ");
            if(playing==0||(playing>0&&MapPreview.serverQuery(text.substring(playing))!=null)){
                announced=true;matchOver=false;reset(System.nanoTime());
                String query=MapPreview.serverQuery(text.substring(playing).trim());
                if(query!=null)resolveTeamOrder(query);
                return;
            }
            if(MatchEnd.kickoff(text)){announced=true;matchOver=false;if(!started||ended)begin(System.nanoTime());return;}
            if(!ended&&text.equalsIgnoreCase("Overtime! The timer will start after the tie is broken")){
                long now=System.nanoTime();
                if(baseSeconds>=0&&direction!=0)baseSeconds=Math.max(0,baseSeconds+(int)(Math.max(0,now-baseNanos)/1_000_000_000L)*direction);
                baseNanos=now;overtime=true;return;
            }
            // PGM sends its time limit as a chat message - "Time Remaining: 5:00", once a minute -
            // and puts it in no scoreboard, boss bar or tab footer at all. Without this the bar had
            // no clock to read and counted up from the kickoff, so a timed match looked untimed.
            if(!ended&&clock(text))ingest(text,System.nanoTime());
        }
    }

    /** A new map has loaded: nothing has carried a clock yet, so the middle starts again at 0:00. */
    private void reset(long now){
        ended=false;started=false;overtime=false;timeLimitSeen=false;frozenSeconds=-1;preMatch=true;baseSeconds=0;baseNanos=now;direction=0;clockSource=0;
        ffaSlots.clear();
        teamOrder=Collections.emptyList();teamMap="";welcomeTitle=null;mapLookup++;
        // The old map's clock lines must not make a source look like it is still counting.
        forgetSources();
    }

    private void resolveTeamOrder(String query){
        teamMap=query;
        int token=mapLookup;
        MapInfo local=MapInfo.findLocal(query);
        if(local!=null&&local.exact(query))setTeamOrder(local);
        if(local!=null&&!local.teamOrder.isEmpty()&&!local.incomplete)return;
        MapRemote.lookup(query,map->MinecraftAccess.get().addScheduledTask(()->{
            if(mapLookup==token&&map!=null&&map.exact(query))setTeamOrder(map);
        }));
    }

    private void setTeamOrder(MapInfo map){
        for(String name:map.teamOrder)if(name.trim().isEmpty()||name.contains("${"))return;
        if(!map.teamOrder.isEmpty())teamOrder=map.teamOrder;
    }

    /**
     * Forget what each source last said. The pre-match count-in and the previous map both tick,
     * so neither may leave a source looking like a running match clock or lend it a direction.
     */
    private void forgetSources(){
        observed=false;
        java.util.Arrays.fill(lastLine,null);java.util.Arrays.fill(changedAt,0L);java.util.Arrays.fill(changes,0);java.util.Arrays.fill(lastSeconds,-1);java.util.Arrays.fill(hint,0);directionHint=0;
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)clear();}

    private void clear(){actionBar=null;baseSeconds=-1;direction=0;ended=false;started=false;overtime=false;timeLimitSeen=false;frozenSeconds=-1;world=null;preMatch=false;clockSource=0;forgetSources();lastMembers.clear();goneAt.clear();tabSeen.clear();ffaSlots.clear();lastSides=new ArrayList<>();
        // Hub and match are separate worlds, so this also runs on the teleport INTO a match, where
        // the "Playing" line has already arrived. Only a match end means we are going back out.
        if(matchOver)announced=false;matchOver=false;}

    private void syncWorld(Minecraft mc){
        if(world!=mc.theWorld)clear();
        world=mc.theWorld;
        // Leaving the server: nothing is announced any more.
        if(mc.theWorld==null){announced=false;matchOver=false;teamOrder=Collections.emptyList();teamMap="";welcomeTitle=null;mapLookup++;}
    }

    /** Every real team, most members first; the local head leads its own team. Observers, tab sorting and leavers are dropped. */
    static List<Side> sides(Minecraft mc){return sides(mc,System.nanoTime());}
    /** The now parameter lets the smoke test age a leaver past the grace without waiting. */
    static List<Side> sides(Minecraft mc,long now){
        List<Side> found=new ArrayList<>();
        if(mc.theWorld==null)return found;
        Scoreboard board=mc.theWorld.getScoreboard();
        // Team packets arrive on the netty thread while this runs on the render thread, so the
        // scoreboard can change mid-iteration. Snapshot first, and never let a race kill the game.
        List<ScorePlayerTeam> teams;
        try{
            teams=new ArrayList<>(board.getTeams());
        }catch(RuntimeException racing){
            return lastSides;
        }
        // One head, one row. A team packet that removes a player and re-adds them elsewhere leaves
        // a moment where they are in no team at all, and the dead-player memory below would put a
        // second head for them on the old row - the same face on both sides of the bar.
        java.util.Set<String> claimed=new java.util.HashSet<>();
        for(ScorePlayerTeam team:teams){
            // PGM sorts the tab list with "\u0001TabViewNNN" teams whose members are fake "|xx-uuid" keys.
            if(team.getTeamName().contains("TabView")||OBSERVERS.matcher(ChatRules.plain(team.getTeamName())).matches())continue;
            List<String> names;
            try{
                names=new ArrayList<>(team.getMembershipCollection());
            }catch(RuntimeException racing){
                continue;
            }
            List<String> members=new ArrayList<>();
            for(String name:names)if(realPlayer(name))members.add(name);
            members=remembered(mc,team.getTeamName(),members,now);
            members=online(mc,members,now);
            List<String> unique=new ArrayList<>();
            for(String name:members)if(claimed.add(name.toLowerCase(Locale.ROOT)))unique.add(name);
            if(!unique.isEmpty())found.add(new Side(team.getTeamName(),unique));
        }
        // Fixed order by team name, never by player count: a team keeps its column for the whole
        // match, so the bar cannot reshuffle itself when somebody joins or leaves. Observers and
        // PGM's tab-sorting teams are already dropped above, so this order is the match's teams.
        sortSides(found,INSTANCE.teamOrder);
        for(int i=0;i<found.size();i++){
            List<String> members=new ArrayList<>(found.get(i).members);
            Collections.sort(members,String.CASE_INSENSITIVE_ORDER);
            found.set(i,new Side(found.get(i).name,members));
        }
        // Teams keep the fixed order above, so a team's column never changes mid-match. Your own
        // head leads the team list here so the "players shown" limit cannot cut it off; a
        // right-column block moves it to the outer end of its last row when the bar places it.
        if(mc.thePlayer!=null)for(int i=0;i<found.size();i++){
            Side side=found.get(i);
            int at=-1;
            for(int j=0;j<side.members.size();j++)if(VoteBook.you(side.members.get(j),mc.thePlayer.getName())){at=j;break;}
            if(at>0){List<String> members=new ArrayList<>(side.members);members.add(0,members.remove(at));found.set(i,new Side(side.name,members));}
        }
        lastSides=found;
        return found;
    }

    /**
     * Drops members who have left the server. The tab list is the online roster, so a name that
     * had a tab entry and lost it is a leaver; a short grace hides the gap while PGM re-sends the
     * team. A name that never had a tab entry is fixture data (or a server that hides its tab
     * list) and is kept as it is.
     */
    private static List<String> online(Minecraft mc,List<String> names,long now){
        if(mc.getNetHandler()==null||names.isEmpty())return names;
        List<String> present=new ArrayList<>();
        for(String name:names){
            if(mc.getNetHandler().getPlayerInfo(name)!=null){present.add(name);tabSeen.put(name,now);continue;}
            Long seen=tabSeen.get(name);
            if(seen==null||now-seen<LEFT_GRACE)present.add(name);
        }
        return present;
    }

    /**
     * A death can also take the player out of the scoreboard team for the respawn delay. Remember
     * the last member list per team so the head stays where it was instead of vanishing and
     * snapping back. Moving to another team or to the observers is not a death: that head belongs
     * in its new place straight away, so it is never left behind on the old row.
     */
    private static List<String> remembered(Minecraft mc,String team,List<String> current,long now){
        Scoreboard board=mc.theWorld.getScoreboard();
        List<String> previous=lastMembers.get(team);
        if(previous!=null)for(String name:previous){
            if(current.contains(name)){goneAt.remove(name);continue;}
            ScorePlayerTeam nowTeam=board.getPlayersTeam(name);
            if(nowTeam!=null&&!nowTeam.getTeamName().equals(team)){goneAt.remove(name);continue;}
            // Only a player still on the server can be respawning. A leaver has no tab entry, so
            // its short grace in online() decides and this memory must not keep the head around.
            if(tabSeen.containsKey(name)&&mc.getNetHandler()!=null&&mc.getNetHandler().getPlayerInfo(name)==null){goneAt.remove(name);continue;}
            goneAt.putIfAbsent(name,now);
            Long gone=goneAt.get(name);
            if(gone!=null&&(now-gone<KEEP_DEAD||(mc.getNetHandler()!=null&&mc.getNetHandler().getPlayerInfo(name)!=null&&dead(name))))current.add(name);
        }
        lastMembers.put(team,new ArrayList<>(current));
        return current;
    }

    /** A row holds up to ten heads; a bigger team wraps onto rows below. */
    static final int MAX_PER_ROW=10;
    /** Shrink one pixel for each five-player increase above the default of five. */
    static int headSize(int shown){return Math.max(MIN_HEAD,BASE_HEAD-Math.max(0,shown/5-1));}
    private static int advance(int head){return head+(head>=8?2:0);}
    private static int row(List<String> members,int head,int advance){return members.isEmpty()?0:(members.size()-1)*advance+head;}

    /**
     * Sizes the bar for the current teams and options; null in game while nothing carries a clock.
     *
     * Players shown sets the base head size in five-player steps; Size scales that base.
     * Rows still wrap when they would be wider than the screen.
     */
    Bar bar(Minecraft mc,boolean preview){
        if(!preview&&!ReliableChat.fullChat()){announced=false;return null;}
        if(!preview&&!UtilitySettings.teamBarObserver&&observing(mc))return null;
        int shown=Math.max(1,Math.min(75,UtilitySettings.teamBarPlayers));
        List<Side> sides=sides(mc);
        // No team has more than one player: that is FFA, where the colour belongs to the player.
        boolean ffa=isFfa(sides);
        Timer timer=timer(mc);
        // The hub runs a clock and keeps scoreboard teams of its own, so neither one means a match.
        // A PGM map or match-start announcement confirms a match, including through teleport.
        if(!preview&&!announced)return null;
        if(!preview)updateFfaSlots(ffaSlots,sides);
        // Heads are a match thing: the hub keeps scoreboard teams and runs its own countdown, so
        // only a match clock earns a bar. The pre-match count-in draws the clock alone, in red,
        // and the heads come back when the match really starts.
        // shortcut: a no-limit map joined mid-match shows no heads until the kickoff line
        if(timer==null&&!preview)return null;
        boolean counting=timer!=null&&timer.starting;
        String label=timer!=null?timer.label:preview?"10:00":"";
        int textW=label.isEmpty()?0:mc.fontRendererObj.getStringWidth(label);
        int scale=Math.max(50,Math.min(150,UtilitySettings.teamBarSize));
        int head=Math.max(MIN_HEAD,headSize(shown)*scale/100);
        ScaledResolution scaled=new ScaledResolution(mc);
        int screen=scaled.getScaledWidth(),screenH=scaled.getScaledHeight();
        List<Objectives.Strip> leftStrips=null,rightStrips=null;
        // Every match team's name, in the order the bar and PGM's sidebar use, with the column each
        // one's heads take. A team with nobody online still has objectives printed, so it still
        // needs to be known and still needs a side.
        List<String> allTeams=teamNames(mc,sides,teamOrder);
        Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<allTeams.size();i++){
            int rank=teamRank(allTeams.get(i),teamOrder);
            columns.put(MapInfo.norm(allTeams.get(i)),rank<teamOrder.size()?rank:i);
        }
        if(UtilitySettings.teamBarObjectives){
            // Each team's objectives sit on the same side of the clock as that team's heads, so a
            // team's goals are always next to its own row. Compact mode keeps only the state icons.
            // Each side may take a third of the room beside the clock, which fits a normal
            // objective row on one line and still leaves the heads readable. Both sides get the
            // same room, so the clock stays centred.
            // shortcut: on a very narrow window a long objective row is dropped, not clipped
            int max=Math.max(24,(screen-textW-2*GAP-8)/3);
            List<List<Objectives.Strip>> beside=Objectives.strips(mc,allTeams,columns,UtilitySettings.teamBarObjectivesCompact,max);
            leftStrips=beside.get(0);rightStrips=beside.get(1);
        }
        int beside=Math.max(stripWidth(leftStrips)+OBJECTIVE_GAP,stripWidth(rightStrips)+OBJECTIVE_GAP);
        int perRow=perRow(head,line(head,ffa),textW+2*beside,screen);
        List<List<String>> left=new ArrayList<>(),right=new ArrayList<>();
        if(counting&&!preview){
            // Counting in: the middle only, so nothing suggests the teams are set yet.
        }else if(sides.isEmpty()){
            // No teams yet (or you are watching from the observer team): keep the clock alone and
            // centred instead of dropping the bar, so it never sits off centre or disappears.
            if(preview){
                int fake=Math.max(1,Math.min(Math.min(shown,8),perRow));
                left.add(placeholders(fake));right.add(placeholders(fake));
            }
        }else if(ffa){
            // FFA has no teams: PGM puts every player on their own one-member team so each name
            // gets its own colour. Treat them as one pool of players - rows alternate between the
            // columns just to keep the stack balanced - and draw no team-colour line, since a line
            // per player would only repeat the heads' own colours.
            List<String> pool=preview?new ArrayList<>():ffaSlots;
            if(preview)for(Side side:sides)pool.addAll(side.members);
            // Slot parity keeps each player's side stable when someone else leaves.
            List<String> odd=new ArrayList<>(),even=new ArrayList<>();
            int count=0;
            for(int i=0;i<pool.size()&&count<shown;i++)if(pool.get(i)!=null){
                (i%2==0?odd:even).add(pool.get(i));count++;
            }
            for(int from=0;from<odd.size();from+=perRow)
                left.add(new ArrayList<>(odd.subList(from,Math.min(from+perRow,odd.size()))));
            for(int from=0;from<even.size();from+=perRow)
                right.add(new ArrayList<>(even.subList(from,Math.min(from+perRow,even.size()))));
        }else{
            // One block of rows per team, alternating columns. A team keeps the column its size
            // gives it, so joining a team never drags the block across the bar. A row holds up to
            // ten heads; the rest wrap onto the rows below rather than shrinking. A right-column
            // block hugs the clock, so the local head moves to the outer end of its last row.
            for(int i=0;i<sides.size();i++){
                List<String> members=new ArrayList<>(sides.get(i).members.subList(0,Math.min(shown,sides.get(i).members.size())));
                Integer columnIndex=columns.get(MapInfo.norm(sides.get(i).name));
                int index=columnIndex==null?i:columnIndex;
                if(index%2==1&&mc.thePlayer!=null&&members.remove(mc.thePlayer.getName()))members.add(mc.thePlayer.getName());
                List<List<String>> column=index%2==0?left:right;
                for(int from=0;from<members.size();from+=perRow)
                    column.add(new ArrayList<>(members.subList(from,Math.min(from+perRow,members.size()))));
            }
        }
        // Last resort, height only: a stack of many teams can still be taller than the screen.
        while(head>MIN_HEAD){
            if(new Bar(left,right,head,textW,timer,label,line(head,ffa),leftStrips,rightStrips).height<=screenH-8)break;
            head--;
        }
        return new Bar(left,right,head,textW,timer,label,line(head,ffa),leftStrips,rightStrips);
    }

    /** The width a side's objective stack needs: its longest line. */
    static int stripWidth(List<Objectives.Strip> lines){
        int w=0;
        if(lines!=null)for(Objectives.Strip one:lines)w=Math.max(w,one.width);
        return w;
    }

    /**
     * How many heads one row may hold at this size: ten at most, fewer when ten would make the bar
     * wider than the screen. Wrapping early keeps the head size the user asked for.
     */
    static int perRow(int head,int lineW,int textW,int screen){
        int column=(screen-8-textW-2*GAP)/2-lineW;
        return Math.max(1,Math.min(MAX_PER_ROW,(column-head)/advance(head)+1));
    }

    /**
     * FFA: PGM gives every player their own team (and their own colour) when there is no team to
     * play on. A party keeps a few of those players on one team, so not every side is a single -
     * but on a team map most sides are real teams, never one player each.
     * shortcut: a badly uneven team map (three teams of one) reads as FFA, which only costs the
     * colour line and the row split.
     */
    static boolean isFfa(List<Side> sides){
        if(sides.isEmpty())return false;
        int singles=0;
        for(Side side:sides)if(side.members.size()==1)singles++;
        return sides.size()>2&&singles*2>sides.size();
    }

    static int teamRank(String name,List<String> order){
        String key=MapInfo.norm(ChatRules.plain(name));
        for(int i=0;i<order.size();i++)if(key.equals(MapInfo.norm(ChatRules.plain(order.get(i)))))return i;
        return order.size();
    }

    /**
     * Every match team's name, in the bar's fixed order: the teams with heads first, then the teams
     * with nobody online. A team with no players draws no heads, but PGM still prints its
     * objectives, so it still needs a side of its own instead of falling in with everyone else's.
     */
    static List<String> teamNames(Minecraft mc,List<Side> sides,List<String> order){
        List<String> all=new ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(Side side:sides)if(seen.add(MapInfo.norm(side.name)))all.add(side.name);
        List<String> extra=new ArrayList<>();
        if(mc.theWorld!=null){
            try{
                for(ScorePlayerTeam team:new ArrayList<>(mc.theWorld.getScoreboard().getTeams())){
                    String name=team.getTeamName();
                    if(name.contains("TabView")||OBSERVERS.matcher(ChatRules.plain(name)).matches())continue;
                    if(seen.add(MapInfo.norm(name)))extra.add(name);
                }
            }catch(RuntimeException racing){
                // Same netty race as sides(): the teams already found are still enough to place them.
            }
        }
        extra.sort((a,b)->{
            int rank=Integer.compare(teamRank(a,order),teamRank(b,order));
            return rank!=0?rank:String.CASE_INSENSITIVE_ORDER.compare(a,b);
        });
        all.addAll(extra);
        return all;
    }

    static void sortSides(List<Side> sides,List<String> order){
        sides.sort((a,b)->{
            int rank=Integer.compare(teamRank(a.name,order),teamRank(b.name,order));
            return rank!=0?rank:String.CASE_INSENSITIVE_ORDER.compare(a.name,b.name);
        });
    }

    static void updateFfaSlots(List<String> slots,List<Side> sides){
        List<String> present=new ArrayList<>();
        for(Side side:sides)for(String name:side.members)present.add(name.toLowerCase(Locale.ROOT));
        for(int i=0;i<slots.size();i++)if(slots.get(i)!=null&&!present.contains(slots.get(i).toLowerCase(Locale.ROOT)))slots.set(i,null);
        for(Side side:sides)for(String name:side.members){
            boolean found=false;
            for(String slot:slots)if(slot!=null&&slot.equalsIgnoreCase(name)){found=true;break;}
            if(!found){int free=slots.indexOf(null);if(free<0)slots.add(name);else slots.set(free,name);}
        }
    }

    /** Width of the team-colour bar plus the gap to the heads; 0 when off or in FFA. */
    private static int line(int head,boolean ffa){
        return !ffa&&UtilitySettings.teamBarTeamColor?Math.max(2,head/5)+LINE_GAP:0;
    }

    private static List<String> placeholders(int count){
        List<String> names=new ArrayList<>();
        for(int i=1;i<=count;i++)names.add("P"+i);
        return names;
    }

    /** The bar's box on screen; the HUD editor drags and resizes this. */
    static HudLayout.Rect bounds(){
        Minecraft mc=MinecraftAccess.get();
        UtilitySettings.load();
        Bar bar=INSTANCE.bar(mc,true);
        return HudLayout.bounds(HudLayout.TEAM_BAR,bar.width,bar.height);
    }

    /**
     * Where a bar is drawn. Always measured from the bar itself: bounds() measures the editor's
     * preview bar, which invents placeholder heads and is far wider than the clock-only bar, so
     * using it for the overlay pushed the clock off centre before any team existed.
     */
    static HudLayout.Rect placed(Bar bar){return HudLayout.bounds(HudLayout.TEAM_BAR,bar.width,bar.height);}

    /** Timer shown in the middle, or null while nothing carries a clock. */
    Timer timer(Minecraft mc){return timer(mc,System.nanoTime());}
    /** The now parameter lets the smoke test check local ticking without waiting. */
    Timer timer(Minecraft mc,long now){
        syncWorld(mc);
        // Once the match has ended the clock stands still until the map changes: the next map's
        // count-in must not overwrite the final time. "Playing <map>" resets it.
        String line=clockLine(mc,now);
        if(line!=null&&!ended)ingest(line,now);
        if(baseSeconds<0)return null;
        // A fresh map with no clock yet (direction 0 and pre-match only comes from reset()): hold
        // the middle at 0:00 in the countdown colour, and no heads until the match really starts.
        if(!ended&&preMatch&&direction==0)return new Timer("0:00",0,false,true);
        // The countdown hit zero without a start line: the match is running, so count up instead.
        if(!ended&&preMatch&&now-baseNanos>=baseSeconds*1_000_000_000L)
            begin(baseNanos+baseSeconds*1_000_000_000L);
        if(ended){
            if(frozenSeconds<0&&direction!=0){
                long elapsed=overtime?0:Math.max(0,(now-baseNanos)/1_000_000_000L);
                frozenSeconds=Math.max(0,baseSeconds+(int)elapsed*direction);
            }
            int stopped=frozenSeconds>=0?frozenSeconds:baseSeconds;
            return new Timer(format(stopped),stopped,direction<0,preMatch);
        }
        int seconds=baseSeconds;
        if(direction!=0&&!overtime){
            long elapsed=Math.max(0,(now-baseNanos)/1_000_000_000L);
            seconds=Math.max(0,baseSeconds+(int)elapsed*direction);
        }
        return new Timer(format(seconds),seconds,direction<0,preMatch);
    }

    /** A match has started with no server clock: count up from 0:00 in white until one arrives. */
    private void begin(long now){
        ended=false;started=true;overtime=false;timeLimitSeen=false;frozenSeconds=-1;preMatch=false;
        baseSeconds=0;baseNanos=now;direction=1;
        forgetSources();
    }

    /** Takes one server clock line: it re-anchors the local clock every time the value moves. */
    private void ingest(String line,long now){
        Matcher start=STARTING.matcher(line);
        if(start.find()){
            int raw=Integer.parseInt(start.group(1));
            // A stale countdown must not restart the clock of a match already running.
            if(ended||preMatch||baseSeconds<0){
                // A new countdown only follows a match end (or a fresh join), so a line left over
                // from the countdown that just finished cannot restart the clock. // shortcut: relies on PGM always announcing the end first
                if(raw>0){
                    // The pre-match countdown is authoritative: re-anchor when the server repeats it.
                    if(!preMatch||baseSeconds<0||raw!=baseSeconds){baseSeconds=raw;baseNanos=now;}
                    preMatch=true;direction=-1;ended=false;frozenSeconds=-1;
                }else begin(now); // "starting in 0": the match is already under way
            }
            return;
        }
        Matcher m=TIME.matcher(line);
        if(!m.find())return;
        int raw=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2));
        preMatch=false; // a real clock replaced the countdown
        String lower=line.toLowerCase(Locale.ROOT);
        if(lower.contains("overtime"))overtime=true;
        if(explicitCountdown(line))timeLimitSeen=true;
        boolean countdown=overtime||lower.contains("remaining")||lower.contains("left")||lower.contains("limit")||lower.contains("ends in");
        if(overtime)direction=-1;
        // An idle PGM server writes a bare "0:00" into its scoreboard; that is not a match clock.
        if(baseSeconds<0&&raw==0&&!countdown)return;
        // A new value re-anchors the clock; the same value leaves it alone so the display keeps
        // ticking between updates. The direction comes from the label, or from a source seen
        // stepping between two of its own reads - never from comparing the server's value with a
        // locally advanced one, which after a source gap points the wrong way. A local count-up is
        // not proof of anything either: Overcast's footer is a bare "Time: 10:00", so guessing
        // "up" made a time-limit map count up from its own limit. Unknown stays a still number
        // until one tick settles it, at most a second.
        if(baseSeconds<0||raw!=baseSeconds){
            // A jump of more than a few seconds is a different clock, not this one ticking, so
            // what this match proved about its direction does not carry over to it.
            boolean continuation=baseSeconds>=0&&Math.abs(raw-baseSeconds)<=5;
            direction=countdown?-1:directionHint!=0?directionHint:continuation&&observed?direction:0;
            baseSeconds=raw;baseNanos=now;
        }
    }

    private static String format(int seconds){
        seconds=Math.max(0,seconds);
        return (seconds/60)+":"+(seconds%60<10?"0":"")+(seconds%60);
    }

    /** A line that carries a clock: either m:ss or the pre-match "starting in N seconds". */
    static boolean clock(String text){
        return text!=null&&(TIME.matcher(text).find()||STARTING.matcher(text).find());
    }

    private static boolean explicitCountdown(String text){
        if(text==null)return false;
        String lower=text.toLowerCase(Locale.ROOT);
        return lower.contains("remaining")||lower.contains("ends in")||lower.contains("overtime")||lower.contains("time left");
    }

    /**
     * The clock line, always from the same source while that source keeps ticking. A source that
     * disappears (the boss bar closing, the tab footer cleared) unlocks the choice, so the clock
     * carries on from the next source instead of stopping.
     *
     * A source that never ticks also loses it. PGM's sidebar carries the map's configured limit
     * ("Time Limit: 10:00") which never moves, while the live time is elsewhere ("Time: 4:32").
     * Trusting whichever line appeared first pinned the bar to the static one, so it showed a
     * number that was simply not the match time. Only a source whose value has moved can hold the
     * lock, and the freshest one wins, so joining a match in progress re-syncs within a second.
     */
    private String clockLine(Minecraft mc,long now){
        String[] lines=new String[5];
        directionHint=0;
        for(int source=1;source<=4;source++){
            lines[source]=sourceLine(mc,source);
            if(lines[source]==null){lastLine[source]=null;lastSeconds[source]=-1;continue;}
            // Whether this source's own number is going up or down, even when its label does not
            // say "remaining": PGM's tab footer is just "Time: 4:31". Only a one-second step counts
            // as ticking; a big jump is a new clock, not a direction.
            int value=seconds(lines[source]);
            if(value>=0&&lastSeconds[source]>=0&&value!=lastSeconds[source]){
                // A one-second step is this source ticking; a bigger jump is a different clock,
                // so the old direction must not be applied to the new number.
                hint[source]=Math.abs(value-lastSeconds[source])<=2?(value<lastSeconds[source]?-1:1):0;
            }
            lastSeconds[source]=value;
            if(!lines[source].equals(lastLine[source])){
                // The first sight is only a baseline: it says a clock exists, not that it counts.
                if(lastLine[source]!=null)changes[source]++;
                lastLine[source]=lines[source];
                changedAt[source]=now;
            }
        }
        int chosen=0;
        // A ticking explicit time limit beats the footer's separate elapsed match clock.
        for(int source=1;source<=4;source++)if(explicitCountdown(lines[source])&&changes[source]>0&&now-changedAt[source]<STALE){chosen=source;break;}
        // Hold the lock while its source is still ticking, so two live sources cannot fight.
        if(chosen==0&&clockSource!=0&&lines[clockSource]!=null&&(!timeLimitSeen||explicitCountdown(lines[clockSource]))&&changes[clockSource]>0&&now-changedAt[clockSource]<STALE)
            chosen=clockSource;
        // Otherwise follow the freshest source that has actually ticked.
        if(chosen==0){
            long freshest=Long.MIN_VALUE;
            for(int source=1;source<=4;source++){
                if(lines[source]==null||(timeLimitSeen&&!explicitCountdown(lines[source]))||changes[source]==0||now-changedAt[source]>=STALE)continue;
                if(changedAt[source]>freshest){freshest=changedAt[source];chosen=source;}
            }
        }
        // Nothing ticked recently: a paused clock keeps its own number. A line that never ticked
        // does not, because it may be the map's static "Time Limit" line rather than the match.
        if(chosen==0&&clockSource!=0&&lines[clockSource]!=null&&(!timeLimitSeen||explicitCountdown(lines[clockSource]))&&changes[clockSource]>0)chosen=clockSource;
        // A line that never moved is still usable when it is all there is (a join, or the first
        // second of a match). Once this match has had a ticking clock, its source going away -
        // PGM swapping the boss bar's text, say - must not hand the bar to a static line or leave
        // it stuck: the local clock counts on from its last value until a source ticks again.
        if(chosen==0&&!observed)for(int source=1;source<=4;source++)if(lines[source]!=null&&(!timeLimitSeen||explicitCountdown(lines[source]))){chosen=source;break;}
        clockSource=chosen;
        if(chosen==0)return null;
        observed|=changes[chosen]>0;
        // Only a source that is ticking right now may set the direction; a hint left over from
        // an earlier clock must not turn this match's count-up into a countdown.
        directionHint=now-changedAt[chosen]<STALE?hint[chosen]:0;
        return lines[chosen];
    }

    /** The m:ss value of a line, or -1 when it carries none. */
    private static int seconds(String line){
        Matcher m=TIME.matcher(line);
        return m.find()?Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)):-1;
    }

    /** One clock source on its own: 1 boss bar, 2 sidebar, 3 tab footer, 4 action bar. */
    private String sourceLine(Minecraft mc,int source){
        if(source==1){
            String boss=ChatRules.plain(BossStatus.bossName==null?"":BossStatus.bossName).trim();
            return clock(boss)?boss:null;
        }
        if(source==2)return sidebarTime(mc);
        if(source==3)return footerTime();
        if(source==4){
            // The action bar never says when it disappears, so only trust it while it is fresh.
            return actionBar!=null&&System.nanoTime()-actionBarAt<3_000_000_000L&&clock(actionBar)?actionBar:null;
        }
        return null;
    }

    private static String sidebarTime(Minecraft mc){
        for(String line:sidebarRows(mc))if(clock(ChatRules.plain(line).trim()))return ChatRules.plain(line).trim();
        return null;
    }

    /** Every sidebar row as the tab-style formatted text PGM wrote, top to bottom. */
    static List<String> sidebarRows(Minecraft mc){
        List<String> rows=new ArrayList<>();
        if(mc.theWorld==null)return rows;
        Scoreboard board=mc.theWorld.getScoreboard();
        ScoreObjective objective=board.getObjectiveInDisplaySlot(1);
        if(objective==null)return rows;
        // getSortedScores is ascending; the sidebar draws it backwards, so reverse to read top down.
        List<Score> scores=new ArrayList<>(board.getSortedScores(objective));
        Collections.reverse(scores);
        for(Score score:scores){
            ScorePlayerTeam team=board.getPlayersTeam(score.getPlayerName());
            rows.add((team==null?"":team.getColorPrefix())+score.getPlayerName()+(team==null?"":team.getColorSuffix()));
        }
        return rows;
    }

    /** PGM's tab-list scoreboard mode writes the clock into the tab header/footer, not the sidebar. */
    private static String footerTime(){
        Minecraft mc=MinecraftAccess.get();
        if(mc.ingameGUI==null||footerBroken)return null;
        try{
            Object tab=mc.ingameGUI.getTabList();
            if(footerField==null)footerField=ReflectionHelper.findField(tab.getClass(),"footer","field_175255_h");
            Object footer=footerField.get(tab);
            if(!(footer instanceof IChatComponent))return null;
            String text=ChatRules.plain(((IChatComponent)footer).getFormattedText()).trim();
            return clock(text)?text:null;
        }catch(Exception missingField){
            // Not this Minecraft build; the other clock sources still work, so stop retrying.
            footerBroken=true;footerField=null;
            return null;
        }
    }

    /** A real account name: PGM's tab-sorting entries are "|xx-uuid" and carry colour codes. */
    static boolean realPlayer(String name){
        return name!=null&&!name.isEmpty()&&name.charAt(0)!='|'&&name.indexOf('\u00a7')<0;
    }

    /** The same text the tab list draws for this player, dead or not. */
    static String tabName(NetworkPlayerInfo info){
        return info.getDisplayName()!=null?info.getDisplayName().getFormattedText()
            :ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(),info.getGameProfile().getName());
    }

    /**
     * A dead player is greyed out by the server in the tab list (PGM does this), and that grey is
     * the window the head follows: it stays grey until the respawn recolours the name, even while
     * the player's body is already back in the world. The body is only a fallback for names with no
     * tab entry, where the scoreboard team colour is the same signal one step coarser.
     */
    static boolean dead(String name){
        Minecraft mc=MinecraftAccess.get();
        boolean self=mc.thePlayer!=null&&name!=null&&name.equalsIgnoreCase(mc.thePlayer.getName());
        // Your own body knows first: your entity is dropped while the death screen is up, and PGM
        // can keep your own tab name in the team colour until the respawn.
        if(self&&(!mc.thePlayer.isEntityAlive()||mc.thePlayer.getHealth()<=0F
            ||mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver||respawning(mc)))return true;
        NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(name);
        if(mc.getNetHandler()!=null&&name!=null)for(NetworkPlayerInfo entry:mc.getNetHandler().getPlayerInfoMap()){
            if(!entry.getGameProfile().getName().startsWith("|")||entry.getDisplayName()==null)continue;
            Boolean shown=greyPlayerName(entry.getDisplayName().getFormattedText(),name);
            if(shown!=null)return shown;
        }
        if(info!=null){
            String formatted=tabName(info);
            Boolean shown=greyPlayerName(formatted,name);
            return shown!=null?shown:grey(formatted);
        }
        if(self)return false;
        net.minecraft.entity.player.EntityPlayer player=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(name);
        if(player!=null)return !player.isEntityAlive()||player.getHealth()<=0F;
        return mc.theWorld!=null&&grey(teamPrefix(mc,name));
    }

    private static boolean respawning(Minecraft mc){
        if(mc.ingameGUI==null||titleFieldsBroken)return false;
        try{
            if(subtitleField==null)subtitleField=ReflectionHelper.findField(net.minecraft.client.gui.GuiIngame.class,"displayedSubTitle","field_175200_y");
            if(titleTimerField==null)titleTimerField=ReflectionHelper.findField(net.minecraft.client.gui.GuiIngame.class,"titlesTimer","field_175195_w");
            return titleTimerField.getInt(mc.ingameGUI)>0&&respawnSubtitle((String)subtitleField.get(mc.ingameGUI));
        }catch(Exception missingField){titleFieldsBroken=true;return false;}
    }

    static boolean respawnSubtitle(String text){
        if(text==null)return false;
        // shortcut: matches English PGM respawn text only; use translation keys if the server exposes them.
        String plain=ChatRules.plain(text).trim().toLowerCase(Locale.ROOT);
        return plain.startsWith("left click to respawn")||plain.startsWith("respawning in ")||plain.startsWith("you will respawn ");
    }

    private static String teamPrefix(Minecraft mc,String name){
        ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(name);
        return team==null?null:team.getColorPrefix();
    }

    /** The colour at the actual account name, not a rank prefix or a later suffix. */
    static Boolean greyPlayerName(String formatted,String name){
        if(formatted==null||name==null||name.isEmpty())return null;
        String plain=ChatRules.plain(formatted);
        Matcher match=Pattern.compile("(?i)(?<![A-Za-z0-9_])"+Pattern.quote(name)+"(?![A-Za-z0-9_])").matcher(plain);
        if(!match.find())return null;
        int visible=0;char colour='r';
        for(int i=0;i<formatted.length();i++){
            char c=formatted.charAt(i);
            if(c=='\u00a7'&&i+1<formatted.length()){
                char code=Character.toLowerCase(formatted.charAt(++i));
                if("0123456789abcdefr".indexOf(code)>=0)colour=code;
            }else{
                if(visible==match.start())return colour=='8';
                visible++;
            }
        }
        return null;
    }

    /**
     * Dark grey (§8) is the dead marker. PGM puts the colour after
     * decorations ("§7§lName"), so the last colour code in the string decides.
     */
    static boolean grey(String formatted){
        if(formatted==null)return false;
        Matcher m=COLOUR.matcher(formatted);
        String last=null;
        while(m.find())last=m.group(1);
        return "8".equals(last);
    }

    /** The player is watching, not playing: PGM's observer team, or plain spectator mode. */
    static boolean observing(Minecraft mc){
        if(mc.thePlayer==null)return false;
        if(mc.thePlayer.isSpectator())return true;
        if(mc.theWorld==null)return false;
        ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
        return team!=null&&OBSERVERS.matcher(ChatRules.plain(team.getTeamName())).matches();
    }

    /**
     * The boss bar is the clock the bar already shows, so hiding it loses nothing. The same goes
     * for the observer: when the bar is not shown there, the server's own timer must not be left
     * behind as the only clock on screen.
     */
    static boolean hidesBossBar(Minecraft mc){
        if(!UtilitySettings.teamBar)return false;
        if(mc.thePlayer==null||mc.gameSettings.hideGUI)return false;
        if(!UtilitySettings.teamBarHideBoss&&!(observing(mc)&&!UtilitySettings.teamBarObserver))return false;
        String boss=ChatRules.plain(BossStatus.bossName==null?"":BossStatus.bossName);
        return TIME.matcher(boss).find();
    }

    @SubscribeEvent public void bossBar(RenderGameOverlayEvent.Pre event){
        if(event.type!=RenderGameOverlayEvent.ElementType.BOSSHEALTH)return;
        UtilitySettings.load();
        if(hidesBossBar(MinecraftAccess.get()))event.setCanceled(true);
    }

    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(event.type!=RenderGameOverlayEvent.ElementType.CHAT)return;
        UtilitySettings.load();
        Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.teamBar||mc.thePlayer==null||mc.theWorld==null||mc.gameSettings.hideGUI)return;
        if(mc.currentScreen instanceof HudEditor)return; // the editor draws its own preview
        if(UtilitySettings.teamBarHideOnTab&&playerListVisible(mc))return;
        Bar bar;
        try{
            bar=bar(mc,false);
        }catch(RuntimeException broken){
            return; // a HUD element must never take the game down
        }
        if(bar==null)return;
        HudLayout.Rect box=placed(bar);
        ScaledResolution sr=new ScaledResolution(mc);
        int mx=Mouse.getX()*sr.getScaledWidth()/mc.displayWidth;
        int my=sr.getScaledHeight()-Mouse.getY()*sr.getScaledHeight()/mc.displayHeight-1;
        String name=Mouse.isGrabbed()?null:headAt(bar,mx-box.x,my-box.y);
        // Compact objectives have no room for names, so hovering one shows what it is.
        if(name==null&&!Mouse.isGrabbed())name=objectiveAt(bar,mx-box.x,my-box.y,mc.fontRendererObj);
        if(!java.util.Objects.equals(name,hovered)){hovered=name;hoverSince=System.nanoTime();}
        draw(mc,bar,box.x,box.y,name,hoverSince);
        if(name!=null){
            int w=mc.fontRendererObj.getStringWidth(name),tx=Math.max(0,Math.min(mx+8,sr.getScaledWidth()-w-6));
            int ty=Math.max(0,Math.min(my+8,sr.getScaledHeight()-14));
            Gui.drawRect(tx,ty,tx+w+6,ty+14,0xE0101010);
            mc.fontRendererObj.drawStringWithShadow(name,tx+3,ty+3,0xFFFFFFFF);
        }
    }

    static boolean playerListVisible(Minecraft mc){
        return mc.gameSettings.keyBindPlayerList.isKeyDown()&&(!mc.isIntegratedServerRunning()
            ||mc.thePlayer.sendQueue.getPlayerInfoMap().size()>1||mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0)!=null);
    }

    /**
     * The compact objective under a point in the bar, for the hover tooltip. Null when the pointer
     * is over a head, empty space, or nothing is drawn in compact mode.
     */
    static String objectiveAt(Bar bar,int x,int y,net.minecraft.client.gui.FontRenderer font){
        for(int side=0;side<2;side++){
            List<Objectives.Strip> lines=side==0?bar.leftStrips:bar.rightStrips;
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                String name=Objectives.at(one,x-bar.stripX(side,one),y-bar.stripY-i*STRIP_LINE,font);
                if(name!=null)return name;
            }
        }
        return null;
    }

    static String headAt(Bar bar,int x,int y){
        for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int r=0;r<rows.size();r++){
                List<String> members=rows.get(r);
                int top=r*(bar.band+ROW_GAP)+(bar.band-bar.head)/2;
                int start=side==0?bar.leftStart(members):bar.rightStart(members);
                for(int i=0;i<members.size();i++){
                    int left=start+i*bar.advance;
                    if(x>=left&&x<left+bar.head&&y>=top&&y<top+bar.head)return members.get(i);
                }
            }
        }
        return null;
    }

    /** The HUD editor's preview: real teams when a match is running, otherwise a placeholder row. */
    static void drawPreview(){
        Minecraft mc=MinecraftAccess.get();
        Bar bar=INSTANCE.bar(mc,true);
        HudLayout.Rect box=placed(bar);
        draw(mc,bar,box.x,box.y);
    }

    private static void draw(Minecraft mc,Bar bar,int x,int y){
        draw(mc,bar,x,y,null,0);
    }

    private static void draw(Minecraft mc,Bar bar,int x,int y,String hover,long since){
        int band=bar.band;
        int hx=0,hy=0;boolean found=false;
        int lineW=bar.line>0?bar.line-LINE_GAP:0;
        // One tessellator pass for the whole bar: a head is up to 64 quads, and drawing each one
        // as its own rect was thousands of draw calls per frame in a full match.
        Tessellator tessellator=Tessellator.getInstance();
        WorldRenderer world=tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.color(1F,1F,1F,1F);
        world.begin(7,DefaultVertexFormats.POSITION_COLOR);
        for(int r=0;r<bar.left.size();r++){
            List<String> members=bar.left.get(r);
            int top=y+r*(band+ROW_GAP)+(band-bar.head)/2;
            // The line sits at the row's own outer end, so it stays attached to its heads.
            if(lineW>0)quad(world,x+bar.leftLine(members),top,x+bar.leftLine(members)+lineW,top+bar.head,teamColour(mc,members));
            int start=x+bar.leftStart(members);
            for(int i=0;i<members.size();i++)if(members.get(i).equals(hover)){hx=start+i*bar.advance;hy=top;found=true;}else head(world,members.get(i),start+i*bar.advance,top,bar.head);
        }
        for(int r=0;r<bar.right.size();r++){
            List<String> members=bar.right.get(r);
            int top=y+r*(band+ROW_GAP)+(band-bar.head)/2;
            // Rows are flush with the clock and grow outward, so both columns read from the middle.
            int start=x+bar.rightStart(members);
            if(lineW>0)quad(world,x+bar.rightLine(members),top,x+bar.rightLine(members)+lineW,top+bar.head,teamColour(mc,members));
            for(int i=0;i<members.size();i++)if(members.get(i).equals(hover)){hx=start+i*bar.advance;hy=top;found=true;}else head(world,members.get(i),start+i*bar.advance,top,bar.head);
        }
        if(found){
            int extra=Math.round(Math.max(2,bar.head*.25F)*Math.min(1F,Math.max(0F,(System.nanoTime()-since)/150_000_000F)));
            head(world,hover,hx-extra/2,hy-extra/2,bar.head+extra);
        }
        // The objective squares share the bar's quad pass; the text parts follow once the
        // tessellator is done with the texture unit. Each side stacks its own lines beside the clock.
        for(int side=0;side<2;side++){
            List<Objectives.Strip> lines=side==0?bar.leftStrips:bar.rightStrips;
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                int px=x+bar.stripX(side,one),py=y+bar.stripY+i*STRIP_LINE;
                for(Objectives.Part part:one.parts){
                    px+=part.space;
                    if(part.text==null)wool(world,px,py,part.colour,part.state);
                    px+=part.width(mc.fontRendererObj);
                }
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        if(bar.textW>0){
            // Sits on the first head row, not the middle of the stack, so extra teams stack below it.
            mc.fontRendererObj.drawStringWithShadow(bar.label,x+bar.textX,y+(band-8)/2,colour(bar.timer));
        }
        for(int side=0;side<2;side++){
            List<Objectives.Strip> lines=side==0?bar.leftStrips:bar.rightStrips;
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                int px=x+bar.stripX(side,one),py=y+bar.stripY+i*STRIP_LINE;
                for(Objectives.Part part:one.parts){
                    px+=part.space;
                    if(part.text!=null)mc.fontRendererObj.drawStringWithShadow(part.text,px,py,part.colour);
                    px+=part.width(mc.fontRendererObj);
                }
            }
        }
    }

    /** A wool state square: hollow untouched, checkerboard picked up, solid placed. */
    private static void wool(WorldRenderer world,int x,int y,int argb,int state){
        int size=Objectives.ICON;
        if(state==2){quad(world,x,y,x+size,y+size,argb);return;}
        if(state==1){
            for(int row=0;row<size;row++)for(int col=0;col<size;col++)
                if(((row+col)&1)==0)quad(world,x+col,y+row,x+col+1,y+row+1,argb);
            return;
        }
        quad(world,x,y,x+size,y+1,argb);quad(world,x,y+size-1,x+size,y+size,argb);
        quad(world,x,y,x+1,y+size,argb);quad(world,x+size-1,y,x+size,y+size,argb);
    }

    /** One flat quad into the shared buffer, coloured per vertex. */
    private static void quad(WorldRenderer world,int x1,int y1,int x2,int y2,int argb){
        float a=(argb>>>24&255)/255F,r=(argb>>16&255)/255F,g=(argb>>8&255)/255F,b=(argb&255)/255F;
        world.pos(x1,y2,0).color(r,g,b,a).endVertex();
        world.pos(x2,y2,0).color(r,g,b,a).endVertex();
        world.pos(x2,y1,0).color(r,g,b,a).endVertex();
        world.pos(x1,y1,0).color(r,g,b,a).endVertex();
    }

    /**
     * Clock colour: red for the match-start count-in, red for the last 25 seconds of a time limit,
     * yellow for a limit that still has time left, white for a count-up.
     */
    static int colour(Timer timer){
        if(timer==null)return WHITE;
        if(timer.starting)return RED;
        if(timer.countdown)return timer.seconds<=LOW_TIME?RED:YELLOW;
        return WHITE;
    }

    /** A row's team colour, taken from the scoreboard prefix PGM puts on the team. */
    static int teamColour(Minecraft mc,List<String> members){
        if(members.isEmpty()||mc.theWorld==null)return FALLBACK;
        ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(members.get(0));
        if(team==null)return FALLBACK;
        Matcher m=COLOUR.matcher(team.getColorPrefix()==null?"":team.getColorPrefix());
        String last=null;
        while(m.find())last=m.group(1);
        return last==null?FALLBACK:COLOURS[Character.digit(last.charAt(0),16)];
    }

    /** The ARGB of a chat colour code; white when the code is not a colour. */
    static int chatColour(char code){
        int digit=Character.digit(Character.toLowerCase(code),16);
        return digit>=0&&digit<COLOURS.length?COLOURS[digit]:WHITE;
    }

    /**
     * Dead players grey and fade until respawn, including the local player.
     */
    private static void head(WorldRenderer world,String name,int x,int y,int size){
        int[] pixels=KillFeed.face(name);
        boolean dead=deadNow(name);
        if(pixels.length!=64){quad(world,x,y,x+size,y+size,headColour(dead?DEAD_FALLBACK:FALLBACK,dead,UtilitySettings.teamBarDeadOpacity));return;}
        if(size>=8){
            for(int row=0;row<8;row++)for(int col=0;col<8;col++){
                int pixel=pixels[row*8+col];if((pixel>>>24)<16)continue;
                quad(world,x+col*size/8,y+row*size/8,x+(col+1)*size/8,y+(row+1)*size/8,headColour(pixel,dead,UtilitySettings.teamBarDeadOpacity));
            }
        }else{
            // Below eight pixels the texels are invisible, so average the face into one readable swatch.
            long red=0,green=0,blue=0;int seen=0;
            for(int pixel:pixels){
                if((pixel>>>24)<16)continue;
                red+=pixel>>16&255;green+=pixel>>8&255;blue+=pixel&255;seen++;
            }
            if(seen==0)quad(world,x,y,x+size,y+size,headColour(dead?DEAD_FALLBACK:FALLBACK,dead,UtilitySettings.teamBarDeadOpacity));
            else{
                int average=0xFF000000|(int)(red/seen)<<16|(int)(green/seen)<<8|(int)(blue/seen);
                quad(world,x,y,x+size,y+size,headColour(average,dead,UtilitySettings.teamBarDeadOpacity));
            }
        }
    }

    static int headColour(int pixel,boolean dead,int opacity){
        if(!dead)return pixel;
        return (greyed(pixel)&0xFFFFFF)|(((pixel>>>24)*opacity/100)<<24);
    }

    /** The draw-time dead flag: cached for a quarter second so a frame does not re-read the tab list. */
    private static boolean deadNow(String name){
        long now=System.nanoTime();
        if(now-deadAt>DEAD_TTL){deadCache.clear();deadAt=now;}
        Boolean known=deadCache.get(name);
        if(known==null){known=dead(name);deadCache.put(name,known);}
        return known;
    }

    /** Colour removed and dimmed: what a greyed tab-list name does to a skin. */
    static int greyed(int pixel){
        int grey=(int)((pixel>>16&255)*.3+(pixel>>8&255)*.59+(pixel&255)*.11);
        grey=grey*3/4;
        return pixel&0xFF000000|grey<<16|grey<<8|grey;
    }
}
