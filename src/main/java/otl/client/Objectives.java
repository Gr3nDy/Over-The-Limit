package otl.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * PGM's objective rows, split around the match clock: each team's objectives sit on that team's
 * own side of the time, with the state icons nearest the clock so the two sides mirror each other.
 *
 * Everything is read from the sidebar PGM already draws, so the strip can never invent a state the
 * server did not send, and a mode with no goals simply has no strip. The glyphs and their colours
 * are PGM's own, which is why a Capture the Wool row turns into the same squares the sidebar shows.
 *
 * PGM prints a flag both teams can capture in a shared section with no team header. It stays
 * centred under the clock whatever its state: following the carrier made the row jump sides
 * every time the flag changed hands.
 */
final class Objectives {
    /** Square size and spacing; the strip is exactly one text line tall. */
    static final int ICON=7,SPACE=2,ENTRY_GAP=8,HEIGHT=8;
    /** PGM's wool state glyphs in order: untouched, picked up, placed. */
    static final String WOOL="\u2B1C\u2592\u2B1B";
    /** PGM's score row: "5", "5/10", then the team's display name. */
    private static final Pattern SCORE=Pattern.compile("^(\\d+)(?:\\s*/\\s*(\\d+))?\\s+(\\S.*)$");
    /**
     * PGM's goal row: a status glyph, a progress fraction or percentage, then the goal's name.
     * A destroyable prints the precise breaks after its percentage ("55% 11/20"), and a touched
     * monument prints PGM's touch glyph (U+2733) until the progress display takes over.
     */
    private static final Pattern GOAL=Pattern.compile("^((?:\\u2714|\\u2715|\\u2733|[\\u29BE\\u29BF\\u2B1C\\u2592\\u2B1B\\u2690\\u2691\\u2794]|\\d{1,3}/\\d{1,3}%?|\\d{1,3}%|\\d+)(?:\\s+(?:\\d{1,3}%|\\d{1,3}/\\d{1,3}))?)\\s+(\\S.*)$");

    static final class ScoreTracker {
        int limit=-1;
        final java.util.Set<String> flags=new java.util.HashSet<>();
        final Map<String,String> flagCarriers=new java.util.HashMap<>();
        void clear(){limit=-1;flags.clear();flagCarriers.clear();}
        void observe(List<String> rows,Map<Character,String> colours){
            int seen=-1;
            for(String row:rows){
                int[] score=TouchdownCard.scoreRow(row);
                if(score==null||!colours.containsKey((char)score[0])||score[2]<=score[1])continue;
                // Only grey, unfilled slots prove a target; filled symbols alone do not.
                if(seen>0&&seen!=score[2]){limit=-1;return;}
                seen=score[2];
            }
            if(seen>0)limit=seen;
        }
        String label(int points){return limit>0?points+"\u00a78/\u00a77"+limit:points+" pts";}
        /** Scored share of a confirmed target; negative when there is no target to fill. */
        float fraction(int points){return limit>0?Math.min(1F,(float)points/limit):-1F;}
    }

    /** PGM keeps the controller in the label colour even while another team captures the point. */
    static final class PointTracker {
        final java.util.Set<String> names=new java.util.HashSet<>();
        final Map<Character,String> colours=new java.util.HashMap<>();
        boolean pointMode;
        void clear(){names.clear();colours.clear();pointMode=false;}
        void prepare(Minecraft mc,Map<Character,String> palette){
            colours.clear();
            colours.putAll(palette);
            if(mc.theWorld==null){pointMode=false;return;}
            net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
            net.minecraft.scoreboard.ScoreObjective sidebar=board.getObjectiveInDisplaySlot(1);
            String title=sidebar==null?"":MapInfo.norm(ChatRules.plain(sidebar.getDisplayName()));
            pointMode=title.contains("kingofthehill")||title.contains("koth")||title.contains("controlpoint")
                ||title.contains("capturethepoint")||title.contains("controlthepoint");
        }
        boolean matches(String status,String name){
            String key=MapInfo.norm(name);
            boolean glyph=status.equals("\u29be")||status.equals("\u29bf");
            if(glyph||(status.contains("%")&&(pointMode||names.contains(key)))){names.add(key);return true;}
            return false;
        }
    }

    /** Resolve only unambiguous live team colours; sidebar row holders are not match teams. */
    static Map<Character,String> teamColours(Minecraft mc,List<String> teams){
        Map<Character,String> colours=new java.util.HashMap<>();
        if(mc.theWorld==null)return colours;
        java.util.Set<Character> ambiguous=new java.util.HashSet<>();
        java.util.Set<String> scored=new java.util.HashSet<>();
        scoreTeamColours(TeamBar.sidebarRows(mc),teams,colours,ambiguous,scored);
        for(net.minecraft.scoreboard.ScorePlayerTeam team:new ArrayList<>(mc.theWorld.getScoreboard().getTeams())){
            String name=team.getTeamName();
            if(!teams.contains(name)||scored.contains(name))continue;
            boolean players=false;
            for(String member:team.getMembershipCollection())if(TeamBar.realPlayer(member)){players=true;break;}
            if(!players&&!ChatRules.plain(team.getColorPrefix()).trim().isEmpty())continue;
            Line styled=line(name.replace('\u00ba','\u00a7')+team.getColorPrefix()+"x");
            char colour=styled.colourAt(styled.plain.length()-1);
            if(colour=='f'&&team.getChatFormat()!=net.minecraft.util.EnumChatFormatting.RESET)
                colour=team.getChatFormat().toString().charAt(1);
            if(colour=='f'||"0123456789abcdef".indexOf(colour)<0)continue;
            addTeamColour(colours,ambiguous,colour,name);
        }
        for(Character colour:ambiguous)colours.remove(colour);
        return colours;
    }

    /** Numeric score rows identify teams and their actual displayed colours without map guesses. */
    static void scoreTeamColours(List<String> rows,List<String> teams,Map<Character,String> colours,
                                 java.util.Set<Character> ambiguous,java.util.Set<String> scored){
        for(String row:rows){
            Line styled=line(row);String text=styled.plain.trim();Matcher score=SCORE.matcher(text);
            if(!score.matches())continue;
            int index=team(score.group(3).trim(),teams);
            if(index<0)continue;
            char colour=styled.colourAt(styled.plain.indexOf(text)+score.start(3));
            if(colour=='f'||"0123456789abcdef".indexOf(colour)<0)continue;
            String name=teams.get(index);scored.add(name);addTeamColour(colours,ambiguous,colour,name);
        }
    }

    private static void addTeamColour(Map<Character,String> colours,java.util.Set<Character> ambiguous,char colour,String name){
        String previous=colours.put(colour,name);
        if(previous!=null&&!previous.equals(name))ambiguous.add(colour);
    }

    /** One piece of the strip: a wool square (text null) or a run of text in one colour. */
    static final class Part {
        final String text;
        final int colour;
        /** 0 untouched, 1 picked up, 2 placed; -1 for text. */
        final int state;
        /** True for the objective's own state, false for a name or a team header. */
        final boolean marker;
        /** What this part is, for the hover tooltip; null when it has no name of its own. */
        final String name;
        /** Pixels before this part; set when the strip is laid out. */
        int space;
        int slotWidth;
        final boolean mirror;
        Part(String text,int colour,int state,boolean marker,String name){
            this(text,colour,state,marker,name,false);
        }
        Part(String text,int colour,int state,boolean marker,String name,boolean mirror){
            this.text=text;this.colour=colour;this.state=state;this.marker=marker;this.name=name;this.mirror=mirror;
        }
        Part(String text,int colour,int state,boolean marker){this(text,colour,state,marker,null);}
        Part(String text,int colour,int state){this(text,colour,state,false,null);}
        int width(FontRenderer font){
            return Math.max(slotWidth,inkWidth(font));
        }
        int inkWidth(FontRenderer font){
            return text==null?ICON:font.getStringWidth(text);
        }
        int inkOffset(FontRenderer font){return Math.max(0,slotWidth-inkWidth(font))/2;}
    }

    /** One sidebar row, and the team it belongs to (null when the row names no team). */
    static final class Entry {
        final List<Part> parts;
        final String team;
        final boolean controlPoint;
        final boolean shared;
        /** The team currently capturing a control point; null when none or unknown. */
        final String capturer;
        /** A team's score line, which the bar prints under the clock rather than under its heads. */
        final boolean score;
        /** A bare team-name row, which the card's own label already prints. */
        final boolean header;
        Entry(List<Part> parts,String team){this(parts,team,false);}
        Entry(List<Part> parts,String team,boolean controlPoint){this(parts,team,controlPoint,null);}
        Entry(List<Part> parts,String team,boolean controlPoint,String capturer){
            this(parts,team,controlPoint,capturer,false);
        }
        Entry(List<Part> parts,String team,boolean controlPoint,String capturer,boolean shared){
            this(parts,team,controlPoint,capturer,shared,false,false);
        }
        Entry(List<Part> parts,String team,boolean controlPoint,String capturer,boolean shared,boolean score){
            this(parts,team,controlPoint,capturer,shared,score,false);
        }
        Entry(List<Part> parts,String team,boolean controlPoint,String capturer,boolean shared,boolean score,boolean header){
            this.parts=parts;this.team=team;this.controlPoint=controlPoint;this.capturer=capturer;
            this.shared=shared;this.score=score;this.header=header;
        }
        static Entry score(List<Part> parts,String team){
            return new Entry(parts,team,false,null,false,true,false);
        }
        static Entry header(List<Part> parts,String team){
            return new Entry(parts,team,false,null,false,false,true);
        }
    }

    static final class Strip {
        final List<Part> parts;
        final int width;
        int centre;
        /** The team block this line belongs to; null for compact markers and unowned rows. */
        final String team;
        /** A team's score line, drawn directly under the clock. */
        final boolean score;
        /** A bare team-name row, which the bar drops when that team already has a card. */
        final boolean header;
        Strip(List<Part> parts,int width){this(parts,width,null,false,false);}
        Strip(List<Part> parts,int width,String team){this(parts,width,team,false,false);}
        Strip(List<Part> parts,int width,String team,boolean score){this(parts,width,team,score,false);}
        Strip(List<Part> parts,int width,String team,boolean score,boolean header){
            this.parts=parts;this.width=width;this.team=team;this.score=score;this.header=header;
            this.centre=width/2;
        }
    }

    /**
     * One line per sidebar row, in the sidebar's own order, so a team's objectives read exactly
     * like its sidebar block. Compact mode has no names, so its rows pack onto one line per team.
     */
    static List<List<Strip>> strips(Minecraft mc,List<String> teams,Map<String,Integer> columns,boolean compact,int max,int pointMax,PointTracker points){
        Map<Character,String> colours=teamColours(mc,teams);
        if(points!=null)points.prepare(mc,colours);
        List<List<Entry>> sides=splitEntries(entries(TeamBar.sidebarRows(mc),teams,points,colours,TeamBar.INSTANCE.symbolicScores),columns,compact);
        if(!compact){
            int slot=ICON;
            for(List<Entry> side:sides)for(Entry entry:side)if(!entry.score)
                for(Part part:entry.parts)if(part.marker)slot=Math.max(slot,part.inkWidth(mc.fontRendererObj));
            for(List<Entry> side:sides)for(Entry entry:side)if(!entry.score)
                for(Part part:entry.parts)if(part.marker)part.slotWidth=slot;
        }
        List<List<Strip>> out=new ArrayList<>();
        for(int side=0;side<5;side++){
            List<Strip> lines=new ArrayList<>();
            if(compact&&side<2){
                // One group per team, so a team's icons stay on their own line(s). Two teams share a
                // side on a three- or four-team map, and balancing the whole side used to split one
                // team's markers across both rows and mix them with the other team's.
                for(List<List<Part>> row:compactLines(sides.get(side),teams,side==1)){
                    Strip one=lay(mc,row,max);
                    if(one!=null)lines.add(one);
                }
            }else{
                int limit=side==2||side==3?Math.max(24,pointMax-(compact?TeamBar.stripWidth(out.get(side-2))+TeamBar.OBJECTIVE_GAP:0)):max;
                for(Entry entry:sides.get(side)){
                    List<Strip> block=namedLines(mc,entry.parts,limit);
                    if(entry.shared)for(Strip one:block){
                        int x=0;
                        for(Part part:one.parts){x+=part.space;int w=part.width(mc.fontRendererObj);if(!part.marker){one.centre=x+w/2;break;}x+=w;}
                    }
                    // Named blocks remember their team, so each sits under that team's own heads.
                    if(side<2)for(Strip one:block)lines.add(new Strip(one.parts,one.width,entry.team,entry.score,entry.header));
                    else lines.addAll(block);
                }
                if(side==2||side==3)lines=packPointLines(lines,limit);
            }
            out.add(lines);
        }
        return out;
    }

    /** Owned site names fill the available team-side width before using another line. */
    static List<Strip> packPointLines(List<Strip> entries,int max){
        List<Strip> lines=new ArrayList<>();
        List<Part> parts=new ArrayList<>();
        int width=0;
        for(Strip entry:entries){
            int gap=parts.isEmpty()?0:ENTRY_GAP;
            if(!parts.isEmpty()&&width+gap+entry.width>max){
                lines.add(new Strip(parts,width));parts=new ArrayList<>();width=0;gap=0;
            }
            for(int i=0;i<entry.parts.size();i++){
                Part original=entry.parts.get(i);
                Part copy=new Part(original.text,original.colour,original.state,original.marker,original.name,original.mirror);
                copy.slotWidth=original.slotWidth;
                copy.space=i==0?gap:original.space;
                parts.add(copy);
            }
            width+=gap+entry.width;
        }
        if(!parts.isEmpty())lines.add(new Strip(parts,width));
        return lines;
    }

    /**
     * One group of marker entries per team, in the bar's own head order. A side holds two teams on
     * a three- or four-team map, and each team's markers must be laid out on their own so they
     * never read as belonging to the block above them. PGM's sidebar sorts teams by score, so the
     * groups are taken from the bar's order instead of the order the lines arrived in.
     */
    static List<List<List<Part>>> compactGroups(List<Entry> entries,List<String> teams){
        java.util.LinkedHashMap<String,List<List<Part>>> byTeam=new java.util.LinkedHashMap<>();
        for(Entry entry:entries)
            byTeam.computeIfAbsent(entry.team==null?"":MapInfo.norm(entry.team),k->new ArrayList<>()).add(entry.parts);
        List<List<List<Part>>> groups=new ArrayList<>();
        for(String team:teams){
            List<List<Part>> group=byTeam.remove(MapInfo.norm(team));
            if(group!=null)groups.add(group);
        }
        groups.addAll(byTeam.values());
        return groups;
    }

    /** Preserve the small-strip layout exactly; larger strips use two balanced marker rows. */
    static List<List<List<Part>>> compactRows(List<List<Part>> entries){
        List<Part> markers=new ArrayList<>();
        for(List<Part> entry:entries)markers.addAll(entry);
        if(markers.size()<=3)return Collections.singletonList(entries);
        int half=(markers.size()+1)/2;
        return Arrays.asList(Collections.singletonList(markers.subList(0,half)),
            Collections.singletonList(markers.subList(half,markers.size())));
    }

    /**
     * The right side of the clock mirrors the left, so its markers run back toward the time: the
     * marker that touches the clock on the left is the first one on the right.
     */
    private static List<List<Part>> mirrorMarkers(List<List<Part>> row,boolean mirror){
        if(!mirror)return row;
        List<List<Part>> out=new ArrayList<>(row);
        Collections.reverse(out);
        return out;
    }

    /** One side's compact marker rows, already mirrored for the right side of the clock. */
    static List<List<List<Part>>> compactLines(List<Entry> side,List<String> teams,boolean mirror){
        List<List<List<Part>>> out=new ArrayList<>();
        for(List<List<Part>> team:compactGroups(side,teams))
            for(List<List<Part>> row:compactRows(team))out.add(mirrorMarkers(row,mirror));
        return out;
    }

    /**
     * Rows grouped into the two sides of the clock by the team each belongs to. A team's side is
     * the column its heads take, so a team's objectives always sit beside that team's own row.
     */
    static List<List<List<Part>>> split(List<Entry> entries,Map<String,Integer> columns,boolean compact){
        List<List<List<Part>>> sides=new ArrayList<>();
        for(List<Entry> side:splitEntries(entries,columns,compact)){
            List<List<Part>> rows=new ArrayList<>();
            for(Entry entry:side)rows.add(entry.parts);
            sides.add(rows);
        }
        return sides;
    }

    /** As split(), but keeps each row's team so a named block can sit under its own heads. */
    static List<List<Entry>> splitEntries(List<Entry> entries,Map<String,Integer> columns,boolean compact){
        List<List<Entry>> sides=new ArrayList<>();
        for(int i=0;i<5;i++)sides.add(new ArrayList<Entry>());
        for(Entry entry:entries){
            Integer column=entry.team==null?null:columns.get(MapInfo.norm(entry.team));
            if(column==null&&!entry.controlPoint&&!entry.shared)continue;
            // A shared flag belongs under the clock in every state, carried or not.
            int side=entry.shared?4:entry.controlPoint?(column==null?4:2+column%2):column%2;
            List<Part> parts=new ArrayList<>();
            parts.addAll(entry.parts);
            if(compact&&!entry.controlPoint&&side<2){
                List<Part> row=new ArrayList<>();
                for(Part part:parts)if(part.marker)row.add(part);
                parts=row;
            }
            if(parts.isEmpty())continue;
            if(side==1||side==3)for(int i=0;i<parts.size();i++){
                Part part=parts.get(i);
                if(part.marker&&part.text!=null&&"\u2794".equals(ChatRules.plain(part.text)))
                    parts.set(i,new Part(part.text,part.colour,part.state,true,part.name,true));
            }
            // Mirror: the right side leads with the marker and the left with the name, so each
            // row's state markers always end up nearest the clock.
            boolean markersFirst=side==1||side==3||side==4;
            // A neutral point sits under the clock, so its progress leans toward the team that is
            // capturing it: percentage first for a left team, name first for a right one.
            if(side==4&&entry.capturer!=null){
                Integer capturerColumn=columns.get(MapInfo.norm(entry.capturer));
                if(capturerColumn!=null)markersFirst=capturerColumn%2==0;
            }
            sides.get(side).add(new Entry(order(parts,markersFirst),entry.team,entry.controlPoint,entry.capturer,entry.shared,entry.score,entry.header));
        }
        return sides;
    }

    /** Markers first (right side) or names first (left side), each group keeping its own order. */
    private static List<Part> order(List<Part> parts,boolean markersFirst){
        List<Part> out=new ArrayList<>();
        for(Part part:parts)if(part.marker==markersFirst)out.add(part);
        for(Part part:parts)if(part.marker!=markersFirst)out.add(part);
        return out;
    }

    /** Wrap the full name in the space left after its state marker, rather than dropping the row. */
    static List<Strip> namedLines(Minecraft mc,List<Part> entry,int max){
        List<Strip> lines=new ArrayList<>();
        Part name=null;
        int markers=0;
        for(Part part:entry){
            if(!part.marker)name=part;
            else markers+=part.width(mc.fontRendererObj)+(markers==0?0:SPACE);
        }
        if(name==null){
            Strip one=lay(mc,Collections.singletonList(entry),max);
            if(one!=null)lines.add(one);
            return lines;
        }
        int room=Math.max(1,max-markers-(markers==0?0:SPACE));
        List<String> wrapped=mc.fontRendererObj.listFormattedStringToWidth(name.text,room);
        for(int i=0;i<wrapped.size();i++){
            List<Part> row=new ArrayList<>();
            for(Part part:entry){
                if(part==name)row.add(new Part(wrapped.get(i),part.colour,part.state,false,name.text));
                else if(i==0)row.add(part);
                else{
                    // Keep wrapped text in the label column without repeating the marker.
                    Part blank=new Part("",part.colour,-1,true,name.text);
                    blank.slotWidth=part.width(mc.fontRendererObj);
                    row.add(blank);
                }
            }
            Strip one=lay(mc,Collections.singletonList(row),max);
            if(one!=null)lines.add(one);
        }
        return lines;
    }

    /** Lays entries out left to right, dropping any that would not fit in max pixels. */
    private static Strip lay(Minecraft mc,List<List<Part>> entries,int max){
        FontRenderer font=mc.fontRendererObj;
        List<Part> parts=new ArrayList<>();
        int width=0;
        for(List<Part> entry:entries){
            int entryWidth=0;
            for(Part part:entry)entryWidth+=(entryWidth>0?SPACE:0)+part.width(font);
            int gap=parts.isEmpty()?0:ENTRY_GAP;
            if(width+gap+entryWidth>max)break;
            for(int i=0;i<entry.size();i++){
                Part part=entry.get(i);
                part.space=i==0?gap:SPACE;
                parts.add(part);
            }
            width+=gap+entryWidth;
        }
        return parts.isEmpty()?null:new Strip(parts,width);
    }

    /** The live sidebar's team headers define the blocks; objective names/colours never move rows. */
    static List<Entry> entries(List<String> rows,List<String> teams){
        return entries(rows,teams,null);
    }

    static List<Entry> entries(List<String> rows,List<String> teams,PointTracker points){
        return entries(rows,teams,points,points==null?Collections.<Character,String>emptyMap():points.colours);
    }

    static List<Entry> entries(List<String> rows,List<String> teams,PointTracker points,Map<Character,String> colours){
        return entries(rows,teams,points,colours,new ScoreTracker());
    }

    static List<Entry> entries(List<String> rows,List<String> teams,PointTracker points,Map<Character,String> colours,ScoreTracker scores){
        scores.observe(rows,colours);
        List<Entry> found=new ArrayList<>();
        String owner=null;
        for(String row:rows){
            Line line=line(row);
            String text=line.plain.trim();
            if(text.isEmpty()||TeamBar.clock(text))continue;
            int[] symbolic=TouchdownCard.scoreRow(row);
            if(symbolic!=null){
                String scoringTeam=colours.get((char)symbolic[0]);
                if(scoringTeam!=null){
                    String name=ChatRules.plain(scoringTeam.replace('\u00ba','\u00a7'));
                    int colour=TeamBar.chatColour((char)symbolic[0]);
                    String label=scores.label(symbolic[1]);
                    // One style only: the number itself. The card above already names the team.
                    found.add(Entry.score(Arrays.asList(new Part(label,colour,-1,true,name)),scoringTeam));
                }
                continue;
            }
            int head=header(text,teams);
            if(head>=0){
                owner=teams.get(head);
                int start=line.plain.indexOf(text);
                found.add(Entry.header(Arrays.<Part>asList(new Part(line.coloured(start,start+text.length()),TeamBar.chatColour(line.colourAt(start)),-1)),owner));
                continue;
            }
            String team=owner;
            boolean controlPoint=false;
            boolean shared=false;
            boolean isScore=false;
            String capturer=null;
            List<Part> parts=null;
            if(woolRow(text))parts=wools(line,team);
            else{
                Matcher scoreMatch=SCORE.matcher(text);
                if(scoreMatch.matches()){
                    int named=team(scoreMatch.group(3).trim(),teams);
                    if(named>=0){
                        team=teams.get(named);
                        parts=scoreRow(line,scoreMatch,team);
                        isScore=true;
                    }
                }
                Matcher goalMatch=GOAL.matcher(text);
                if(parts==null&&goalMatch.matches()){
                    String status=goalMatch.group(1),goalName=goalMatch.group(2);
                    boolean flagSymbol="\u2690\u2691\u2794".contains(status)&&Character.isLetterOrDigit(goalName.charAt(0));
                    String flagKey=MapInfo.norm(goalName);
                    if(flagSymbol)scores.flags.add(flagKey);
                    // shortcut: a mid-countdown join needs "flag" in an unseen name; use server goal metadata if exposed.
                    boolean flagTimer=status.matches("\\d+")&&(scores.flags.contains(flagKey)||flagKey.contains("flag"));
                    if((status.matches("\\d+")&&!flagTimer)||("\u2690\u2691\u2794".contains(status)&&!flagSymbol))continue;
                    parts=goalRow(line,text,goalMatch);
                    controlPoint=points!=null&&points.matches(goalMatch.group(1),goalMatch.group(2));
                    shared=team==null&&!controlPoint&&(flagTimer||flagSymbol);
                    // A shared flag's carried arrow uses the carrier's team colour in PGM.
                    if(shared){
                        if("\u2794".equals(status)){
                            char arrowColour=line.colourAt(line.plain.indexOf(text)+goalMatch.start(1));
                            // PGM blinks the carried marker black, independently of team colours.
                            String carrier=arrowColour=='0'?null:colours.get(arrowColour);
                            if(carrier!=null)scores.flagCarriers.put(flagKey,carrier);
                            team=scores.flagCarriers.get(flagKey);
                        }else team=scores.flagCarriers.get(flagKey);
                    }
                    if(controlPoint){
                        team=points.colours.get(line.colourAt(line.plain.indexOf(text)+goalMatch.start(2)));
                        // PGM prints the progress in the capturer's colour while the point is neutral.
                        capturer=points.colours.get(line.colourAt(line.plain.indexOf(text)+goalMatch.start(1)));
                    }
                }
            }
            if(parts!=null&&!parts.isEmpty())found.add(new Entry(parts,team,controlPoint,capturer,shared,isScore));
        }
        return found;
    }

    /** A compact wool row: nothing but PGM's three state glyphs, one per wool. */
    private static boolean woolRow(String text){
        boolean glyph=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c==' ')continue;
            if(WOOL.indexOf(c)<0)return false;
            glyph=true;
        }
        return glyph;
    }

    private static List<Part> wools(Line line,String team){
        List<Part> parts=new ArrayList<>();
        for(int i=0;i<line.plain.length();i++){
            int state=WOOL.indexOf(line.plain.charAt(i));
            if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(i)),state,true,team));
        }
        return parts;
    }

    /** "5 Red": the score number, with the team name as its hover label. */
    private static List<Part> scoreRow(Line line,Matcher score,String team){
        int at=line.plain.indexOf(score.group());
        int numberStart=at+score.start(1),numberEnd=at+(score.group(2)==null?score.end(1):score.end(2));
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(line.coloured(numberStart,numberEnd),TeamBar.chatColour(line.colourAt(numberStart)),-1,true,team));
        return parts;
    }

    /**
     * A goal row: preserve PGM's state marker and colour in both modes; compact hides only names.
     */
    private static List<Part> goalRow(Line line,String text,Matcher goal){
        String status=goal.group(1);
        int at=line.plain.indexOf(text);
        int state=status.length()==1?WOOL.indexOf(status.charAt(0)):-1;
        String name=goal.group(2);
        List<Part> parts=new ArrayList<>();
        // The wool square keeps the colour PGM gave the status, since that is the wool's own colour.
        if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(at)),state,true,name));
        else if(!status.isEmpty())parts.add(new Part(line.coloured(at,at+status.length()),TeamBar.chatColour(line.colourAt(at)),-1,true,name));
        int nameStart=at+goal.start(2);
        parts.add(new Part(line.coloured(nameStart,nameStart+name.length()),TeamBar.chatColour(line.colourAt(nameStart)),-1));
        return parts;
    }

    /** Index of the team a row names; PGM prints the team's display name, not its id. */
    private static int team(String name,List<String> teams){
        if(MapInfo.norm(name).isEmpty())return -1;
        for(int i=0;i<teams.size();i++)if(sameTeam(name,teams.get(i)))return i;
        return -1;
    }

    /**
     * Index of the team a row is named after exactly. PGM puts a bare team name above that team's
     * goals, which is the only thing that says whose wools a compact wool row belongs to.
     */
    private static int header(String text,List<String> teams){
        if(MapInfo.norm(text).isEmpty())return -1;
        for(int i=0;i<teams.size();i++)if(sameTeam(text,teams.get(i)))return i;
        return -1;
    }

    /**
     * Whether a sidebar label names a team. PGM prints the team's display name ("Red Team") while
     * the scoreboard team is keyed by its id ("red"), so the trailing "team" word is ignored.
     */
    static boolean sameTeam(String label,String team){
        String a=teamKey(label),b=teamKey(team);
        if(a.isEmpty()||b.isEmpty())return false;
        if(a.equals(b))return true;
        // "Red Team" and "red" are the same team; PGM's display name carries the word, the
        // scoreboard team does not.
        a=withoutTeamWord(a);b=withoutTeamWord(b);
        return !a.isEmpty()&&a.equals(b);
    }

    private static String teamKey(String name){
        if(name==null)return "";
        // Live PGM display names use a masculine-ordinal marker in place of the section sign.
        return MapInfo.norm(ChatRules.plain(name).replaceAll("(?i)\u00ba[0-9a-fk-or]",""));
    }

    private static String withoutTeamWord(String key){
        if(key.startsWith("team")&&key.length()>4)key=key.substring(4);
        if(key.endsWith("team")&&key.length()>4)key=key.substring(0,key.length()-4);
        return key;
    }

    /** The name of the objective at this point in a strip, or null. */
    static String at(Strip strip,int x,int y,FontRenderer font,int lineHeight){
        if(strip==null||y<0)return null;
        int px=0;
        for(Part part:strip.parts){
            px+=part.space;
            int w=part.width(font);
            int h=part.text==null?ICON:HEIGHT,top=(lineHeight-h)/2;
            if(x>=px&&x<px+w&&y>=top&&y<top+h)return part.name;
            px+=w;
        }
        return null;
    }

    /** Visible text plus the chat colour in force at each visible character. */
    static final class Line {
        final String plain;
        private final char[] colours;
        Line(String plain,char[] colours){this.plain=plain;this.colours=colours;}
        char colourAt(int index){return index>=0&&index<colours.length?colours[index]:'f';}
        /** The part's base colour supplies its first character; retain every later colour change. */
        String coloured(int start,int end){
            StringBuilder text=new StringBuilder();
            char colour=colourAt(start);
            for(int i=start;i<end;i++){
                char next=colourAt(i);
                if(next!=colour){text.append('\u00a7').append(next);colour=next;}
                text.append(plain.charAt(i));
            }
            return text.toString();
        }
    }

    static Line line(String legacy){
        StringBuilder plain=new StringBuilder();
        char[] colours=new char[legacy.length()];
        char colour='f';
        for(int i=0;i<legacy.length();i++){
            char c=legacy.charAt(i);
            if(c=='\u00a7'&&i+1<legacy.length()){
                char code=Character.toLowerCase(legacy.charAt(++i));
                if(code=='r')colour='f';
                else if("0123456789abcdef".indexOf(code)>=0)colour=code;
                continue;
            }
            colours[plain.length()]=colour;
            plain.append(c);
        }
        return new Line(plain.toString(),Arrays.copyOf(colours,plain.length()));
    }
}
