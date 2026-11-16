package otl.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * PGM's objective rows, compacted into one strip beside the match clock: wool states as coloured
 * squares (hollow untouched, half picked up, filled placed), other goals as their own status glyph
 * and name, and team scores as coloured numbers.
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
        /** Pixels before this part; set when the strip is laid out. */
        int space;
        Part(String text,int colour,int state){this.text=text;this.colour=colour;this.state=state;}
        int width(FontRenderer font){return text==null?ICON:font.getStringWidth(text);}
    }

    static final class Strip {
        final List<Part> parts;
        final int width;
        Strip(List<Part> parts,int width){this.parts=parts;this.width=width;}
    }

    /** The strip for the current sidebar, or null when it holds no objective row. */
    static Strip strip(Minecraft mc,List<TeamBar.Side> sides){
        List<String> teams=new ArrayList<>();
        for(TeamBar.Side side:sides)teams.add(side.name);
        List<List<Part>> entries=entries(TeamBar.sidebarRows(mc),teams);
        if(entries.isEmpty())return null;
        FontRenderer font=mc.fontRendererObj;
        // Never let objectives take over the screen: the clock stays the bar's middle.
        int max=Math.max(40,new ScaledResolution(mc).getScaledWidth()/2);
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
    static List<List<Part>> entries(List<String> rows,List<String> teams){
        List<List<Part>> found=new ArrayList<>();
        for(String row:rows){
            Line line=line(row);
            String text=line.plain.trim();
            if(text.isEmpty()||TeamBar.clock(text))continue;
            List<Part> entry=null;
            if(woolRow(text))entry=wools(line);
            else if(header(text,teams)>=0)entry=Collections.singletonList(new Part(text,TeamBar.chatColour(line.colourAt(0)),-1));
            else{
                Matcher scoreMatch=SCORE.matcher(text);
                if(scoreMatch.matches())entry=scoreRow(line,scoreMatch,teams);
                Matcher goalMatch=GOAL.matcher(text);
                if(entry==null&&goalMatch.matches())entry=goalRow(line,text,goalMatch);
            }
            if(entry!=null&&!entry.isEmpty())found.add(entry);
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

    private static List<Part> wools(Line line){
        List<Part> parts=new ArrayList<>();
        for(int i=0;i<line.plain.length();i++){
            int state=WOOL.indexOf(line.plain.charAt(i));
            if(state>=0)parts.add(new Part(null,TeamBar.chatColour(line.colourAt(i)),state));
        }
        return parts;
    }

    /** "5 Red": the score, then the team's own coloured name. */
    private static List<Part> scoreRow(Line line,Matcher score,List<String> teams){
        String name=score.group(3).trim();
        if(team(name,teams)<0)return null; // a number that names no team is not a score
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(score.group(1)+(score.group(2)==null?"":"/"+score.group(2)),TeamBar.WHITE,-1));
        parts.add(new Part(name,TeamBar.chatColour(line.colourOf(name)),-1));
        return parts;
    }

    /** "✔ North Monument": the status in its own colour, then the goal's name. */
    private static List<Part> goalRow(Line line,String text,Matcher goal){
        List<Part> parts=new ArrayList<>();
        parts.add(new Part(goal.group(1),TeamBar.chatColour(line.colourAt(line.plain.indexOf(text))),-1));
        parts.add(new Part(label(goal.group(2)),TeamBar.WHITE,-1));
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
