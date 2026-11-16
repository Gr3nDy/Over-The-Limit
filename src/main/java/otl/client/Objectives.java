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

    /**
     * One line per sidebar row, in the sidebar's own order, so a team's objectives read exactly
     * like its sidebar block. Compact mode has no names, so its rows pack onto one line.
     */
    static List<List<Strip>> strips(Minecraft mc,List<String> teams,Map<String,Integer> columns,boolean compact,int max){
        List<List<List<Part>>> sides=split(entries(TeamBar.sidebarRows(mc),teams),columns,compact);
        List<List<Strip>> out=new ArrayList<>();
        for(int side=0;side<2;side++){
            List<Strip> lines=new ArrayList<>();
            if(compact){
                Strip one=lay(mc,sides.get(side),max);
                if(one!=null)lines.add(one);
            }else for(List<Part> entry:sides.get(side)){
                Strip one=lay(mc,Collections.singletonList(entry),max);
                if(one!=null)lines.add(one);
            }
            out.add(lines);
        }
        return out;
    }

    /**
     * Rows grouped into the two sides of the clock by the team each belongs to. A team's side is
     * the column its heads take, so a team's objectives always sit beside that team's own row.
     */
    static List<List<List<Part>>> split(List<Entry> entries,Map<String,Integer> columns,boolean compact){
        List<List<List<Part>>> sides=new ArrayList<>();
        sides.add(new ArrayList<List<Part>>());
        sides.add(new ArrayList<List<Part>>());
        for(Entry entry:entries){
            Integer column=entry.team==null?null:columns.get(MapInfo.norm(entry.team));
            int side=column!=null&&column%2==1?1:0;
            List<Part> parts=new ArrayList<>();
            for(Part part:entry.parts)if(compact||!part.beside)parts.add(part);
            if(compact){
                List<Part> row=new ArrayList<>();
                for(Part part:parts)if(part.marker)row.add(part);
                parts=row;
            }
            if(parts.isEmpty())continue;
            // Mirror: the right side leads with the marker and the left with the name, so each
            // row's state markers always end up nearest the clock.
            sides.get(side).add(order(parts,side==1));
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

    /**
     * Objective rows from a sidebar snapshot, top to bottom, one entry per row. A row belongs to
     * the team header nearest it, above or below, so a layout that puts a team's goals before its
     * name still attributes them - a row that fell through to "no team" used to land on the left
     * side and pile every team's objectives there.
     */
    static List<Entry> entries(List<String> rows,List<String> teams){
        List<Line> lines=new ArrayList<>();
        List<String> texts=new ArrayList<>();
        List<Integer> heads=new ArrayList<>();
        for(String row:rows){
            Line line=line(row);
            String text=line.plain.trim();
            lines.add(line);texts.add(text);
            heads.add(text.isEmpty()||TeamBar.clock(text)?-1:header(text,teams));
        }
        List<Entry> found=new ArrayList<>();
        for(int i=0;i<texts.size();i++){
            String text=texts.get(i);
            if(text.isEmpty()||TeamBar.clock(text))continue;
            Line line=lines.get(i);
            int head=heads.get(i);
            if(head>=0){
                found.add(new Entry(Arrays.<Part>asList(new Part(text,TeamBar.chatColour(line.colourAt(0)),-1)),teams.get(head)));
                continue;
            }
            String team=nearest(i,heads,teams);
            List<Part> parts=null;
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

    /**
     * The team a row belongs to: the nearest header above it, since PGM prints a team's name over
     * its goals. Only when no header sits above the row does the nearest one below count, which
     * covers a sidebar that prints the goals first - those rows used to fall through to "no team"
     * and every one of them landed on the left side of the clock.
     */
    private static String nearest(int row,List<Integer> heads,List<String> teams){
        for(int at=row-1;at>=0;at--)if(heads.get(at)>=0)return teams.get(heads.get(at));
        for(int at=row+1;at<heads.size();at++)if(heads.get(at)>=0)return teams.get(heads.get(at));
        return null;
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
            if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(i)),state,true,false,team));
        }
        return parts;
    }

    /** "5 Red": the score, then the team's own coloured name. */
    private static List<Part> scoreRow(Line line,Matcher score,String team){
        String name=score.group(3).trim();
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(score.group(1)+(score.group(2)==null?"":"/"+score.group(2)),TeamBar.WHITE,-1,true,false,team));
        parts.add(new Part(name,TeamBar.chatColour(line.colourOf(name)),-1));
        return parts;
    }

    /**
     * A goal row: its state marker then its label, the order PGM's sidebar uses. PGM's tick and
     * cross glyphs say nothing beside a name, so they are dropped from the named strip; they are
     * the goal's whole state once the name is gone, so compact mode keeps them.
     */
    private static List<Part> goalRow(Line line,String text,Matcher goal){
        String status=goal.group(1);
        int at=line.plain.indexOf(text);
        int state=status.length()==1?WOOL.indexOf(status.charAt(0)):-1;
        String name=label(goal.group(2));
        List<Part> parts=new ArrayList<>();
        // The wool square keeps the colour PGM gave the status, since that is the wool's own colour.
        if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(at)),state,true,false,name));
        else if(!status.isEmpty()&&"\u2714\u2715".indexOf(status.charAt(0))<0)
            parts.add(new Part(status,TeamBar.chatColour(line.colourAt(at)),-1,true,false,name));
        else if(!status.isEmpty())
            parts.add(new Part(status,TeamBar.chatColour(line.colourAt(at)),-1,true,true,name));
        parts.add(new Part(name,TeamBar.WHITE,-1));
        return parts;
    }

    /** A goal name kept short, so one long name cannot push the strip off the screen. */
    private static String label(String text){return text.length()>14?text.substring(0,13)+".":text;}

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
    private static boolean sameTeam(String label,String team){
        String a=MapInfo.norm(label),b=MapInfo.norm(team);
        if(a.isEmpty()||b.isEmpty())return false;
        if(a.equals(b))return true;
        // "Red Team" and "red" are the same team; PGM's display name carries the word, the
        // scoreboard team does not.
        a=withoutTeamWord(a);b=withoutTeamWord(b);
        return !a.isEmpty()&&a.equals(b);
    }

    private static String withoutTeamWord(String key){
        if(key.startsWith("team")&&key.length()>4)key=key.substring(4);
        if(key.endsWith("team")&&key.length()>4)key=key.substring(0,key.length()-4);
        return key;
    }

    /** The name of the objective at this point in a strip, or null. */
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
