package otl.client;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Custom vote book: map image cards with live vote counts, then the Match Stats MVP row
 * (player models for most kills/deaths/assists/damage) and the match link. Stays open
 * across count updates and auto-closes a few moments after the last update.
 */
final class VoteScreen extends GuiScreen {
    private static final String[] ROW={"Kills","Deaths","Assists","Damage","Killstreak","Longest shot"};
    /** Narrow-window labels, so a value is never cut off at 427x240. */
    private static final String[] SHORT={"Kills","Deaths","Assists","Damage","Streak","Shot"};
    private static final String[] ABBR={"Kills","Deaths","Assists","Dmg","Streak","Shot"};
    private final VoteBook.Book book;
    private final ResourceLocation[] textures;
    private final String[] keys;
    private final String[] paths;
    private java.util.List<String> catalogSeen;
    private int cardSize,x0,cardTop,titleY;
    private int linkX,linkY,linkW,doneX,doneY,doneW,doneH;
    private int superX,superY,superW,superH;
    private int statY,headScale,headH,blockH,youY;
    /** Slot whose column drew "(You)" this frame, or -1. */
    private int youSlot=-1;
    /** The exact name drawn per stat slot, for the smoke check. */
    private final String[] drawnName=new String[ROW.length];
    private boolean supervoteUsed,superReady;
    /** Hover box and tie count per stat slot: {x0,y0,x1,y1,extra,slot}. */
    private final int[][] names=new int[ROW.length][];
    /** Row indices that have data this cycle, in ROW order. */
    private final int[] shown=new int[ROW.length];
    private int shownCount;
    VoteScreen(VoteBook.Book book){
        this.book=book;
        int n=visible();
        textures=new ResourceLocation[n];
        keys=new String[n];
        paths=new String[n];
    }
    private int visible(){return Math.min(book.cards.size(),6);}

    public void initGui(){layout();}

    /**
     * Every row is measured from the window height, so any GUI scale works: the map cards
     * give up size first, then the stats block centres itself between the supervote and Done.
     */
    private void layout(){
        int n=visible();
        boolean tall=height>=320;
        titleY=8;
        headScale=tall?32:22;
        headH=headScale*9/5;              // player model height above its feet line
        blockH=headH+44;                  // model, name, "N more", value, "(You)" rows
        doneW=75;doneH=20;doneX=width/2-doneW/2;
        superH=20;
        linkY=height-(book.matchLink!=null?14:0);
        doneY=linkY-doneH-24;
        cardTop=tall?48:26;
        // Only categories the server actually sent get a column; the rest are dropped and the
        // survivors centre themselves. While the whole block is missing, all six wait as Loading.
        shownCount=0;
        boolean any=false;
        for(String cat:ROW)if(statFor(cat)!=null){any=true;break;}
        for(int i=0;i<ROW.length;i++)if(!any||statFor(ROW[i])!=null)shown[shownCount++]=i;
        // Room left for a card once the supervote, stats block and button are reserved.
        int room=doneY-cardTop-16-superH-8-blockH-6;
        int widthFit=n==0?0:(width-20-(n-1)*6)/n;
        cardSize=Math.max(24,Math.min(58,Math.min(widthFit,room)));
        x0=(width-(n*cardSize+(n-1)*6))/2;
        superW=fontRendererObj.getStringWidth(superLabel())+16;
        superX=width/2-superW/2;
        superY=cardTop+cardSize+16;
        int top=superY+superH+8;
        statY=top+Math.max(0,(doneY-6-top-blockH)/2);
    }

    private String superLabel(){
        int count=VoteBook.superVotes>=0?VoteBook.superVotes:book.superVotes;
        return supervoteUsed?"Supervote used":"Supervote"+(count>=0?" ("+count+")":"");
    }

    /** Live block first, then the snapshot taken when the book opened. */
    private VoteBook.Stat statFor(String cat){
        VoteBook.Stat stat=VoteBook.matchStats.get(cat);
        return stat!=null?stat:book.stats.get(cat);
    }

    public void updateScreen(){
        if(System.nanoTime()-VoteBook.lastUpdate>VoteBook.AUTO_CLOSE_NANOS)mc.displayGuiScreen(null);
    }

    public void drawScreen(int mx,int my,float f){
        layout();
        drawRect(0,0,width,height,0x50000000);
        drawCenteredString(fontRendererObj,"Click on all of the maps you want to play:",width/2,titleY,0xFFFFD34F);
        for(int i=0;i<visible();i++){
            VoteBook.Card card=book.cards.get(i);
            resolve(i,card);
            int x=x0+i*(cardSize+6);
            boolean voted=card.voted||(card.click!=null&&card.click.equals(VoteBook.votedCommand));
            boolean hover=mx>=x&&mx<x+cardSize&&my>=cardTop&&my<cardTop+cardSize;
            if(voted)drawRect(x-2,cardTop-2,x+cardSize+2,cardTop+cardSize+2,0xFFFFFFFF);
            else if(hover)drawRect(x-1,cardTop-1,x+cardSize+1,cardTop+cardSize+1,0xFFFFFFFF);
            if(textures[i]!=null){
                drawRect(x,cardTop,x+cardSize,cardTop+cardSize,0xFF1A1A1A);
                GlStateManager.color(1f,1f,1f,1f);
                mc.getTextureManager().bindTexture(textures[i]);
                Gui.drawModalRectWithCustomSizedTexture(x,cardTop,0,0,cardSize,cardSize,cardSize,cardSize);
            }else drawRect(x,cardTop,x+cardSize,cardTop+cardSize,0xFF232323);
            Integer count=countOf(i,card);
            if(count!=null){
                drawRect(x,cardTop+cardSize-12,x+cardSize,cardTop+cardSize,0x90000000);
                drawCenteredString(fontRendererObj,"\u00d7"+count,x+cardSize/2,cardTop+cardSize-10,0xFFFFD34F);
            }
            drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(card.text,cardSize),x+cardSize/2,cardTop+cardSize+4,0xFFFFFFFF);
        }
        // Supervote reads after the map choice, so it sits between the cards and the stats row.
        int superCount=VoteBook.superVotes>=0?VoteBook.superVotes:book.superVotes;
        String superLabel=superLabel();
        superReady=!supervoteUsed&&superCount!=0&&hasVote();
        drawRect(superX,superY,superX+superW,superY+superH,superReady?0xFF7A3FB5:0xFF4A4A4A);
        drawCenteredString(fontRendererObj,superLabel,width/2,superY+6,superReady?0xFFFFFFFF:0xFFA0A0A0);
        // Match Stats row: only categories that arrived, centred as one group.
        int spacing=Math.max(64,Math.min(118,(width-20)/Math.max(1,shownCount)));
        int feetY=statY+headH,nameY=feetY+6,labelY=nameY+10,moreY=labelY+10;
        youY=moreY+9;
        java.util.Arrays.fill(names,null);
        java.util.Arrays.fill(drawnName,null);youSlot=-1;
        for(int k=0;k<shownCount;k++){
            int slot=shown[k];
            String cat=ROW[slot];
            VoteBook.Stat stat=statFor(cat);
            int cx=width/2+(k*2-(shownCount-1))*spacing/2;
            int room=spacing-6;
            String local=mc.thePlayer==null?null:mc.thePlayer.getName();
            int pick=stat==null||stat.names.isEmpty()?0:VoteBook.pick(stat.names,local);
            EntityPlayer player=stat==null||stat.names.isEmpty()?null:VoteBook.findPlayer(stat.names.get(pick));
            GuiInventory.drawEntityOnScreen(cx,feetY,headScale,0,0,player!=null?player:placeholder());
            if(stat!=null&&!stat.names.isEmpty()){
                int extra=stat.names.size()-1;
                // The chat colours carry the rank tag and its own colours, so the whole styled
                // name is copied as-is; only the width is cut, never the §-codes.
                String styledName=pick<stat.styled.size()?stat.styled.get(pick):stat.names.get(pick);
                String shownName=clip(fontRendererObj,styledName,room);
                drawCenteredString(fontRendererObj,shownName,cx,nameY,0xFFFFFF);
                drawCenteredString(fontRendererObj,fit(new String[]{
                    "Most "+cat.toLowerCase(Locale.ROOT)+": "+stat.value,
                    SHORT[slot]+": "+stat.value,
                    ABBR[slot]+" "+stat.value},room),cx,labelY,0xFFAAAAAA);
                if(extra>0)drawCenteredString(fontRendererObj,"\u00a77and "+extra+" more...",cx,moreY,0xFFAAAAAA);
                // "(You)" sits on its own last line, so it never eats room from the rank name.
                // PGM's "(You: 12)" tag keeps your value on that line instead of inside the name.
                String own=stat.you!=null?stat.you:(VoteBook.you(stat.names.get(pick),local)?"":null);
                if(own!=null){drawCenteredString(fontRendererObj,"\u00a77(You"+(own.isEmpty()?"":": "+own)+")",cx,youY,0xFFAAAAAA);youSlot=slot;}
                drawnName[slot]=shownName;
                names[slot]=new int[]{cx-fontRendererObj.getStringWidth(shownName)/2,nameY-2,cx+fontRendererObj.getStringWidth(shownName)/2,moreY+10,extra,slot};
            }else{
                drawCenteredString(fontRendererObj,"Loading...",cx,nameY,0xFFAAAAAA);
            }
        }
        drawRect(doneX,doneY,doneX+doneW,doneY+doneH,0xFF555555);
        drawCenteredString(fontRendererObj,"Done",width/2,doneY+6,0xFFFFFFFF);
        if(book.matchLink!=null){
            String label="\u00a7n\u00a7bMatch link";
            linkW=fontRendererObj.getStringWidth("Match link");
            linkX=width/2-linkW/2;
            drawCenteredString(fontRendererObj,label,width/2,linkY,0xFFFFFFFF);
        }
        super.drawScreen(mx,my,f);
        // Tied players: the row shows "<name> and N more..."; hovering lists them all.
        for(int[] box:names){
            if(box==null||box[4]<=0||mx<box[0]-2||mx>box[2]+2||my<box[1]||my>box[3])continue;
            VoteBook.Stat stat=statFor(ROW[box[5]]);
            if(stat!=null&&stat.styled.size()>1)drawHoveringText(new ArrayList<>(stat.styled),mx,my);
            break;
        }
    }

    /**
     * Trims a chat-styled string to a pixel width, keeping every §-code. FontRenderer's own
     * trimStringToWidth throws the codes away, which flattened rank names to one colour.
     */
    static String clip(net.minecraft.client.gui.FontRenderer font,String styled,int room){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<styled.length();){
            char c=styled.charAt(i);
            if(c=='\u00a7'&&i+1<styled.length()){out.append(c).append(styled.charAt(i+1));i+=2;continue;}
            out.append(c);
            if(font.getStringWidth(ChatRules.plain(out.toString()))>room){out.setLength(out.length()-1);break;}
            i++;
        }
        return out.toString();
    }

    /** First candidate that fits the slot, else the shortest one trimmed. */
    private String fit(String[] options,int room){        for(String option:options)if(fontRendererObj.getStringWidth(option)<=room)return option;
        return fontRendererObj.trimStringToWidth(options[options.length-1],room);
    }

    /** A vote has been cast this cycle: the supervote only makes sense after that. */
    private boolean hasVote(){
        if(!VoteBook.votedCommand.isEmpty())return true;
        for(VoteBook.Card card:book.cards)if(card.voted)return true;
        return false;
    }

    public void mouseClicked(int mx,int my,int button) throws java.io.IOException{
        for(int i=0;i<visible();i++){
            VoteBook.Card card=book.cards.get(i);
            int x=x0+i*(cardSize+6);
            if(card.click!=null&&mx>=x-2&&mx<x+cardSize+2&&my>=cardTop-2&&my<cardTop+cardSize+10){
                VoteBook.votedCommand=card.click;
                VoteBook.lastUpdate=System.nanoTime();
                if(card.action==net.minecraft.event.ClickEvent.Action.RUN_COMMAND)mc.thePlayer.sendChatMessage(card.click.startsWith("/")?card.click:"/"+card.click);
                else openURL(card.click);
                return;
            }
        }
        if(book.matchLink!=null&&mx>=linkX-4&&mx<linkX+linkW+4&&my>=linkY-2&&my<linkY+12){
            openURL(book.matchLink);
            return;
        }
        if(mx>=superX&&mx<superX+superW&&my>=superY&&my<superY+superH&&superReady){
            mc.thePlayer.sendChatMessage("/supervote");supervoteUsed=true;
            VoteBook.lastUpdate=System.nanoTime();
            return;
        }
        if(mx>=doneX&&mx<doneX+doneW&&my>=doneY&&my<doneY+doneH){mc.displayGuiScreen(null);return;}
        super.mouseClicked(mx,my,button);
    }

    private static final ResourceLocation STEVE=new ResourceLocation("textures/entity/steve.png");
    private EntityPlayer placeholder;
    /** Default Steve player model for a player who is not in the world yet. */
    private EntityPlayer placeholder(){
        if(placeholder==null||placeholder.worldObj!=mc.theWorld){
            GameProfile profile=new GameProfile(UUID.nameUUIDFromBytes("otl-loading".getBytes(StandardCharsets.UTF_8)),"Loading...");
            placeholder=new EntityOtherPlayerMP(mc.theWorld,profile){
                @Override public ResourceLocation getLocationSkin(){return STEVE;}
                // Full daylight: the placeholder has no world position to light it from.
                @Override public int getBrightnessForRender(float partialTicks){return 15728880;}
            };
            placeholder.setAlwaysRenderNameTag(false);
        }
        return placeholder;
    }

    /** Same open path vanilla click events use (java.awt.Desktop#browse). */
    private static void openURL(String url){
        try{
            if(java.awt.Desktop.isDesktopSupported())java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        }catch(Exception ignored){}
    }

    /** Lazily resolves the map image and its standings key; retries while the catalog/image loads. */
    private void resolve(int i,VoteBook.Card card){
        if(textures[i]!=null)return;
        if(keys[i]==null){
            MapPreview.Entry entry=MapPreview.resolveEntry(card.text);
            if(entry==null){
                // Retry only when the remote catalog that holds non-bundled maps arrives.
                if(MapRemote.catalog==catalogSeen)return;
                catalogSeen=MapRemote.catalog;
                MapRemote.requestCatalog();
                entry=MapPreview.resolveEntry(card.text);
                if(entry==null)return;
            }
            String name=entry.displayName==null||entry.displayName.isEmpty()?entry.folder:entry.displayName;
            keys[i]=MapInfo.norm(name);
            paths[i]=entry.path;
        }
        if(paths[i]!=null)textures[i]=MapPreview.galleryTexture(paths[i]+"/map.png");
    }
    /** Standings chat first (live), then the count printed in the book line, when either exists. */
    private Integer countOf(int i,VoteBook.Card card){
        if(keys[i]!=null&&!keys[i].isEmpty()){
            Integer count=VoteBook.standings.get(keys[i]);
            if(count!=null)return count;
        }
        Integer byName=VoteBook.standings.get(MapInfo.norm(card.text));
        if(byName!=null)return byName;
        return card.count>=0?Integer.valueOf(card.count):null;
    }

    // smoke helpers
    int cardCount(){return visible();}
    int votedCount(){int n=0;for(int i=0;i<visible();i++)if(book.cards.get(i).voted)n++;return n;}
    boolean superEnabled(){return superReady;}
    int columns(){return shownCount;}
    /** Where the "(You)" line was drawn this frame; 0 until a local-player row is drawn. */
    int youLineY(){return youY;}
    /** Slot that actually drew "(You)" on its last line this frame, or -1. */
    int youSlot(){return youSlot;}
    /** The name actually drawn for a stat slot. */
    String drawnName(int slot){return drawnName[slot];}
    String matchLink(){return book.matchLink;}
    /** Renders once at the current size, then reports whether any two rows collide. */
    boolean layoutFits(){
        layout();
        if(titleY+9>cardTop)return false;                       // title clear of the cards
        if(cardTop+cardSize+4+9>superY)return false;            // map names above the supervote
        if(superY+superH>statY)return false;                    // supervote above the models
        if(statY+blockH>doneY)return false;                     // labels above Done
        if(doneY+doneH>(book.matchLink!=null?linkY:height))return false; // Done above the link
        return cardSize>=24&&x0>=0;
    }
}
