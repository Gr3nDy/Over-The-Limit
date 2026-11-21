package otl.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
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
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
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
    /** A team count row: the number, then the team's colour bar underneath. */
    static final int COUNT_BAND=12,COUNT_LINE=2;
    /** Teams at which heads no longer fit under the clock, so the bar counts them instead. */
    static final int COUNT_TEAMS=8;
    /** The sixteen chat colour codes as RGB, for the team-colour line. */
    private static final int[] COLOURS={0xFF000000,0xFF0000AA,0xFF00AA00,0xFF00AAAA,0xFFAA0000,0xFFAA00AA,0xFFFFAA00,0xFFAAAAAA,
        0xFF555555,0xFF5555FF,0xFF55FF55,0xFF55FFFF,0xFFFF5555,0xFFFF55FF,0xFFFFFF55,0xFFFFFFFF};
    static final int WHITE=0xFFFFFFFF,GREEN=0xFF55FF55,GOLD=0xFFFFAA00,YELLOW=0xFFFFD34F,RED=0xFFFF5555,DARK_RED=0xFFAA0000,AQUA=0xFF55FFFF;
    /** One bold line between the bar's top edge and the heads, for the map name or gamemode. */
    static final int HEADER_H=14;
    /** A time limit turns red in its last stretch, so the end of the match reads at a glance. */
    static final int LOW_TIME=25;
    private static final int FALLBACK=0xFF8A8A8A,DEAD_FALLBACK=0xFF5A5A5A;
    private static final Pattern TIME=Pattern.compile("(\\d{1,3}):(\\d{2})");
    /** PGM counts the players in before the match: "Match starting in 10 seconds". */
    private static final Pattern STARTING=Pattern.compile("(?i)(?:match\\s+)?(?:starting|starts|begins)\\s+in\\D{0,10}(\\d{1,3})");
    private static final Pattern COLOUR=Pattern.compile("\u00a7([0-9a-fA-F])");
    /** PGM puts observers in their own team; it must never become one of the two sides. */
    private static final Pattern OBSERVERS=Pattern.compile("(?i).*\\b(observers?|spectators?|spec)\\b.*");
    /** A welcome block's heading: the map name between runs of dashes or spaces. */
    private static final Pattern HEADING=Pattern.compile("^\\W{2,}\\s*(\\S(?:.*?\\S)?)\\s*\\W{2,}$");
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
    /** The current map, from the server's "Playing <map> by <author>" line. */
    private String mapName="",mapAuthor="";
    private Object world;
    /** Last server clock value and when it arrived; the display ticks on from there. */
    private int baseSeconds=-1;
    private long baseNanos;
    private int direction;              // -1 countdown, +1 count up, 0 a static label
    private boolean ended;
    /** This mod is the one holding the vanilla sidebar off, so it is the one that puts it back. */
    private boolean scoreboardHidden;
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
    private boolean mapFreeForAll;
    private final Objectives.PointTracker controlPoints=new Objectives.PointTracker();
    final Objectives.ScoreTracker symbolicScores=new Objectives.ScoreTracker();
    private HeadMenu headMenu;

    static final class HeadMenu {
        static final int ROW=18;
        /** The player's own name above the actions, so a menu opened on the wrong row is obvious. */
        static final int TITLE=14;
        static final String[] LABELS={"View Profile","Teleport to player","Add Friend","Message player"};
        static final int HEIGHT=TITLE+ROW*LABELS.length;
        static int width(Minecraft mc,String player){
            int w=Math.min(120,mc.fontRendererObj.getStringWidth(player==null?"":player));
            for(String label:LABELS)w=Math.max(w,mc.fontRendererObj.getStringWidth(label));
            return w+12;
        }
        final String player;
        final int x,y,width;
        HeadMenu(String player,int headX,int headBottom,int headWidth,int width,int screenWidth,int screenHeight){
            this.player=player;
            this.width=Math.min(width,Math.max(1,screenWidth-8));
            this.x=Math.max(4,Math.min(headX+headWidth/2-this.width/2,screenWidth-this.width-4));
            this.y=Math.max(4,Math.min(headBottom+3,screenHeight-HEIGHT-4));
        }
        int actionAt(int mx,int my){
            return mx>=x&&mx<x+width&&my>=y+TITLE&&my<y+HEIGHT?(my-y-TITLE)/ROW:-1;
        }
    }

    static String profileUrl(String player){return validMenuPlayer(player)?"https://oc.tc/"+player:null;}
    static String teleportCommand(String player){return validMenuPlayer(player)?"/tp "+player:null;}
    static String friendCommand(String player){return validMenuPlayer(player)?"/friend add "+player:null;}
    private static boolean validMenuPlayer(String player){return player!=null&&player.matches("[A-Za-z0-9_]{1,16}");}
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
        /** Pre-match clock/countdown: shown in gold, before the real clock exists. */
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
        /** Team counts instead of heads: one cell per team, and how wide that cell is. */
        final boolean counts;
        final int cell;
        /** Compact markers beside the clock, or named rows below the heads; empty when none. */
        final List<Objectives.Strip> leftStrips,rightStrips;
        final List<List<Objectives.Strip>> pointStrips;
        final int pointY,neutralPointY;
        final boolean compact;
        final int leftStripX,rightStripX,leftStripW;
        /** Top of the objective stack and its total height, both inside the bar. */
        final int stripY,stripH;
        final int stripLine,pointLine,neutralLine;
        /** Row tops per column, and where each objective line is drawn. */
        final int[] leftRowTops,rightRowTops,leftStripTops,rightStripTops;
        /** The team each head row belongs to; null, or shorter than the rows, means unknown. */
        private final List<String> leftOwners,rightOwners;
        final Timer timer;
        final String label;
        final boolean ffa;
        final Map<String,String> ffaScores;
        /** Bold line above the heads, with its own hover text; null when the option is off. */
        final Header header;
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label){this(left,right,head,textW,timer,label,0,null,null);}
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line){this(left,right,head,textW,timer,label,line,null,null);}
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,true);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,compact,null);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact,List<List<Objectives.Strip>> points){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,compact,points,null,0);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact,List<List<Objectives.Strip>> points,
            Header header,int headerW){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,compact,points,header,headerW,null,null);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact,List<List<Objectives.Strip>> points,
            Header header,int headerW,List<String> leftOwners,List<String> rightOwners){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,compact,points,header,headerW,leftOwners,rightOwners,false,0);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact,List<List<Objectives.Strip>> points,
            Header header,int headerW,List<String> leftOwners,List<String> rightOwners,boolean counts,int cell){
            this(left,right,head,textW,timer,label,line,leftStrips,rightStrips,compact,points,header,headerW,leftOwners,rightOwners,counts,cell,COUNT_BAND);
        }
        Bar(List<List<String>> left,List<List<String>> right,int head,int textW,Timer timer,String label,int line,
            List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips,boolean compact,List<List<Objectives.Strip>> points,
            Header header,int headerW,List<String> leftOwners,List<String> rightOwners,boolean counts,int cell,int countBand){
            this.ffa=false;this.ffaScores=Collections.emptyMap();
            this.left=left;this.right=right;this.head=head;this.advance=advance(head);this.timer=timer;
            this.compact=compact;this.counts=counts;this.cell=cell;
            this.line=line;
            this.header=header;
            this.leftOwners=leftOwners;this.rightOwners=rightOwners;
            this.leftStrips=leftStrips==null?Collections.<Objectives.Strip>emptyList():leftStrips;
            this.rightStrips=rightStrips==null?Collections.<Objectives.Strip>emptyList():rightStrips;
            this.pointStrips=points==null?Arrays.asList(Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList()):points;
            boolean vertical=UtilitySettings.teamBarScoreStyle==2&&(hasScoreBars(this.leftStrips)||hasScoreBars(this.rightStrips)
                ||hasScoreBars(this.pointStrips.get(0))||hasScoreBars(this.pointStrips.get(1))||hasScoreBars(this.pointStrips.get(2)));
            boolean stacked=hasStackedScores(this.leftStrips)||hasStackedScores(this.rightStrips)
                ||hasStackedScores(this.pointStrips.get(0))||hasStackedScores(this.pointStrips.get(1))||hasStackedScores(this.pointStrips.get(2));
            int contentHeight=stacked?Objectives.STACK_H:vertical?Objectives.BAR_VH:Objectives.HEIGHT;
            this.stripLine=stacked||vertical?contentHeight+2:STRIP_LINE;
            this.pointLine=pointLine(this.pointStrips.get(0),this.pointStrips.get(1));
            this.neutralLine=pointLine(this.pointStrips.get(2),Collections.<Objectives.Strip>emptyList());
            // Both columns take the widest row, so the clock keeps the exact centre of the bar
            // and does not shift when players join or leave one side.
            int w=0;
            for(List<String> members:left)w=Math.max(w,rowWidth(members));
            for(List<String> members:right)w=Math.max(w,rowWidth(members));
            // The colour line sits on the outer edge, so the widest row plus the line is the column.
            this.column=w+line;
            this.textW=textW;
            // A side is as wide as its longest line, and each side's heads hug their own strip.
            // The bar is sized for the wider side and the clock sits on its exact centre, so the
            // narrower side just leaves its spare room on the outside instead of a gap by the clock.
            int leftW=TeamBar.stripWidth(this.leftStrips),rightW=TeamBar.stripWidth(this.rightStrips);
            this.leftStripW=leftW;
            int leftRoom=compact?column+GAP+(leftW==0?0:leftW+OBJECTIVE_GAP):Math.max(column+GAP,leftW+OBJECTIVE_GAP);
            int rightRoom=compact?column+GAP+(rightW==0?0:rightW+OBJECTIVE_GAP):Math.max(column+GAP,rightW+OBJECTIVE_GAP);
            int half=Math.max(leftRoom,rightRoom);
            half=Math.max(half,TeamBar.stripWidth(pointStrips.get(0))+GAP+(compact&&leftW>0?leftW+OBJECTIVE_GAP:0));
            half=Math.max(half,TeamBar.stripWidth(pointStrips.get(1))+GAP+(compact&&rightW>0?rightW+OBJECTIVE_GAP:0));
            half=Math.max(half,(TeamBar.stripWidth(pointStrips.get(2))-textW+1)/2);
            for(Objectives.Strip strip:pointStrips.get(2))
                half=Math.max(half,Math.max(strip.centre,strip.width-strip.centre)-(textW/2));
            // A long header widens the bar symmetrically, so the clock stays on the exact centre.
            half=Math.max(half,(headerW-textW+1)/2);
            this.width=2*half+textW;
            this.textX=half;
            this.leftEnd=textX-GAP-(!compact||leftW==0?0:leftW+OBJECTIVE_GAP);
            this.rightX=textX+textW+GAP+(!compact||rightW==0?0:rightW+OBJECTIVE_GAP);
            // Each line sits against the clock on its own side, right-aligned on the left.
            this.rightStripX=textX+textW+OBJECTIVE_GAP;
            this.leftStripX=textX-OBJECTIVE_GAP-leftW;
            // Each row is drawn in a band at least nine pixels tall so the clock still fits.
            int lines=Math.max(this.leftStrips.size(),this.rightStrips.size());
            this.band=counts?Math.max(COUNT_LINE+5,Math.min(COUNT_BAND,countBand)):Math.max(Math.max(head,9),compact&&lines>0?lines*stripLine:0);
            int rows=Math.max(left.size(),right.size());
            int stack=rows>0?rows*band+(rows-1)*ROW_GAP:band;
            // Only compact markers share the clock's row; named team blocks stay underneath.
            this.stripH=lines*stripLine;
            this.leftRowTops=new int[left.size()];
            this.rightRowTops=new int[right.size()];
            this.leftStripTops=new int[this.leftStrips.size()];
            this.rightStripTops=new int[this.rightStrips.size()];
            this.stripY=compact?(band-(lines==0?Objectives.HEIGHT:(lines-1)*stripLine+contentHeight))/2:stack+OBJECTIVE_GAP;
            int standardHeight;
            if(compact){
                for(int r=0;r<left.size();r++)leftRowTops[r]=r*(band+ROW_GAP);
                for(int r=0;r<right.size();r++)rightRowTops[r]=r*(band+ROW_GAP);
                for(int i=0;i<leftStripTops.length;i++)leftStripTops[i]=stripY+i*stripLine;
                for(int i=0;i<rightStripTops.length;i++)rightStripTops[i]=stripY+i*stripLine;
                standardHeight=Math.max(stack,stripH);
            }else{
                // Each team's objectives sit directly under its own heads, so on a four-team bar a
                // block never drifts under another team's row. Teams with no heads left over go
                // below the stack, as before.
                // With heads hidden there is no head row to fill: the team's name takes the bar's
                // own top edge and everything under it moves up, instead of hanging below a gap.
                int rowH=head>0||counts?band:0;
                standardHeight=Math.max(
                    layout(left,leftOwners,this.leftStrips,leftRowTops,leftStripTops,band,rowH,stripLine),
                    layout(right,rightOwners,this.rightStrips,rightRowTops,rightStripTops,band,rowH,stripLine));
            }
            this.pointY=standardHeight+OBJECTIVE_GAP;
            int ownedRows=Math.max(pointStrips.get(0).size(),pointStrips.get(1).size());
            int neutralRows=pointStrips.get(2).size();
            this.neutralPointY=pointY+ownedRows*pointLine+(ownedRows>0&&neutralRows>0?OBJECTIVE_GAP:0);
            this.height=(ownedRows==0&&neutralRows==0?standardHeight:neutralPointY+neutralRows*neutralLine)
                +(header==null?0:HEADER_H);
            this.label=label;
        }
        /** FFA has one centred grid below the clock, with a colour line and score under each head. */
        Bar(List<List<String>> rows,int head,int cell,int textW,Timer timer,String label,Header header,int headerW,Map<String,String> scores){
            this(rows,head,cell,textW,timer,label,header,headerW,scores,Collections.<Objectives.Strip>emptyList());
        }
        /**
         * FFA with its objectives: every player is their own team, so a control point or a flag has
         * no side of its own and goes in the middle, between the clock and the grid.
         */
        Bar(List<List<String>> rows,int head,int cell,int textW,Timer timer,String label,Header header,int headerW,
            Map<String,String> scores,List<Objectives.Strip> middle){
            this.ffa=true;this.ffaScores=scores;this.left=rows;this.right=Collections.emptyList();
            this.head=head;this.advance=cell;this.line=0;this.compact=false;this.header=header;
            this.counts=false;this.cell=0;
            this.timer=timer;this.label=label;this.textW=textW;this.leftOwners=null;this.rightOwners=null;
            this.leftStrips=Collections.emptyList();this.rightStrips=Collections.emptyList();
            List<Objectives.Strip> centre=middle==null?Collections.<Objectives.Strip>emptyList():middle;
            // Shared flags already use this slot on a team map; in FFA everything does.
            this.pointStrips=Arrays.asList(Collections.<Objectives.Strip>emptyList(),
                Collections.<Objectives.Strip>emptyList(),centre);
            boolean vertical=UtilitySettings.teamBarScoreStyle==2&&hasScoreBars(centre);
            boolean stacked=hasStackedScores(centre);
            int contentHeight=stacked?Objectives.STACK_H:vertical?Objectives.BAR_VH:Objectives.HEIGHT;
            this.neutralLine=stacked||vertical?contentHeight+2:STRIP_LINE;
            this.stripLine=STRIP_LINE;this.pointLine=STRIP_LINE;
            int widest=0;for(List<String> row:rows)widest=Math.max(widest,row.size()*cell);
            for(Objectives.Strip one:centre)widest=Math.max(widest,2*Math.max(one.centre,one.width-one.centre));
            this.width=Math.max(Math.max(widest,textW),headerW)+2;
            this.textX=(width-textW)/2;this.column=0;this.leftEnd=0;this.rightX=0;
            this.leftStripX=0;this.rightStripX=0;this.leftStripW=0;this.band=head;
            this.leftRowTops=new int[rows.size()];this.rightRowTops=new int[0];
            this.leftStripTops=new int[0];this.rightStripTops=new int[0];
            // Clock, then the grid, then the objectives under the last head row: on a free-for-all
            // map a control point or a flag scores the players above it, so it belongs under them.
            this.stripY=0;this.stripH=0;this.pointY=0;
            int top=12;
            for(int i=0;i<rows.size();i++)leftRowTops[i]=top+i*(head+15);
            int rowsBottom=rows.isEmpty()?top:top+rows.size()*(head+15)-2;
            this.neutralPointY=rowsBottom+(centre.isEmpty()?0:OBJECTIVE_GAP);
            this.height=Math.max(9,centre.isEmpty()?rowsBottom:neutralPointY+centre.size()*neutralLine)
                +(header==null?0:HEADER_H);
        }
        /** Left edge of one objective line, against the clock on its own side. */
        int stripX(int side,Objectives.Strip one){
            if(side==2)return leftEnd-one.width;
            if(side==3)return rightX;
            if(side==4)return width/2-one.centre;
            return side==0?textX-OBJECTIVE_GAP-one.width:rightStripX;
        }
        List<Objectives.Strip> strips(int side){return side==0?leftStrips:side==1?rightStrips:pointStrips.get(side-2);}
        int lineHeight(int side){return side<2?stripLine:side==4?neutralLine:pointLine;}
        int stripTop(int side){
            if(side<2)return stripY+(compact?(Math.max(leftStrips.size(),rightStrips.size())-strips(side).size())*stripLine/2:0);
            return side==4?neutralPointY:pointY;
        }
        int rowTop(int side,int row){return side==0?leftRowTops[row]:rightRowTops[row];}
        /** How wide one row is: a cell per team in count mode, otherwise its heads. */
        int rowWidth(List<String> members){return counts?cell:row(members,head,advance);}
        /** The team a head row belongs to, or null when it is unknown (the editor's placeholders). */
        String owner(int side,int row){return ownerAt(side==0?leftOwners:rightOwners,row);}
        int stripTop(int side,int index){
            if(side==0)return leftStripTops[index];
            if(side==1)return rightStripTops[index];
            return stripTop(side)+index*lineHeight(side);
        }
        /**
         * Stacks one column's head blocks and gives each team's objectives the space directly
         * under that team's own block, so a block can never drift under another team's row.
         * Objectives of teams with no heads online fall in below the stack, as they did before.
         * {@code band} is the clock's own row, which the column never gets shorter than;
         * {@code rowH} is what a head row takes, which is nothing when the heads are hidden.
         * Returns the column's bottom, measured inside the bar.
         */
        private static int layout(List<List<String>> rows,List<String> owners,List<Objectives.Strip> strips,
                                  int[] rowTops,int[] stripTops,int band,int rowH,int stripLine){
            boolean[] placed=new boolean[strips.size()];
            int y=0,r=0;
            while(r<rows.size()){
                String owner=ownerAt(owners,r);
                int from=r;
                if(owner==null)r++;
                else do{r++;}while(r<rows.size()&&owner.equals(ownerAt(owners,r)));
                for(int i=from;i<r;i++){rowTops[i]=y;y+=rowH;}
                if(owner!=null){
                    boolean any=false;
                    // Matched by team name, not by the order the lines arrived: PGM sorts the
                    // sidebar by score while the bar sorts by the map's team order, so on a
                    // three- or four-team map a team's goals would otherwise land under another
                    // team's heads or pile up at the bottom.
                    for(int i=0;i<strips.size();i++){
                        if(placed[i]||!sameTeam(strips.get(i).team,owner))continue;
                        if(!any){y+=OBJECTIVE_GAP;any=true;}
                        stripTops[i]=y;y+=stripLine;placed[i]=true;
                    }
                }
                if(r<rows.size())y+=ROW_GAP;
            }
            boolean leftover=false;
            for(boolean done:placed)if(!done){leftover=true;break;}
            if(rows.isEmpty()){
                // No heads at all: keep the stack below the clock's own row.
                y=rowH+(strips.isEmpty()?0:OBJECTIVE_GAP);
            }else if(leftover)y+=OBJECTIVE_GAP;
            for(int i=0;i<strips.size();i++)if(!placed[i]){stripTops[i]=y;y+=stripLine;}
            return Math.max(band,y);
        }
        private static boolean hasScoreBars(List<Objectives.Strip> strips){
            for(Objectives.Strip strip:strips)for(Objectives.Part part:strip.parts)if(part.fraction>=0)return true;
            return false;
        }
        private static int pointLine(List<Objectives.Strip> a,List<Objectives.Strip> b){
            if(hasStackedScores(a)||hasStackedScores(b))return Objectives.STACK_H+2;
            if(UtilitySettings.teamBarScoreStyle==2&&(hasScoreBars(a)||hasScoreBars(b)))return Objectives.BAR_VH+2;
            return STRIP_LINE;
        }
        private static boolean hasStackedScores(List<Objectives.Strip> strips){
            for(Objectives.Strip strip:strips)for(Objectives.Part part:strip.parts)if(part.stacked)return true;
            return false;
        }
        private static String ownerAt(List<String> owners,int row){
            return owners!=null&&row<owners.size()?owners.get(row):null;
        }
        private static boolean sameTeam(String a,String b){
            return a!=null&&b!=null&&Objectives.sameTeam(a,b);
        }
        /**
         * Where a right-column row starts: flush against the clock, growing outward to the right,
         * so an empty tail always falls on the bar's outside and never next to the clock.
         */
        int rightStart(List<String> members){return rightX;}
        /** Where a left-column row starts: flush against the clock, growing outward to the left. */
        int leftStart(List<String> members){return ffa?(width-members.size()*advance)/2+(advance-head)/2:leftEnd-rowWidth(members);}
        /** The team-colour line for a left row: at the row's outer end, not stranded in the gap. */
        int leftLine(List<String> members){return leftEnd-line-rowWidth(members);}
        /** The team-colour line for a right row: just past the row's outer end. */
        int rightLine(List<String> members){return rightX+rowWidth(members)+LINE_GAP;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void chat(ClientChatReceivedEvent event){
        if(event.type==2){ // action bar only; chat lines carry other players' times
            actionBar=ChatRules.plain(event.message.getUnformattedText()).trim();
            actionBarAt=System.nanoTime();
            return;
        }
        String formatted=event.message.getFormattedText();
        // A welcome block always opens with the map name wrapped in a rule of dashes or spaces, so
        // the name is the first line between them. That rule carries no words, which is what makes
        // this read the same on every client; the author line below it is translated, so it is only
        // read when the client's own language is English.
        // Only a line that actually is a heading replaces the pending one: every other message would
        // otherwise clear it before the author line that completes it arrives.
        if(!ChatRules.playerMessage(formatted)){
            String heading=welcomeHeading(formatted);
            if(heading!=null)welcomeTitle=heading;
        }
        for(String line:ChatRules.plain(event.message.getUnformattedText()).split("[\\r\\n]+")){
            String text=line.trim();
            if(MatchEnd.is(text)){end();return;}
            if(welcomeTitle!=null&&(text.startsWith("Created by ")||ChatRules.welcomeEnd(text))){
                announce(welcomeTitle,text.startsWith("Created by ")?text.substring("Created by ".length()).trim():"");
                continue;
            }
            // "Playing <map>" is the map change itself: the last match's clock must not linger.
            // shortcut: a player typing "Playing x" in chat also resets it; the next server clock line repairs it
            int playing=text.indexOf("Playing ");
            if(playing==0||(playing>0&&MapPreview.serverQuery(text.substring(playing))!=null)){
                announced=true;matchOver=false;reset(System.nanoTime());
                String query=MapPreview.serverQuery(text.substring(playing).trim());
                if(query!=null){
                    resolveTeamOrder(query);
                    // The server prints "Playing <map> by <author>"; the map's name and author are
                    // what the header above the clock shows and names on hover.
                    mapName=query.trim();
                    int by=text.lastIndexOf(" by ");
                    mapAuthor=by<0?"":text.substring(by+4).trim();
                }
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
        // A welcome block whose author line is in another language still names its map: it closes on
        // a rule of dashes instead, so the heading is all the block gave us. A heading sent on its
        // own line waits for the message that follows it.
        if(welcomeTitle!=null&&ChatRules.plain(formatted).contains("\n"))announce(welcomeTitle,"");
    }

    /**
     * The map name at the top of a welcome block: the first line, wrapped in the strikethrough rule
     * PGM puts around it. Strikethrough is formatting, not a word, so this reads the same on every
     * client; the name between the rule is the map itself.
     */
    static String welcomeHeading(String formatted){
        if(formatted==null)return null;
        for(String line:formatted.split("[\\r\\n]+")){
            if(line.trim().isEmpty())continue;
            if(!line.contains("\u00a7m"))return null;
            Matcher m=HEADING.matcher(ChatRules.plain(line));
            String title=m.matches()?m.group(1).trim():"";
            // A rule with no name in it is not a heading: a map name always has a letter or digit.
            return title.matches(".*[A-Za-z0-9\\u00C0-\\uFFFF].*")?title:null;
        }
        return null;
    }

    /** A map's welcome block has been read in full: this is the map the bar is now on. */
    private void announce(String map,String author){
        welcomeTitle=null;
        if(!announced||!MapInfo.norm(map).equals(MapInfo.norm(teamMap))){
            announced=true;matchOver=false;reset(System.nanoTime());resolveTeamOrder(map);
        }
        mapName=map;
        mapAuthor=author;
    }

    /** The match is over: the clock stops where it stands and the bar keeps its last state. */
    private void end(){ended=true;frozenSeconds=-1;matchOver=true;}

    /**
     * PGM plays the same wither sound to everyone when a match finishes. The chat line that says so
     * is translated per player, so on a non-English client this sound is the only end signal left.
     */
    @SubscribeEvent(priority=EventPriority.LOWEST) public void sound(PlaySoundEvent event){
        if(MatchEnd.endedBySound(event.name))end();
    }

    /** A new map has loaded: nothing has carried a clock yet, so the middle starts again at 0:00. */
    private void reset(long now){
        symbolicScores.clear();
        controlPoints.clear();
        ended=false;started=false;overtime=false;timeLimitSeen=false;frozenSeconds=-1;preMatch=true;baseSeconds=0;baseNanos=now;direction=0;clockSource=0;
        ffaSlots.clear();
        teamOrder=Collections.emptyList();mapFreeForAll=false;teamMap="";welcomeTitle=null;mapLookup++;
        mapName="";mapAuthor="";
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
        mapFreeForAll=map.freeForAll;
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
        symbolicScores.clear();
        headMenu=null;
        controlPoints.clear();
        // Hub and match are separate worlds, so this also runs on the teleport INTO a match, where
        // the "Playing" line has already arrived. Only a match end means we are going back out.
        if(matchOver)announced=false;matchOver=false;}

    private void syncWorld(Minecraft mc){
        if(world!=mc.theWorld)clear();
        world=mc.theWorld;
        // Leaving the server: nothing is announced any more.
        if(mc.theWorld==null){announced=false;matchOver=false;teamOrder=Collections.emptyList();mapFreeForAll=false;teamMap="";welcomeTitle=null;mapLookup++;mapName="";mapAuthor="";}
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
        java.util.Set<String> tabNames=new java.util.HashSet<>();
        if(mc.getNetHandler()!=null)for(NetworkPlayerInfo entry:mc.getNetHandler().getPlayerInfoMap()){
            String profile=entry.getGameProfile().getName();
            if(!profile.startsWith("|"))tabNames.add(profile.toLowerCase(Locale.ROOT));
            else if(entry.getDisplayName()!=null){
                Matcher names=Pattern.compile("[A-Za-z0-9_]+").matcher(ChatRules.plain(entry.getDisplayName().getFormattedText()));
                while(names.find())tabNames.add(names.group().toLowerCase(Locale.ROOT));
            }
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
            members=remembered(mc,team.getTeamName(),members,now,tabNames);
            members=online(mc,members,now,tabNames);
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
     * team. Only trust missing entries after our own name confirms the tab roster is available.
     */
    private static List<String> online(Minecraft mc,List<String> names,long now,java.util.Set<String> tabNames){
        if(mc.getNetHandler()==null||names.isEmpty())return names;
        List<String> present=new ArrayList<>();
        for(String name:names){
            if(tabNames.contains(name.toLowerCase(Locale.ROOT))){present.add(name);tabSeen.put(name,now);continue;}
            boolean rosterReady=mc.thePlayer!=null&&tabNames.contains(mc.thePlayer.getName().toLowerCase(Locale.ROOT));
            if(keepMissingPlayer(tabSeen,name,now,rosterReady))present.add(name);
        }
        return present;
    }

    static boolean keepMissingPlayer(Map<String,Long> seen,String name,long now,boolean rosterReady){
        Long last=seen.get(name);
        if(last==null&&!rosterReady)return true;
        if(last==null){seen.put(name,now);return true;}
        return now-last<LEFT_GRACE;
    }

    /**
     * A death can also take the player out of the scoreboard team for the respawn delay. Remember
     * the last member list per team so the head stays where it was instead of vanishing and
     * snapping back. Moving to another team or to the observers is not a death: that head belongs
     * in its new place straight away, so it is never left behind on the old row.
     */
    private static List<String> remembered(Minecraft mc,String team,List<String> current,long now,java.util.Set<String> tabNames){
        Scoreboard board=mc.theWorld.getScoreboard();
        List<String> previous=lastMembers.get(team);
        if(previous!=null)for(String name:previous){
            if(current.contains(name)){goneAt.remove(name);continue;}
            ScorePlayerTeam nowTeam=board.getPlayersTeam(name);
            if(nowTeam!=null&&!nowTeam.getTeamName().equals(team)){goneAt.remove(name);continue;}
            // Only a player still on the server can be respawning. A leaver has no tab entry, so
            // its short grace in online() decides and this memory must not keep the head around.
            boolean inTab=mc.getNetHandler()!=null&&tabNames.contains(name.toLowerCase(Locale.ROOT));
            if(tabSeen.containsKey(name)&&mc.getNetHandler()!=null&&!inTab){goneAt.remove(name);continue;}
            goneAt.putIfAbsent(name,now);
            Long gone=goneAt.get(name);
            if(gone!=null&&(now-gone<KEEP_DEAD||(inTab&&dead(name))))current.add(name);
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
        // only a match clock earns a bar. The pre-match count-in draws the clock alone, in gold,
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
        List<List<Objectives.Strip>> pointStrips=null;
        // PGM renders the map's gamemode as the sidebar's title, in its own colour; keep that colour.
        // Read it in both modes: the map-name header names it on hover.
        String modeText="";
        int modeColour=AQUA;
        if(mc.theWorld!=null){
            net.minecraft.scoreboard.ScoreObjective sidebar=mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
            if(sidebar!=null){
                String formatted=sidebar.getDisplayName();
                modeText=ChatRules.plain(formatted).trim();
                int code=formatted.indexOf('\u00a7');
                if(code>=0&&code+1<formatted.length())modeColour=chatColour(formatted.charAt(code+1));
            }
        }
        String modeKey=MapInfo.norm(modeText);
        List<String> allTeams=teamNames(mc,sides,teamOrder);
        ffa=mapFreeForAll||ffaMode(ffa,modeKey,teamOrder,sidebarRows(mc),allTeams);
        Header header=header(UtilitySettings.teamBarHeader,mapName,mapAuthor,modeText,modeColour);
        int headerW=header==null?0:mc.fontRendererObj.getStringWidth("\u00a7l"+header.text);
        // Every match team's name, in the order the bar and PGM's sidebar use, with the column each
        // one's heads take. A team with nobody online still has objectives printed, so it still
        // needs to be known and still needs a side.
        Map<String,Integer> columns=teamColumns(allTeams,teamOrder);
        if(ffa){
            if(UtilitySettings.teamBarHideHeads)head=0;
            List<String> pool=preview?new ArrayList<String>():ffaSlots;
            if(preview)for(Side side:sides)pool.addAll(side.members);
            List<String> players=new ArrayList<>();
            if(!counting||preview)for(String player:pool)if(player!=null&&players.size()<shown)players.add(player);
            Map<String,String> scores=ffaScores(sidebarRows(mc),players);
            int cell=head+4;
            for(String score:scores.values())cell=Math.max(cell,mc.fontRendererObj.getStringWidth(score)+4);
            int fit=Math.max(1,(screen-8)/cell);
            List<List<String>> rows=new ArrayList<>();
            for(int from=0;from<players.size();from+=fit)
                rows.add(new ArrayList<>(players.subList(from,Math.min(from+fit,players.size()))));
            // Every player is their own team here, so a control point or a flag has no side of its
            // own: it goes in the middle, under the clock, instead of being dropped.
            List<Objectives.Strip> middle=new ArrayList<>();
            if(UtilitySettings.teamBarObjectives){
                // Half the screen: the strips are centred under the clock, so their full width counts.
                int pointMax=Math.max(24,(screen-8)/2);
                List<List<Objectives.Strip>> beside=Objectives.strips(mc,allTeams,columns,false,pointMax,pointMax,
                    UtilitySettings.teamBarControlPoints?controlPoints:null);
                for(int side=2;side<5;side++)middle.addAll(beside.get(side));
            }
            return new Bar(rows,head,cell,textW,timer,label,header,headerW,scores,middle);
        }
        // Eight teams of heads no longer fit under the clock, so those maps count them instead.
        boolean counts=!UtilitySettings.teamBarHideHeads&&(UtilitySettings.teamBarCount||countsForced(allTeams));
        // Wide enough for a two-digit count; the editor's placeholder rows have no team to measure.
        int cell=counts?14:0;
        if(counts)for(Side side:sides)cell=Math.max(cell,mc.fontRendererObj.getStringWidth(Integer.toString(side.members.size()))+8);
        // Objectives go below the counts, never beside the clock, so each team's goals read as part
        // of its own row.
        boolean compact=UtilitySettings.teamBarObjectivesCompact&&!counts;
        if(UtilitySettings.teamBarObjectives){
            // Each team's objectives sit on the same side of the clock as that team's heads, so a
            // team's goals are always next to its own row. Compact mode keeps only the state icons.
            // Named blocks are below the heads, so they can use the full half-width and wrap.
            int max=Math.max(24,(screen-textW-2*GAP-8)/(compact?3:2));
            int pointMax=Math.max(24,(screen-textW-2*GAP-8)/2);
            List<List<Objectives.Strip>> beside=Objectives.strips(mc,allTeams,columns,compact,max,pointMax,
                UtilitySettings.teamBarControlPoints?controlPoints:null);
            leftStrips=beside.get(0);rightStrips=beside.get(1);
            pointStrips=beside.subList(2,5);
        }
        int beside=compact?Math.max(stripWidth(leftStrips)+OBJECTIVE_GAP,stripWidth(rightStrips)+OBJECTIVE_GAP):0;
        int perRow=perRow(head,line(head,ffa),textW+2*beside,screen);
        List<List<String>> left=new ArrayList<>(),right=new ArrayList<>();
        // Which team each row belongs to, so a team's objectives can sit under its own block.
        List<String> leftOwners=new ArrayList<>(),rightOwners=new ArrayList<>();
        if(counting&&!preview){
            // Counting in: the middle only, so nothing suggests the teams are set yet.
        }else if(sides.isEmpty()){
            // No teams yet (or you are watching from the observer team): keep the clock alone and
            // centred instead of dropping the bar, so it never sits off centre or disappears.
            if(preview){
                int fake=Math.max(1,Math.min(Math.min(shown,8),perRow));
                left.add(placeholders(fake));right.add(placeholders(fake));
            }
        }else{
            // One block of rows per team, alternating columns. A team keeps the column its size
            // gives it, so joining a team never drags the block across the bar. A row holds up to
            // ten heads; the rest wrap onto the rows below rather than shrinking. A right-column
            // block hugs the clock, so the local head moves to the outer end of its last row.
            // A count is one cell per team, so nothing wraps and nobody is left out of the number.
            int wrap=counts||UtilitySettings.teamBarHideHeads?Integer.MAX_VALUE:perRow;
            for(int i=0;i<sides.size();i++){
                Side side=sides.get(i);
                List<String> members=new ArrayList<>(side.members.subList(0,Math.min(counts?side.members.size():shown,side.members.size())));
                Integer columnIndex=columns.get(MapInfo.norm(side.name));
                int index=columnIndex==null?i:columnIndex;
                if(!counts&&index%2==1&&mc.thePlayer!=null&&members.remove(mc.thePlayer.getName()))members.add(mc.thePlayer.getName());
                List<List<String>> column=index%2==0?left:right;
                List<String> owners=index%2==0?leftOwners:rightOwners;
                for(int from=0;from<members.size();from+=wrap){
                    column.add(new ArrayList<>(members.subList(from,Math.min(from+wrap,members.size()))));
                    owners.add(side.name);
                }
            }
        }
        // Hidden heads still keep one row per team, so the team-colour line and the objectives stay
        // where they belong; the row itself shrinks to the line.
        boolean hideHeads=UtilitySettings.teamBarHideHeads;
        // Last resort, height only: a stack of many teams can still be taller than the screen.
        int band=COUNT_BAND;
        while(true){
            int h=hideHeads||counts?0:head;
            Bar fit=new Bar(left,right,h,textW,timer,label,counts?0:line(h,ffa),leftStrips,rightStrips,compact,pointStrips,header,headerW,leftOwners,rightOwners,counts,cell,band);
            if(fit.height<=screenH-8)break;
            // Counting has no head to shrink, so the count rows themselves get shorter instead.
            if(counts){if(band<=COUNT_LINE+5)break;band--;}
            else if(head>MIN_HEAD)head--;
            else break;
        }
        int drawnHead=hideHeads||counts?0:head;
        return new Bar(left,right,drawnHead,textW,timer,label,counts?0:line(drawnHead,ffa),leftStrips,rightStrips,compact,pointStrips,header,headerW,leftOwners,rightOwners,counts,cell,band);
    }

    /** Eight teams of heads no longer fit under the clock, so the bar counts them instead. */
    static boolean countsForced(List<String> teams){return teams.size()>=COUNT_TEAMS;}

    /** PGM prints numeric scores as "current/target player" or "current player". */
    static Map<String,String> ffaScores(List<String> rows,List<String> players){
        Map<String,String> scores=new HashMap<>();
        Pattern score=Pattern.compile("^(\\d+(?:\\s*/\\s*\\d+)?)\\s+(.+)$");
        for(String row:rows){
            Matcher match=score.matcher(ChatRules.plain(row).trim());
            if(!match.matches())continue;
            for(String player:players)if(player.equalsIgnoreCase(match.group(2).trim())){
                scores.put(player.toLowerCase(Locale.ROOT),match.group(1));break;
            }
        }
        return scores;
    }

    /** The line above the clock: shown text, its colour, and what hovering it says. */
    static final class Header {
        final String text,hover;final int colour;
        Header(String text,int colour,String hover){this.text=text;this.colour=colour;this.hover=hover;}
    }

    /**
     * Map name or gamemode above the clock, plus the other one and the author on hover. Values the
     * server has not printed yet leave the line out instead of showing a placeholder.
     */
    static Header header(int mode,String mapName,String author,String modeText,int modeColour){
        if(mode==0)return null;
        boolean map=mode==1;
        String text=text(map?mapName:modeText);
        if(text.isEmpty())return null;
        String other=text(map?modeText:mapName);
        String by=text(author);
        String hover=other.isEmpty()?by.isEmpty()?"":"by "+by
            :by.isEmpty()?other:other+" by "+by;
        return new Header(text,map?AQUA:modeColour,hover);
    }

    private static String text(String value){return value==null?"":value.trim();}

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

    static boolean ffaMode(boolean guessed,String mode,List<String> order,List<String> rows,List<String> teams){
        if("ffa".equals(mode)||"freeforall".equals(mode))return true;
        if(!order.isEmpty())return false;
        java.util.Set<String> headers=new java.util.HashSet<>();
        for(String row:rows)for(String team:teams)if(Objectives.sameTeam(ChatRules.plain(row).trim(),team))headers.add(team);
        return guessed&&headers.size()<2;
    }

    static int teamRank(String name,List<String> order){
        String key=MapInfo.norm(ChatRules.plain(name));
        for(int i=0;i<order.size();i++)if(key.equals(MapInfo.norm(ChatRules.plain(order.get(i)))))return i;
        return order.size();
    }

    /** Known map teams reserve their own columns; unknown teams cannot collide with them. */
    static Map<String,Integer> teamColumns(List<String> teams,List<String> order){
        Map<String,Integer> columns=new HashMap<>();int next=order.size();
        for(String name:teams){
            int rank=teamRank(name,order);
            columns.put(MapInfo.norm(name),rank<order.size()?rank:next++);
        }
        return columns;
    }

    /**
     * Every match team's name, in the bar's fixed order, including teams with nobody online.
     * A team with no players draws no heads, but PGM still prints its
     * objectives, so it still needs a side of its own instead of falling in with everyone else's.
     */
    static List<String> teamNames(Minecraft mc,List<Side> sides,List<String> order){
        List<String> all=new ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(Side side:sides)if(seen.add(MapInfo.norm(side.name)))all.add(side.name);
        if(mc.theWorld!=null){
            try{
                for(ScorePlayerTeam team:new ArrayList<>(mc.theWorld.getScoreboard().getTeams())){
                    String name=team.getTeamName();
                    if(name.contains("TabView")||OBSERVERS.matcher(ChatRules.plain(name)).matches())continue;
                    boolean players=team.getMembershipCollection().isEmpty();
                    for(String member:team.getMembershipCollection())if(realPlayer(member)){players=true;break;}
                    if(players&&seen.add(MapInfo.norm(name)))all.add(name);
                }
            }catch(RuntimeException racing){
                // Same netty race as sides(): the teams already found are still enough to place them.
            }
        }
        all.sort((a,b)->{
            int rank=Integer.compare(teamRank(a,order),teamRank(b,order));
            return rank!=0?rank:String.CASE_INSENSITIVE_ORDER.compare(a,b);
        });
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

    /** A match has started with no server clock: count up from 0:00 in gold until one arrives. */
    private void begin(long now){
        symbolicScores.clear();
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
                    if(!preMatch||ended)symbolicScores.clear();
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

    /** Hide only PGM's duplicate time-remaining bar, and only while Team Bar is visible. */
    static boolean hidesBossBar(Minecraft mc){
        if(!UtilitySettings.teamBar||mc.thePlayer==null||mc.theWorld==null||mc.gameSettings.hideGUI
            ||!ReliableChat.fullChat()||!INSTANCE.announced||(!UtilitySettings.teamBarObserver&&observing(mc))
            ||(UtilitySettings.teamBarHideOnTab&&playerListVisible(mc))||mc.currentScreen instanceof HudEditor)return false;
        String boss=ChatRules.plain(BossStatus.bossName==null?"":BossStatus.bossName).trim();
        return boss.matches("(?i)^time remaining\\s*:\\s*\\d{1,3}:\\d{2}\\s*$");
    }

    /** Hide the vanilla sidebar while the bar is up: it repeats the clock and the objectives. */
    static boolean hidesScoreboard(Minecraft mc){
        return UtilitySettings.teamBar&&UtilitySettings.teamBarHideScoreboard
            &&mc.thePlayer!=null&&mc.theWorld!=null&&!mc.gameSettings.hideGUI
            &&!(mc.currentScreen instanceof HudEditor);
    }

    /**
     * The sidebar has no render event of its own, so its switch is read at the top of the frame,
     * before vanilla draws it. Nothing else in the mod may leave it off: the flag is only ever
     * cleared while this option is on.
     */
    @SubscribeEvent(priority=EventPriority.LOWEST) public void scoreboard(RenderGameOverlayEvent.Pre event){
        if(event.type!=RenderGameOverlayEvent.ElementType.ALL)return;
        UtilitySettings.load();
        boolean hide=hidesScoreboard(MinecraftAccess.get());
        if(hide)GuiIngameForge.renderObjective=false;
        else if(scoreboardHidden)GuiIngameForge.renderObjective=true;
        scoreboardHidden=hide;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void bossBar(RenderGameOverlayEvent.Pre event){
        if(event.type!=RenderGameOverlayEvent.ElementType.BOSSHEALTH)return;
        UtilitySettings.load();
        Minecraft mc=MinecraftAccess.get();
        if(hidesBossBar(mc)){event.setCanceled(true);if(BossStatus.statusBarTime>0)BossStatus.statusBarTime--;return;}
        if(!UtilitySettings.teamBar||!UtilitySettings.teamBarHideVanillaBossHealth)return;
        event.setCanceled(true);
        if(BossStatus.bossName!=null&&BossStatus.statusBarTime>0){
            BossStatus.statusBarTime--;
            String name=BossStatus.bossName;
            int width=new ScaledResolution(mc).getScaledWidth();
            mc.fontRendererObj.drawStringWithShadow(name,(width-mc.fontRendererObj.getStringWidth(name))/2,12,0xFFFFFF);
        }
    }

    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(!HudStage.due(event))return;
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
        String name=Mouse.isGrabbed()?null:headerAt(bar,mx-box.x,my-box.y,mc.fontRendererObj);
        // A count stands in for the whole team, so hovering it lists who the number is made of.
        if(name==null&&!Mouse.isGrabbed())name=countAt(bar,mx-box.x,my-box.y,sr.getScaledHeight());
        if(name==null&&!Mouse.isGrabbed())name=headAt(bar,mx-box.x,my-box.y);
        // The colour line is its own target now that heads can be hidden.
        if(name==null&&!Mouse.isGrabbed())name=lineAt(bar,mx-box.x,my-box.y);
        // Compact objectives have no room for names, so hovering one shows what it is.
        if(name==null&&!Mouse.isGrabbed())name=objectiveAt(bar,mx-box.x,my-box.y,mc.fontRendererObj);
        if(!java.util.Objects.equals(name,hovered)){hovered=name;hoverSince=System.nanoTime();}
        draw(mc,bar,box.x,box.y,name,hoverSince);
        if(name!=null){
            // A count's roster arrives as one line per player, so the box sizes to its longest line.
            String[] lines=name.split("\n",-1);
            int w=0;for(String line:lines)w=Math.max(w,mc.fontRendererObj.getStringWidth(line));
            int h=lines.length*10+4;
            int tx=Math.max(0,Math.min(mx+8,sr.getScaledWidth()-w-6));
            int ty=Math.max(0,Math.min(my+8,sr.getScaledHeight()-h-2));
            Gui.drawRect(tx,ty,tx+w+6,ty+h,0xE0101010);
            for(int i=0;i<lines.length;i++)mc.fontRendererObj.drawStringWithShadow(lines[i],tx+3,ty+3+i*10,0xFFFFFFFF);
        }
    }

    @SubscribeEvent public void menuScreenChanged(GuiOpenEvent event){headMenu=null;}

    private Bar menuBar(Minecraft mc){
        if(!(mc.currentScreen instanceof GuiChat)||!UtilitySettings.teamBar||mc.thePlayer==null||mc.theWorld==null
            ||mc.gameSettings.hideGUI||(UtilitySettings.teamBarHideOnTab&&playerListVisible(mc)))return null;
        try{return bar(mc,false);}catch(RuntimeException unavailable){return null;}
    }

    HeadMenu positionHeadMenu(Bar bar,HudLayout.Rect box,String player,int width,int screenWidth,int screenHeight){
        if(!validMenuPlayer(player))return null;
        int[] head=headBounds(bar,player);
        return head==null?null:new HeadMenu(player,box.x+head[0],box.y+head[1]+bar.head,bar.head,width,screenWidth,screenHeight);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void headClick(GuiScreenEvent.MouseInputEvent.Pre event){
        if(!(event.gui instanceof GuiChat)||Mouse.getEventButton()!=0||!Mouse.getEventButtonState())return;
        Minecraft mc=MinecraftAccess.get();Bar bar=menuBar(mc);
        if(bar==null){headMenu=null;return;}
        ScaledResolution sr=new ScaledResolution(mc);
        int mx=Mouse.getEventX()*sr.getScaledWidth()/mc.displayWidth;
        int my=sr.getScaledHeight()-Mouse.getEventY()*sr.getScaledHeight()/mc.displayHeight-1;
        if(headMenu!=null){
            int action=headMenu.actionAt(mx,my);
            String target=headMenu.player;headMenu=null;
            if(action>=0){
                event.setCanceled(true);
                if(headBounds(bar,target)==null)return;
                if(action==0)confirmProfile(target);
                else if(action==1)teleportFromHeadMenu(mc,target);
                else if(action==2)mc.thePlayer.sendChatMessage(friendCommand(target));
                else ReliableChat.openPrivate(target);
                return;
            }
        }
        HudLayout.Rect box=placed(bar);
        String target=headAt(bar,mx-box.x,my-box.y);
        if(target==null)return;
        headMenu=positionHeadMenu(bar,box,target,HeadMenu.width(mc,target),
            sr.getScaledWidth(),sr.getScaledHeight());
        if(headMenu!=null)event.setCanceled(true);
    }

    static void teleportFromHeadMenu(Minecraft mc,String player){
        mc.thePlayer.sendChatMessage(teleportCommand(player));
        mc.displayGuiScreen(null);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void headMenuDraw(GuiScreenEvent.DrawScreenEvent.Post event){
        if(headMenu==null)return;
        Minecraft mc=MinecraftAccess.get();Bar bar=menuBar(mc);
        if(bar==null){headMenu=null;return;}
        ScaledResolution sr=new ScaledResolution(mc);
        headMenu=positionHeadMenu(bar,placed(bar),headMenu.player,HeadMenu.width(mc,headMenu.player),
            sr.getScaledWidth(),sr.getScaledHeight());
        if(headMenu==null)return;
        drawHeadMenu(mc,headMenu,event.mouseX,event.mouseY);
    }

    /** One shared popup, so the tab list and the team bar offer the same actions in the same look. */
    static void drawHeadMenu(Minecraft mc,HeadMenu menu,int mx,int my){
        // Solid, unlike the menu panels: a popup over the list or the bar must not show what it covers.
        BedrockUi.rounded(menu.x-1,menu.y-1,menu.width+2,HeadMenu.HEIGHT+2,2,BedrockTokens.SURFACE_EDGE);
        BedrockUi.rounded(menu.x,menu.y,menu.width,HeadMenu.HEIGHT,1,(BedrockTokens.SURFACE_PANEL&0xFFFFFF)|0xFF000000);
        mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(menu.player,menu.width-12),
            menu.x+7,menu.y+3,BedrockUi.TEXT);
        BedrockUi.rounded(menu.x+6,menu.y+HeadMenu.TITLE-3,menu.width-12,1,1,BedrockTokens.SURFACE_EDGE);
        for(int action=0;action<HeadMenu.LABELS.length;action++){
            int y=menu.y+HeadMenu.TITLE+action*HeadMenu.ROW;
            if(menu.actionAt(mx,my)==action)BedrockUi.rounded(menu.x+2,y+1,menu.width-4,HeadMenu.ROW-2,2,BedrockTokens.STATE_HOVER);
            String label=HeadMenu.LABELS[action];
            mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(label,menu.width-12),
                menu.x+7,y+5,BedrockUi.TEXT);
        }
    }

    void confirmProfile(String player){
        final String url=profileUrl(player);
        if(url==null)return;
        final Minecraft mc=MinecraftAccess.get();
        final GuiScreen parent=mc.currentScreen;
        mc.displayGuiScreen(new GuiConfirmOpenLink((confirmed,id)->{
            mc.displayGuiScreen(parent);
            if(!confirmed)return;
            try{
                if(!java.awt.Desktop.isDesktopSupported())throw new IllegalStateException("Desktop browsing unavailable");
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }catch(Exception unavailable){
                if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new ChatComponentText("\u00a7cCould not open the player profile in your browser."));
            }
        },url,0,false));
    }

    static int[] headBounds(Bar bar,String player){
        if(bar.head==0)return null;
        int y0=bar.header==null?0:HEADER_H;
        for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int row=0;row<rows.size();row++){
                List<String> members=rows.get(row);int index=members.indexOf(player);
                if(index>=0)return new int[]{(side==0?bar.leftStart(members):bar.rightStart(members))+index*bar.advance,
                    y0+bar.rowTop(side,row)+(bar.band-bar.head)/2};
            }
        }
        return null;
    }

    private static long playerListRenderedAt;
    private static Object playerListWorld;
    @SubscribeEvent(priority=EventPriority.LOWEST) public void playerListRender(RenderGameOverlayEvent.Pre event){
        if(event.type==RenderGameOverlayEvent.ElementType.PLAYER_LIST){
            playerListWorld=MinecraftAccess.get().theWorld;playerListRenderedAt=System.nanoTime();
        }
    }
    static boolean recentlyRenderedPlayerList(long now,long renderedAt){
        return renderedAt>0&&now>=renderedAt&&now-renderedAt<150_000_000L;
    }
    static boolean playerListVisible(Minecraft mc){
        if(mc.thePlayer==null||mc.theWorld==null)return false;
        if(playerListWorld==mc.theWorld&&recentlyRenderedPlayerList(System.nanoTime(),playerListRenderedAt))return true;
        return mc.gameSettings.keyBindPlayerList.isKeyDown()&&(!mc.isIntegratedServerRunning()
            ||mc.thePlayer.sendQueue.getPlayerInfoMap().size()>1||mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0)!=null);
    }

    /** The header line's hover text when the pointer is over it; null otherwise. */
    static String headerAt(Bar bar,int x,int y,net.minecraft.client.gui.FontRenderer font){
        if(bar.header==null||y<0||y>=HEADER_H)return null;
        int w=font.getStringWidth("\u00a7l"+bar.header.text);
        int left=bar.width/2-w/2;
        return x>=left&&x<left+w?bar.header.hover:null;
    }

    /**
     * The compact objective under a point in the bar, for the hover tooltip. Null when the pointer
     * is over a head, empty space, or nothing is drawn in compact mode.
     */
    static String objectiveAt(Bar bar,int x,int y,net.minecraft.client.gui.FontRenderer font){
        y-=bar.header==null?0:HEADER_H;
        for(int side=0;side<5;side++){
            List<Objectives.Strip> lines=bar.strips(side);
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                String name=Objectives.at(one,x-bar.stripX(side,one),y-bar.stripTop(side,i),font,bar.lineHeight(side));
                if(name!=null)return name;
            }
        }
        return null;
    }

    /** The team whose colour line the pointer is on, so the line names the team it belongs to. */
    static String lineAt(Bar bar,int x,int y){
        if(bar.line<=0)return null;
        int height=bar.head>0?bar.head:bar.band,width=bar.line-LINE_GAP;
        y-=bar.header==null?0:HEADER_H;
        for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int r=0;r<rows.size();r++){
                List<String> members=rows.get(r);
                int top=bar.rowTop(side,r)+(bar.band-height)/2;
                int left=side==0?bar.leftLine(members):bar.rightLine(members);
                if(x>=left-1&&x<left+width+1&&y>=top&&y<top+height)return bar.owner(side,r);
            }
        }
        return null;
    }

    /**
     * The roster a team count stands for: the team and its size, then one player per line. Null when
     * the pointer is not over a count. Long teams are cut to what the screen can show.
     */
    static String countAt(Bar bar,int x,int y,int screenHeight){
        if(!bar.counts)return null;
        y-=bar.header==null?0:HEADER_H;
        for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int r=0;r<rows.size();r++){
                List<String> members=rows.get(r);
                if(members.isEmpty())continue;
                int left=side==0?bar.leftStart(members):bar.rightStart(members);
                int top=bar.rowTop(side,r);
                if(x<left||x>=left+bar.cell||y<top||y>=top+bar.band)continue;
                String team=bar.owner(side,r);
                StringBuilder out=new StringBuilder(team==null?members.size()+" players"
                    :team+" ("+members.size()+")");
                int shown=Math.min(members.size(),Math.max(3,(screenHeight-24)/10-1));
                for(int i=0;i<shown;i++)out.append('\n').append(members.get(i));
                if(shown<members.size())out.append("\n+").append(members.size()-shown).append(" more");
                return out.toString();
            }
        }
        return null;
    }

    static String headAt(Bar bar,int x,int y){
        if(bar.head==0)return null;
        y-=bar.header==null?0:HEADER_H;
        for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int r=0;r<rows.size();r++){
                List<String> members=rows.get(r);
                int top=bar.rowTop(side,r)+(bar.band-bar.head)/2;
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
        int barTop=y;
        if(bar.header!=null)y+=HEADER_H;
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
        // Hidden heads leave the line on its own, so it takes the whole row.
        int lineH=bar.head>0?bar.head:bar.band;
        for(int r=0;r<bar.left.size();r++){
            List<String> members=bar.left.get(r);
            int top=y+bar.rowTop(0,r)+(band-bar.head)/2;
            int lineTop=y+bar.rowTop(0,r)+(band-lineH)/2;
            // The line sits at the row's own outer end, so it stays attached to its heads.
            if(lineW>0)quad(world,x+bar.leftLine(members),lineTop,x+bar.leftLine(members)+lineW,lineTop+lineH,teamColour(mc,members));
            int start=x+bar.leftStart(members);
            // A count is the whole row: the number follows the pass, with the team colour under it.
            if(bar.counts&&!members.isEmpty()){
                int rowTop=y+bar.rowTop(0,r);
                quad(world,start,rowTop+bar.band-COUNT_LINE,start+bar.cell,rowTop+bar.band,teamColour(mc,members));
            }else if(bar.head>0)for(int i=0;i<members.size();i++)if(members.get(i).equals(hover)){hx=start+i*bar.advance;hy=top;found=true;}else head(world,members.get(i),start+i*bar.advance,top,bar.head);
            if(bar.ffa&&bar.head>0)for(int i=0;i<members.size();i++){
                int px=start+i*bar.advance;
                quad(world,px,top+bar.head+2,px+bar.head,top+bar.head+4,teamColour(mc,Collections.singletonList(members.get(i))));
            }
        }
        for(int r=0;r<bar.right.size();r++){
            List<String> members=bar.right.get(r);
            int top=y+bar.rowTop(1,r)+(band-bar.head)/2;
            int lineTop=y+bar.rowTop(1,r)+(band-lineH)/2;
            // Rows are flush with the clock and grow outward, so both columns read from the middle.
            int start=x+bar.rightStart(members);
            if(lineW>0)quad(world,x+bar.rightLine(members),lineTop,x+bar.rightLine(members)+lineW,lineTop+lineH,teamColour(mc,members));
            if(bar.counts&&!members.isEmpty()){
                int rowTop=y+bar.rowTop(1,r);
                quad(world,start,rowTop+bar.band-COUNT_LINE,start+bar.cell,rowTop+bar.band,teamColour(mc,members));
            }else if(bar.head>0)for(int i=0;i<members.size();i++)if(members.get(i).equals(hover)){hx=start+i*bar.advance;hy=top;found=true;}else head(world,members.get(i),start+i*bar.advance,top,bar.head);
        }
        if(found){
            int extra=Math.round(Math.max(2,bar.head*.25F)*Math.min(1F,Math.max(0F,(System.nanoTime()-since)/150_000_000F)));
            head(world,hover,hx-extra/2,hy-extra/2,bar.head+extra);
        }
        // The objective squares share the bar's quad pass; the text parts follow once the
        // tessellator is done with the texture unit. Each side stacks its own lines beside the clock.
        for(int side=0;side<5;side++){
            List<Objectives.Strip> lines=bar.strips(side);
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                int px=x+bar.stripX(side,one),py=y+bar.stripTop(side,i);
                for(Objectives.Part part:one.parts){
                    px+=part.space;
                    if(part.stacked)quad(world,px,py+10,px+part.width(mc.fontRendererObj),py+11,chatColour('8'));
                    else if(part.fraction>=0)scoreBar(world,px,py,part.colour,part.fraction,side==1||side==3);
                    else if(part.text==null)wool(world,px,py+(bar.lineHeight(side)-STRIP_LINE)/2,part.colour,part.state);
                    px+=part.width(mc.fontRendererObj);
                }
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        if(bar.header!=null){
            String line="\u00a7l"+bar.header.text;
            // Kept at the top of its line so the heads below it keep some breathing room.
            mc.fontRendererObj.drawStringWithShadow(line,x+bar.width/2-mc.fontRendererObj.getStringWidth(line)/2,
                barTop+1,bar.header.colour);
        }
        if(bar.textW>0){
            // Sits on the first head row, not the middle of the stack, so extra teams stack below it.
            mc.fontRendererObj.drawStringWithShadow(bar.label,x+bar.textX,y+(bar.ffa?0:(band-8)/2),colour(bar.timer));
        }
        if(bar.counts)for(int side=0;side<2;side++){
            List<List<String>> rows=side==0?bar.left:bar.right;
            for(int r=0;r<rows.size();r++){
                List<String> members=rows.get(r);
                if(members.isEmpty())continue;
                String count=Integer.toString(members.size());
                int start=x+(side==0?bar.leftStart(members):bar.rightStart(members));
                mc.fontRendererObj.drawStringWithShadow(count,
                    start+(bar.cell-mc.fontRendererObj.getStringWidth(count))/2,y+bar.rowTop(side,r),WHITE);
            }
        }
        if(bar.ffa)for(int r=0;r<bar.left.size();r++){
            List<String> members=bar.left.get(r);
            int start=x+bar.leftStart(members),top=y+bar.rowTop(0,r);
            for(int i=0;i<members.size();i++){
                String score=bar.ffaScores.get(members.get(i).toLowerCase(Locale.ROOT));
                if(score!=null)mc.fontRendererObj.drawStringWithShadow(score,
                    start+i*bar.advance+bar.head/2-mc.fontRendererObj.getStringWidth(score)/2,top+bar.head+5,WHITE);
            }
        }
        for(int side=0;side<5;side++){
            List<Objectives.Strip> lines=bar.strips(side);
            for(int i=0;i<lines.size();i++){
                Objectives.Strip one=lines.get(i);
                int px=x+bar.stripX(side,one),py=y+bar.stripTop(side,i);
                for(Objectives.Part part:one.parts){
                    px+=part.space;
                    if(part.text!=null){
                        int textY=py+(bar.lineHeight(side)-STRIP_LINE)/2;
                        if(part.stacked){
                            int slash=part.text.indexOf('/'),width=part.width(mc.fontRendererObj);
                            String top=part.text.substring(0,slash).trim(),bottom=part.text.substring(slash+1).trim();
                            mc.fontRendererObj.drawStringWithShadow(top,px+(width-mc.fontRendererObj.getStringWidth(top))/2,py,part.colour);
                            mc.fontRendererObj.drawStringWithShadow(bottom,px+(width-mc.fontRendererObj.getStringWidth(bottom))/2,py+13,part.colour);
                        }else if(part.mirror){
                            GlStateManager.pushMatrix();
                            boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
                            try{
                                if(cull)GlStateManager.disableCull();
                                GlStateManager.translate(px+part.width(mc.fontRendererObj),textY,0);
                                GlStateManager.scale(-1F,1F,1F);
                                mc.fontRendererObj.drawStringWithShadow(part.text,0,0,part.colour);
                            }finally{if(cull)GlStateManager.enableCull();GlStateManager.popMatrix();}
                        }else mc.fontRendererObj.drawStringWithShadow(part.text,px,textY,part.colour);
                    }
                    px+=part.width(mc.fontRendererObj);
                }
            }
        }
    }


    /**
     * A score as a bar: vertical fills upward; horizontal fills from the team's outer edge.
     */
    private static void scoreBar(WorldRenderer world,int x,int y,int argb,float fraction,boolean right){
        int filled=Math.round(Math.max(0F,Math.min(1F,fraction))*(UtilitySettings.teamBarScoreStyle==2?Objectives.BAR_VH:Objectives.BAR_W));
        int track=(argb&0xFFFFFF)|0x55000000;
        if(UtilitySettings.teamBarScoreStyle==2){
            roundedScoreBar(world,x,y,Objectives.BAR_VW,Objectives.BAR_VH,track);
            if(filled>0)roundedScoreBar(world,x,y+Objectives.BAR_VH-filled,Objectives.BAR_VW,filled,argb);
            for(int tick=1;tick<4;tick++){
                int at=y+verticalScoreTick(tick);
                quad(world,x+1,at,x+Objectives.BAR_VW-1,at+1,0x99000000);
            }
        }else{
            int top=y+(STRIP_LINE-Objectives.BAR_H)/2;
            roundedScoreBar(world,x,top,Objectives.BAR_W,Objectives.BAR_H,track);
            if(filled>0)roundedScoreBar(world,x+horizontalScoreStart(filled,right),top,filled,Objectives.BAR_H,argb);
            for(int tick=1;tick<4;tick++){
                int at=x+scoreTick(tick);
                quad(world,at,top+1,at+1,top+Objectives.BAR_H-1,0x99000000);
            }
        }
    }

    static int horizontalScoreStart(int filled,boolean right){return right?Objectives.BAR_W-filled:0;}
    static int scoreTick(int quarter){return Objectives.BAR_W*quarter/4;}
    static int verticalScoreTick(int quarter){return Objectives.BAR_VH*quarter/4;}

    private static void roundedScoreBar(WorldRenderer world,int x,int y,int width,int height,int colour){
        if(width<=2||height<=2){quad(world,x,y,x+width,y+height,colour);return;}
        quad(world,x+1,y,x+width-1,y+height,colour);
        quad(world,x,y+1,x+1,y+height-1,colour);
        quad(world,x+width-1,y+1,x+width,y+height-1,colour);
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

    /** Gold count-up, yellow limit, red at 25 seconds and dark red at 5 seconds. */
    static int colour(Timer timer){
        if(timer==null)return GOLD;
        if(timer.starting)return GOLD;
        if(timer.countdown)return timer.seconds<=5?DARK_RED:timer.seconds<=LOW_TIME?RED:YELLOW;
        return GOLD;
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
                if(UtilitySettings.teamBarRoundedHeads&&!roundedTexel(col,row))continue;
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

    /**
     * The face is drawn from an eight-by-eight grid, so rounding is done by leaving texels out. One
     * texel at each corner is the smallest cut the grid allows, and it reads as a clipped edge
     * rather than a circle. Below eight pixels a head is one averaged swatch, where a corner would
     * be invisible.
     */
    private static final int ROUND_CUT=1;

    static boolean roundedTexel(int col,int row){
        boolean side=col<ROUND_CUT||col>=8-ROUND_CUT;
        boolean edge=row<ROUND_CUT||row>=8-ROUND_CUT;
        return !(side&&edge);
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
