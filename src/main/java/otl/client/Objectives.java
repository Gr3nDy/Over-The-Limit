package otl.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * PGM's objective rows, compacted for the team bar: wool states as coloured squares (hollow
 * untouched, half picked up, filled placed), other goals as their progress, and team scores as
 * numbers.
 *
 * Everything is read from the sidebar PGM already draws, so the strip can never invent a state the
 * server did not send, and a mode with no goals simply has no strip. The glyphs and their colours
 * are PGM's own, which is why a Capture the Wool row turns into the same squares the sidebar shows.
 */
final class Objectives {
    /** Square size and spacing; the strip is exactly one text line tall. */
    static final int ICON=7,SPACE=2,ENTRY_GAP=8,HEIGHT=8;
    /** PGM's wool state glyphs in order: untouched, picked up, placed. */
    static final String WOOL="\u2B1C\u2592\u2B1B";
    /** PGM's score row: "5", "5/10", then the team's display name. */
    private static final Pattern SCORE=Pattern.compile("^(\\d{1,3})(?:/(\\d{1,3}))?\\s+(\\S.*)$");
    /** PGM's goal row: a status glyph, a progress fraction or a percentage, then the goal's name. */
    private static final Pattern GOAL=Pattern.compile("^((?:\\u2714|\\u2715|[\\u2B1C\\u2592\\u2B1B]|\\d{1,3}/\\d{1,3}%?|\\d{1,3}%)(?:\\s+\\d{1,3}%)?)\\s+(\\S.*)$");

    /** One piece of the strip: a wool square (text null) or a run of text in one colour. */
    static final class Part {
        final String text;
        final int colour;
        /** 0 untouched, 1 picked up, 2 placed; -1 for text. */
        final int state;
        /** True for the objective's own state, false for a name or a team header. */
        final boolean marker;
        /** True when the part only belongs beside the clock (compact mode), never in the named strip. */
        final boolean beside;
        /** What this part is, for the hover tooltip; null when it has no name of its own. */
        final String name;
        /** Pixels before this part; set when the strip is laid out. */
        int space;
        Part(String text,int colour,int state,boolean marker,boolean beside,String name){
            this.text=text;this.colour=colour;this.state=state;this.marker=marker;this.beside=beside;this.name=name;
        }
        Part(String text,int colour,int state,boolean marker,String name){this(text,colour,state,marker,false,name);}
        Part(String text,int colour,int state,boolean marker){this(text,colour,state,marker,false,null);}
        Part(String text,int colour,int state){this(text,colour,state,false,false,null);}
        int width(FontRenderer font){return text==null?ICON:font.getStringWidth(text);}
    }

    /** One sidebar row, and the team it belongs to (null when the row names no team). */
    static final class Entry {
        final List<Part> parts;
        final String team;
        Entry(List<Part> parts,String team){this.parts=parts;this.team=team;}
    }

    static final class Strip {
        final List<Part> parts;
        final int width;
        Strip(List<Part> parts,int width){this.parts=parts;this.width=width;}
    }

    /** Every objective row under the clock, with its name, or null when there is none. */
    static Strip strip(Minecraft mc,List<TeamBar.Side> sides){
        List<Entry> entries=entries(TeamBar.sidebarRows(mc),names(sides));
        List<List<Part>> rows=new ArrayList<>();
        for(Entry entry:entries){
            List<Part> named=new ArrayList<>();
            for(Part part:entry.parts)if(!part.beside)named.add(part);
            if(!named.isEmpty())rows.add(named);
        }
        if(rows.isEmpty())return null;
        return lay(mc,rows,Math.max(40,new ScaledResolution(mc).getScaledWidth()/2));
    }

    /**
     * Compact mode: only the objectives themselves - squares, progress and scores - split into the
     * two sides of the bar so each team's objectives sit beside the clock on that team's own side.
     * The caller gives the column of every team, since the bar decides which side a team is on.
     */
    static Strip[] compact(Minecraft mc,List<TeamBar.Side> sides,Map<String,Integer> columns){
        List<List<List<Part>>> rows=split(entries(TeamBar.sidebarRows(mc),names(sides)),columns);
        // Half the screen each, so one busy side cannot swallow the other's room.
        int max=Math.max(24,new ScaledResolution(mc).getScaledWidth()/4);
        return new Strip[]{lay(mc,rows.get(0),max),lay(mc,rows.get(1),max)};
    }

    /** Markers only, grouped into the two sides of the bar by the team each row belongs to. */
    static List<List<List<Part>>> split(List<Entry> entries,Map<String,Integer> columns){
        List<List<List<Part>>> rows=new ArrayList<>();
        rows.add(new ArrayList<List<Part>>());
        rows.add(new ArrayList<List<Part>>());
        for(Entry entry:entries){
            List<Part> markers=new ArrayList<>();
            for(Part part:entry.parts)if(part.marker)markers.add(part);
            if(markers.isEmpty())continue;
            Integer column=entry.team==null?null:columns.get(MapInfo.norm(entry.team));
            rows.get(column!=null&&column==1?1:0).add(markers);
        }
        return rows;
    }

    private static List<String> names(List<TeamBar.Side> sides){
        List<String> teams=new ArrayList<>();
        for(TeamBar.Side side:sides)teams.add(side.name);
        return teams;
    }

    /** The name of the compact objective at this point in the strip, or null. */
    static String at(Strip strip,int x,int y,FontRenderer font){
        if(strip==null||y<0||y>=HEIGHT)return null;
        int px=0;
        for(Part part:strip.parts){
            px+=part.space;
            int w=part.width(font);
            if(x>=px&&x<px+w)return part.name;
            px+=w;
        }
        return null;
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

    /** Objective rows from a sidebar snapshot, top to bottom, one entry per row. */
    static List<Entry> entries(List<String> rows,List<String> teams){
        List<Entry> found=new ArrayList<>();
        String owner=null; // the team named by the last header, whose goals follow it
        for(String row:rows){
            Line line=line(row);
            String text=line.plain.trim();
            if(text.isEmpty()||TeamBar.clock(text))continue;
            int head=header(text,teams);
            if(head>=0){
                owner=teams.get(head);
                found.add(new Entry(Arrays.<Part>asList(new Part(text,TeamBar.chatColour(line.colourAt(0)),-1)),owner));
                continue;
            }
            List<Part> parts=null;
            String team=owner;
            if(woolRow(text))parts=wools(line,team);
            else{
                Matcher scoreMatch=SCORE.matcher(text);
                if(scoreMatch.matches()){
                    int named=team(scoreMatch.group(3).trim(),teams);
                    if(named>=0){
                        team=teams.get(named);
                        parts=scoreRow(line,scoreMatch,team);
                    }
                }
                Matcher goalMatch=GOAL.matcher(text);
                if(parts==null&&goalMatch.matches())parts=goalRow(line,text,goalMatch);
            }
            if(parts!=null&&!parts.isEmpty())found.add(new Entry(parts,team));
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

    /** "5 Red": the score, then the team's own coloured name. */
    private static List<Part> scoreRow(Line line,Matcher score,String team){
        String name=score.group(3).trim();
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(score.group(1)+(score.group(2)==null?"":"/"+score.group(2)),TeamBar.WHITE,-1,true,team));
        parts.add(new Part(name,TeamBar.chatColour(line.colourOf(name)),-1));
        return parts;
    }

    /**
     * A goal row: its label first, then its state marker - a coloured wool square, or the progress
     * number. PGM's tick and cross glyphs say nothing at this size, so they are dropped.
     */
    private static List<Part> goalRow(Line line,String text,Matcher goal){
        String status=goal.group(1);
        int at=line.plain.indexOf(text);
        int state=status.length()==1?WOOL.indexOf(status.charAt(0)):-1;
        String name=label(goal.group(2));
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(name,TeamBar.WHITE,-1));
        // The wool square keeps the colour PGM gave the status, since that is the wool's own colour.
        if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(at)),state,true,false,name));
        else if(!status.isEmpty()&&"\u2714\u2715".indexOf(status.charAt(0))<0)
            parts.add(new Part(status,TeamBar.chatColour(line.colourAt(at)),-1,true,false,name));
        else if(!status.isEmpty())
            // A tick or a cross says nothing beside a name, but it is the goal's whole state when
            // the name is dropped, so it survives compact mode only.
            parts.add(new Part(status,TeamBar.chatColour(line.colourAt(at)),-1,true,true,name));
        return parts;
    }

    /** A goal name kept short, so one long name cannot push the strip off the screen. */
    private static String label(String text){return text.length()>14?text.substring(0,13)+".":text;}

    /** Index of the team a row names; PGM prints the team's display name, not its id. */
    private static int team(String name,List<String> teams){
        String key=MapInfo.norm(name);
        if(key.isEmpty())return -1;
        for(int i=0;i<teams.size();i++){
            String team=MapInfo.norm(teams.get(i));
            if(!team.isEmpty()&&(key.equals(team)||key.startsWith(team)))return i;
        }
        return -1;
    }

    /**
     * Index of the team a row is named after exactly. PGM puts a bare team name above that team's
     * goals, which is the only thing that says whose wools a compact wool row belongs to.
     */
    private static int header(String text,List<String> teams){
        String key=MapInfo.norm(text);
        if(key.isEmpty())return -1;
        for(int i=0;i<teams.size();i++)if(key.equals(MapInfo.norm(teams.get(i))))return i;
        return -1;
    }

    /** Visible text plus the chat colour in force at each visible character. */
    static final class Line {
        final String plain;
        private final char[] colours;
        Line(String plain,char[] colours){this.plain=plain;this.colours=colours;}
        char colourAt(int index){return index>=0&&index<colours.length?colours[index]:'f';}
        char colourOf(String text){
            int at=plain.indexOf(text);
            return colourAt(at);
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
