package otl.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
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
    private String actionBar;
    private long actionBarAt;
    private Object world;
    /** Last server clock value and when it arrived; the display ticks on from there. */
    private int baseSeconds=-1;
    private long baseNanos;
    private int direction;              // -1 countdown, +1 count up, 0 a static label
    private boolean ended;
    /** True while the middle slot counts the players in instead of showing the match clock. */
    private boolean preMatch=false;
    /** True while the clock is a local count-up, not a server value. */
    private boolean local;
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
    /**
     * How long a source may report the same value before it is treated as not counting. PGM
     * updates its clock about once a second, so a live source changes well inside this window.
     */
    static final long STALE=2_000_000_000L;
    private static Field footerField;
    private static boolean footerBroken;
    /** Last complete read of the teams, used when a team packet lands mid-frame. */
    private static List<Side> lastSides=new ArrayList<>();
    /** A dead player can drop out of the team for the respawn delay; keep the head that long. */
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
        final int head,advance,column,rightX,textX,textW,width,height,band;
        final Timer timer;
        final String label;
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label){this(left,right,head,textW,timer,label,0);}
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line){
            this.left=left;this.right=right;this.head=head;this.advance=advance(head);this.timer=timer;
            this.line=line;
            // Both columns take the widest row, so the clock keeps the exact centre of the bar
            // and does not shift when players join or leave one side.
            int w=0;
            for(List<String> members:left)w=Math.max(w,row(members,head,advance));
            for(List<String> members:right)w=Math.max(w,row(members,head,advance));
            // The colour line sits on the outer edge, so the widest row plus the line is the column.
            this.column=w+line;
            this.textW=textW;
            this.textX=column+(textW>0?GAP:0);
            this.rightX=textX+textW+(textW>0?GAP:0);
            this.width=rightX+column;
            // Each row is drawn in a band at least nine pixels tall so the clock still fits.
            this.band=Math.max(head,9);
            int rows=Math.max(left.size(),right.size());
            this.height=Math.max(band,rows*band+(rows-1)*ROW_GAP);
            this.label=label;
        }
        /**
         * Where a right-column row starts: flush against the clock, growing outward to the right,
         * so an empty tail always falls on the bar's outside and never next to the clock.
         */
        int rightStart(List<String> members){return rightX;}
        /** Where a left-column row starts: flush against the clock, growing outward to the left. */
        int leftStart(List<String> members){return column-row(members,head,advance);}
        /** The team-colour line for a left row: at the row's outer end, not stranded in the gap. */
        int leftLine(List<String> members){return column-line-row(members,head,advance);}
        /** The team-colour line for a right row: just past the row's outer end. */
        int rightLine(List<String> members){return rightX+row(members,head,advance)+LINE_GAP;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void chat(ClientChatReceivedEvent event){
        if(event.type==2){ // action bar only; chat lines carry other players' times
            actionBar=ChatRules.plain(event.message.getUnformattedText()).trim();
            actionBarAt=System.nanoTime();
            return;
        }
        for(String line:ChatRules.plain(event.message.getUnformattedText()).split("[\\r\\n]+")){
            String text=line.trim();
            if(MatchEnd.is(text)){ended=true;frozenSeconds=-1;return;}
            // "Playing <map>" is the map change itself: the last match's clock must not linger.
            // shortcut: a player typing "Playing x" in chat also resets it; the next server clock line repairs it
            if(text.startsWith("Playing ")){reset(System.nanoTime());return;}
            if(MatchEnd.kickoff(text)){begin(System.nanoTime());return;}
        }
    }

    /** A new map has loaded: nothing has carried a clock yet, so the middle starts again at 0:00. */
    private void reset(long now){
        ended=false;frozenSeconds=-1;local=false;preMatch=true;baseSeconds=0;baseNanos=now;direction=0;clockSource=0;
        // The old map's clock lines must not make a source look like it is still counting.
        java.util.Arrays.fill(lastLine,null);java.util.Arrays.fill(changedAt,0L);java.util.Arrays.fill(changes,0);java.util.Arrays.fill(lastSeconds,-1);java.util.Arrays.fill(hint,0);directionHint=0;
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)clear();}

    private void clear(){actionBar=null;baseSeconds=-1;direction=0;ended=false;frozenSeconds=-1;world=null;preMatch=false;local=false;clockSource=0;java.util.Arrays.fill(lastLine,null);java.util.Arrays.fill(changedAt,0L);java.util.Arrays.fill(changes,0);java.util.Arrays.fill(lastSeconds,-1);java.util.Arrays.fill(hint,0);directionHint=0;lastMembers.clear();goneAt.clear();tabSeen.clear();lastSides=new ArrayList<>();}

    private void syncWorld(Minecraft mc){
        if(world!=mc.theWorld)clear();
        world=mc.theWorld;
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
            if(!members.isEmpty())found.add(new Side(team.getTeamName(),members));
        }
        // Fixed order by team name, never by player count: a team keeps its column for the whole
        // match, so the bar cannot reshuffle itself when somebody joins or leaves. Observers and
        // PGM's tab-sorting teams are already dropped above, so this order is the match's teams.
        found.sort((a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.name,b.name));
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
            if(gone!=null&&now-gone<KEEP_DEAD)current.add(name);
        }
        lastMembers.put(team,new ArrayList<>(current));
        return current;
    }

    /** Ten heads is one row; a bigger team wraps onto the rows below instead of shrinking its heads. */
    static final int MAX_PER_ROW=10;
    /** Head size: full size, since a row never holds more than MAX_PER_ROW heads. */
    static int headSize(int shown){return BASE_HEAD;}
    private static int advance(int head){return head+(head>=8?2:0);}
    private static int row(List<String> members,int head,int advance){return members.isEmpty()?0:(members.size()-1)*advance+head;}

    /** Sizes the bar for the current teams and options; null in game while nothing carries a clock. */
    Bar bar(Minecraft mc,boolean preview){
        if(!preview&&!UtilitySettings.teamBarObserver&&observing(mc))return null;
        int shown=Math.max(1,Math.min(75,UtilitySettings.teamBarPlayers));
        List<List<String>> left=new ArrayList<>(),right=new ArrayList<>();
        List<Side> sides=sides(mc);
        // No team has more than one player: that is FFA, where the colour belongs to the player.
        boolean ffa=isFfa(sides);
        Timer timer=timer(mc);
        // Heads are a match thing: the hub keeps scoreboard teams and runs its own countdown, so
        // only a match clock earns a bar. The pre-match count-in draws the clock alone, in red,
        // and the heads come back when the match really starts.
        // shortcut: a no-limit map joined mid-match shows no heads until the kickoff line
        if(timer==null&&!preview)return null;
        boolean counting=timer!=null&&timer.starting;
        if(counting&&!preview){
            // Counting in: the middle only, so nothing suggests the teams are set yet.
        }else if(sides.isEmpty()){
            // No teams yet (or you are watching from the observer team): keep the clock alone and
            // centred instead of dropping the bar, so it never sits off centre or disappears.
            if(preview){
                int fake=Math.max(1,Math.min(shown,8));
                left.add(placeholders(fake));right.add(placeholders(fake));
            }
        }else if(ffa){
            // FFA has no teams: PGM puts every player on their own one-member team so each name
            // gets its own colour. Treat them as one pool of players - rows alternate between the
            // columns just to keep the stack balanced - and draw no team-colour line, since a line
            // per player would only repeat the heads' own colours.
            List<String> pool=new ArrayList<>();
            for(Side side:sides)pool.addAll(side.members);
            if(mc.thePlayer!=null&&pool.remove(mc.thePlayer.getName()))pool.add(0,mc.thePlayer.getName());
            int count=Math.min(shown,pool.size());
            for(int from=0;from<count;from+=MAX_PER_ROW){
                List<String> members=new ArrayList<>(pool.subList(from,Math.min(from+MAX_PER_ROW,count)));
                (left.size()<=right.size()?left:right).add(members);
            }
        }else{
            // One block of rows per team, alternating columns. A team keeps the column its size
            // gives it, so joining a team never drags the block across the bar. Ten heads fill a
            // row; the rest wrap onto the rows below rather than shrinking. A right-column block
            // hugs the clock, so the local head moves to the outer end of its last row.
            for(int i=0;i<sides.size();i++){
                List<String> members=new ArrayList<>(sides.get(i).members.subList(0,Math.min(shown,sides.get(i).members.size())));
                if(i%2==1&&mc.thePlayer!=null&&members.remove(mc.thePlayer.getName()))members.add(mc.thePlayer.getName());
                List<List<String>> column=i%2==0?left:right;
                for(int from=0;from<members.size();from+=MAX_PER_ROW)
                    column.add(new ArrayList<>(members.subList(from,Math.min(from+MAX_PER_ROW,members.size()))));
            }
        }
        String label=timer!=null?timer.label:preview?"10:00":"";
        int textW=label.isEmpty()?0:mc.fontRendererObj.getStringWidth(label);
        int scale=Math.max(50,Math.min(150,UtilitySettings.teamBarSize));
        int head=Math.max(MIN_HEAD,headSize(biggest(left,right))*scale/100);
        ScaledResolution scaled=new ScaledResolution(mc);
        int screen=scaled.getScaledWidth(),screenH=scaled.getScaledHeight();
        // Never spill off the screen, however many teams and players are shown: the rows stack
        // downward, so a big team count shrinks the heads until the whole stack fits the height.
        while(head>MIN_HEAD){
            Bar fit=new Bar(left,right,head,textW,timer,label,line(head,ffa));
            if(fit.width<=screen-8&&fit.height<=screenH-8)break;
            head--;
        }
        return new Bar(left,right,head,textW,timer,label,line(head,ffa));
    }

    /**
     * FFA: no side has more than one player, because PGM gives every player their own team (and
     * their own colour) when there is no team to play on. A team map always has real sides.
     */
    static boolean isFfa(List<Side> sides){
        if(sides.isEmpty())return false;
        for(Side side:sides)if(side.members.size()!=1)return false;
        return true;
    }

    /** Width of the team-colour bar plus the gap to the heads; 0 when off or in FFA. */
    private static int line(int head,boolean ffa){
        return !ffa&&UtilitySettings.teamBarTeamColor?Math.max(2,head/5)+LINE_GAP:0;
    }

    private static int biggest(List<List<String>> left,List<List<String>> right){
        int most=1;
        for(List<String> members:left)most=Math.max(most,members.size());
        for(List<String> members:right)most=Math.max(most,members.size());
        return most;
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
                long elapsed=Math.max(0,(now-baseNanos)/1_000_000_000L);
                frozenSeconds=Math.max(0,baseSeconds+(int)elapsed*direction);
            }
            int stopped=frozenSeconds>=0?frozenSeconds:baseSeconds;
            return new Timer(format(stopped),stopped,direction<0,preMatch);
        }
        int seconds=baseSeconds;
        if(direction!=0){
            long elapsed=Math.max(0,(now-baseNanos)/1_000_000_000L);
            seconds=Math.max(0,baseSeconds+(int)elapsed*direction);
        }
        return new Timer(format(seconds),seconds,direction<0,preMatch);
    }

    /** A match has started with no server clock: count up from 0:00 in white until one arrives. */
    private void begin(long now){
        ended=false;frozenSeconds=-1;preMatch=false;local=true;
        baseSeconds=0;baseNanos=now;direction=1;
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
        boolean countdown=lower.contains("remaining")||lower.contains("left")||lower.contains("limit");
        // An idle PGM server writes a bare "0:00" into its scoreboard; that is not a match clock.
        if(baseSeconds<0&&raw==0&&!countdown)return;
        boolean wasLocal=local;local=false;
        if(baseSeconds<0||Math.abs(raw-baseSeconds)>5){
            // A new clock: the label decides, but a source seen ticking keeps its own direction,
            // and a confirmed local count-up keeps counting up.
            baseSeconds=raw;baseNanos=now;direction=countdown?-1:directionHint!=0?directionHint:wasLocal?1:0;
        }else if(raw!=baseSeconds){
            // An observed drop is a countdown even when the label does not say so.
            direction=raw<baseSeconds||countdown?-1:1;
            baseSeconds=raw;baseNanos=now;
        }
    }

    private static String format(int seconds){
        seconds=Math.max(0,seconds);
        return (seconds/60)+":"+(seconds%60<10?"0":"")+(seconds%60);
    }

    /** A line that carries a clock: either m:ss or the pre-match "starting in N seconds". */
    private static boolean clock(String text){
        return text!=null&&(TIME.matcher(text).find()||STARTING.matcher(text).find());
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
            if(value>=0&&lastSeconds[source]>=0&&value!=lastSeconds[source]
                &&Math.abs(value-lastSeconds[source])<=2)
                hint[source]=value<lastSeconds[source]?-1:1;
            lastSeconds[source]=value;
            if(!lines[source].equals(lastLine[source])){
                // The first sight is only a baseline: it says a clock exists, not that it counts.
                if(lastLine[source]!=null)changes[source]++;
                lastLine[source]=lines[source];
                changedAt[source]=now;
            }
        }
        int chosen=0;
        // Hold the lock while its source is still ticking, so two live sources cannot fight.
        if(clockSource!=0&&lines[clockSource]!=null&&changes[clockSource]>0&&now-changedAt[clockSource]<STALE)
            chosen=clockSource;
        // Otherwise follow the freshest source that has actually ticked.
        if(chosen==0){
            long freshest=Long.MIN_VALUE;
            for(int source=1;source<=4;source++){
                if(lines[source]==null||changes[source]==0||now-changedAt[source]>=STALE)continue;
                if(changedAt[source]>freshest){freshest=changedAt[source];chosen=source;}
            }
        }
        // Nothing ticked recently: a paused clock keeps its own number, and a line that never
        // ticked is still shown when it is all there is, so the bar is never empty on a join.
        if(chosen==0&&clockSource!=0&&lines[clockSource]!=null)chosen=clockSource;
        if(chosen==0)for(int source=1;source<=4;source++)if(lines[source]!=null){chosen=source;break;}
        clockSource=chosen;
        if(chosen==0)return null;
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
        if(mc.theWorld==null)return null;
        Scoreboard board=mc.theWorld.getScoreboard();
        ScoreObjective objective=board.getObjectiveInDisplaySlot(1);
        if(objective==null)return null;
        for(Score score:board.getSortedScores(objective)){
            ScorePlayerTeam team=board.getPlayersTeam(score.getPlayerName());
            String line=(team==null?"":team.getColorPrefix())+score.getPlayerName()+(team==null?"":team.getColorSuffix());
            String plain=ChatRules.plain(line).trim();
            if(clock(plain))return plain;
        }
        return null;
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
            ||mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver))return true;
        NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(name);
        if(info!=null)return grey(tabName(info));
        if(self)return false;
        net.minecraft.entity.player.EntityPlayer player=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(name);
        if(player!=null)return !player.isEntityAlive()||player.getHealth()<=0F;
        return mc.theWorld!=null&&grey(teamPrefix(mc,name));
    }

    private static String teamPrefix(Minecraft mc,String name){
        ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(name);
        return team==null?null:team.getColorPrefix();
    }

    /**
     * Grey (§7) and dark grey (§8) are the dead marker. PGM puts the colour after
     * decorations ("§7§lName"), so the last colour code in the string decides.
     */
    static boolean grey(String formatted){
        if(formatted==null)return false;
        Matcher m=COLOUR.matcher(formatted);
        String last=null;
        while(m.find())last=m.group(1);
        return "7".equals(last)||"8".equals(last);
    }

    /** The player is watching, not playing: PGM's observer team, or plain spectator mode. */
    static boolean observing(Minecraft mc){
        if(mc.thePlayer==null)return false;
        if(mc.thePlayer.isSpectator())return true;
        if(mc.theWorld==null)return false;
        ScorePlayerTeam team=mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
        return team!=null&&OBSERVERS.matcher(ChatRules.plain(team.getTeamName())).matches();
    }

    /** The boss bar is the clock the bar already shows, so hiding it loses nothing. */
    static boolean hidesBossBar(Minecraft mc){
        if(!UtilitySettings.teamBar||!UtilitySettings.teamBarHideBoss)return false;
        if(mc.thePlayer==null||mc.gameSettings.hideGUI)return false;
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
        Bar bar;
        try{
            bar=bar(mc,false);
        }catch(RuntimeException broken){
            return; // a HUD element must never take the game down
        }
        if(bar==null)return;
        HudLayout.Rect box=placed(bar);
        draw(mc,bar,box.x,box.y);
    }

    /** The HUD editor's preview: real teams when a match is running, otherwise a placeholder row. */
    static void drawPreview(){
        Minecraft mc=MinecraftAccess.get();
        Bar bar=INSTANCE.bar(mc,true);
        HudLayout.Rect box=placed(bar);
        draw(mc,bar,box.x,box.y);
    }

    private static void draw(Minecraft mc,Bar bar,int x,int y){
        int band=bar.band;
        int lineW=bar.line>0?bar.line-LINE_GAP:0;
        for(int r=0;r<bar.left.size();r++){
            List<String> members=bar.left.get(r);
            int top=y+r*(band+ROW_GAP)+(band-bar.head)/2;
            // The line sits at the row's own outer end, so it stays attached to its heads.
            if(lineW>0)Gui.drawRect(x+bar.leftLine(members),top,x+bar.leftLine(members)+lineW,top+bar.head,teamColour(mc,members));
            int start=x+bar.leftStart(members);
            for(int i=0;i<members.size();i++)head(members.get(i),start+i*bar.advance,top,bar.head);
        }
        for(int r=0;r<bar.right.size();r++){
            List<String> members=bar.right.get(r);
            int top=y+r*(band+ROW_GAP)+(band-bar.head)/2;
            // Rows are flush with the clock and grow outward, so both columns read from the middle.
            int start=x+bar.rightStart(members);
            if(lineW>0)Gui.drawRect(x+bar.rightLine(members),top,x+bar.rightLine(members)+lineW,top+bar.head,teamColour(mc,members));
            for(int i=0;i<members.size();i++)head(members.get(i),start+i*bar.advance,top,bar.head);
        }
        if(bar.textW>0){
            // Sits on the first head row, not the middle of the stack, so extra teams stack below it.
            mc.fontRendererObj.drawStringWithShadow(bar.label,x+bar.textX,y+(band-8)/2,colour(bar.timer));
        }
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

    /**
     * Dead players lose their colours, exactly like the grey tab-list name, so the head is
     * visibly faded and comes back on respawn. The texels stay, so the skin is still readable.
     */
    private static void head(String name,int x,int y,int size){
        int[] pixels=KillFeed.face(name);
        boolean dead=dead(name);
        if(pixels.length!=64){Gui.drawRect(x,y,x+size,y+size,dead?DEAD_FALLBACK:FALLBACK);return;}
        if(size>=8){
            for(int row=0;row<8;row++)for(int col=0;col<8;col++){
                int pixel=pixels[row*8+col];if((pixel>>>24)<16)continue;
                Gui.drawRect(x+col*size/8,y+row*size/8,x+(col+1)*size/8,y+(row+1)*size/8,dead?greyed(pixel):pixel);
            }
        }else{
            // Below eight pixels the texels are invisible, so average the face into one readable swatch.
            long red=0,green=0,blue=0;int seen=0;
            for(int pixel:pixels){
                if((pixel>>>24)<16)continue;
                red+=pixel>>16&255;green+=pixel>>8&255;blue+=pixel&255;seen++;
            }
            if(seen==0)Gui.drawRect(x,y,x+size,y+size,dead?DEAD_FALLBACK:FALLBACK);
            else{
                int average=0xFF000000|(int)(red/seen)<<16|(int)(green/seen)<<8|(int)(blue/seen);
                Gui.drawRect(x,y,x+size,y+size,dead?greyed(average):average);
            }
        }
    }

    /** Colour removed and dimmed: what a greyed tab-list name does to a skin. */
    static int greyed(int pixel){
        int grey=(int)((pixel>>16&255)*.3+(pixel>>8&255)*.59+(pixel&255)*.11);
        grey=grey*3/4;
        return pixel&0xFF000000|grey<<16|grey<<8|grey;
    }
}
