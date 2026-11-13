package otl.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/**
 * Row cursor for a compact settings panel. Rows stack top-down, so adding a control never means
 * renumbering the ones below it; the whole canvas scrolls as one in {@link SettingsScreen}.
 */
final class SettingsPanel {
    static final int PAD=14,ROW=20,CHOICE=34,SECTION=15,NOTE=11,GAP=4;
    private static final int RULE=0,LABEL=1,TEXT=2;
    private static final class Caption{
        final String text;final int y,kind;
        Caption(String text,int y,int kind){this.text=text;this.y=y;this.kind=kind;}
    }
    private final int x,w,top;
    private final List<Caption> captions=new ArrayList<>();
    private int cursor;

    SettingsPanel(int x,int w,int top){this.x=x;this.w=w;this.top=top;}
    int left(){return x+PAD;}
    int width(){return w-2*PAD;}
    int columnWidth(int columns){return (width()-(columns-1)*GAP)/columns;}
    int columnX(int columns,int index){return left()+index*(columnWidth(columns)+GAP);}
    int row(){return row(ROW);}
    /** Advances one row of the given control height; the shared y for a multi-column row. */
    int row(int height){int y=top+cursor;cursor+=height+GAP;return y;}
    int choice(){return row(CHOICE);}
    /** Caption plus divider; scrolls with the controls below it. */
    int section(String label){int y=row(SECTION);captions.add(new Caption(label,y,RULE));return y;}
    /** Row label for a control that shares its row, e.g. a text field on the right. */
    int label(String text){int y=row(ROW);captions.add(new Caption(text,y,LABEL));return y;}
    /** Dim explanation line. */
    int note(String text){int y=row(NOTE);captions.add(new Caption(text,y,TEXT));return y;}
    int height(){return Math.max(0,cursor-GAP);}
    void draw(FontRenderer font){
        for(Caption c:captions){
            if(c.kind==RULE)BedrockUi.section(font,c.text,left(),c.y,width());
            else if(c.kind==LABEL)font.drawString(c.text,left(),c.y+6,BedrockUi.TEXT);
            else font.drawString(c.text,left(),c.y+1,BedrockUi.DIM);
        }
    }
}
