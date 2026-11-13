package otl.client;

import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Replaces the server's vote book with the custom VoteScreen. PGM opens the book from a held
 * written book; a book that is not a map vote is passed through untouched.
 *
 * Book format (PGM VotingBookCreatorImpl): one page of "✕/✔ MapName" entries, each with a
 * /votenext command click and a gamemode/tag hover. ✔ marks the viewer's own votes.
 */
final class VoteBook {
    static final VoteBook INSTANCE=new VoteBook();
    /** Click value of the map the local player clicked; highlights it across book updates. */
    static String votedCommand="";
    /** Normalized map name -> vote count, from the "[✕ N] Map by Authors" chat block. */
    static final Map<String,Integer> standings=new HashMap<>();
    /** Latest Match Stats block, captured live: category -> value and every tied player. */
    static final Map<String,Stat> matchStats=new HashMap<>();
    /** Supervotes the player has this cycle; -1 until the server says. */
    static int superVotes=-1;
    /** Match link from the same block; null until one arrives. */
    static String matchLink;
    /** Last book or standings activity; the vote screen auto-closes when this goes stale. */
    static long lastUpdate;
    static final long AUTO_CLOSE_NANOS=15_000_000_000L;
    /** Legacy PGM sends the vote list as chat lines instead of opening a book. */
    private static final List<Card> collected=new ArrayList<>();
    private static boolean collecting;
    private static long collectAt;
    private Object world;
    private static final long COLLECT_SETTLE_NANOS=600_000_000L;
    /** An unfinished vote list is dropped after this long, so it cannot open later by surprise. */
    private static final long COLLECT_EXPIRE_NANOS=10_000_000_000L;
    static final String VOTED_SYMBOL="\u2714";
    private static final Pattern COUNT=Pattern.compile("\\s*\\((\\d+)\\)\\s*$");
    private static final Pattern SYMBOL=Pattern.compile("([\u2714\u2713\u2715\u2716\u2717\u2718\u2611\u2612])\\s*");
    private static final Pattern STANDINGS=Pattern.compile("^\\[[\u2714\u2715\u2717\u2718xX\u00d7]\\s*(\\d+)\\]\\s*(.+)$");
    /** "Most kills: 47 by Alice", "Longest Shot: 53 blocks by X", "Damage: 177.73\u2764 by X". */
    private static final Pattern STAT=Pattern.compile("^\\s*(?:Most\\s+)?(Kills?|Deaths?|Assists?|Damage(?:\\s+dealt)?|Killstreak|Longest\\s+shot(?:\\s+distance)?)\\s*[:=-]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:blocks?|hearts?|hp)?\\s*[^\\w\\s]*\\s*(?:by\\s+)?(.+?)\\s*$",Pattern.CASE_INSENSITIVE);
    /** PGM's own tag inside a stats name: "(You)", or "(You: 12)" carrying your own value. */
    private static final Pattern YOU=Pattern.compile("(?i)\\(\\s*you\\b([^)]*)\\)?");
    private static final Pattern LINK=Pattern.compile("Match link\\s*:\\s*(https?://\\S+)");
    private static final Pattern SUPERVOTES=Pattern.compile("(?i)(?:super\\s*votes?|votes?\\s*left)\\s*[:x-]?\\s*(\\d+)|(?:\\b(\\d+)\\s+super\\s*votes?)");
    private VoteBook(){}

    /** Prevent vote counts, stats, and selected votes leaking across maps or relogs. */
    void reset(){
        standings.clear();matchStats.clear();matchLink=null;votedCommand="";superVotes=-1;
        collected.clear();collecting=false;collectAt=0;
    }

    /** New vote cycle: drop votes, but keep the finished match's stats and link on screen. */
    private void resetVotes(){
        standings.clear();votedCommand="";
        collected.clear();collecting=false;collectAt=0;
    }

    private void syncWorld(){
        Minecraft mc=MinecraftAccess.get();
        if(world!=mc.theWorld){world=mc.theWorld;reset();}
    }

    @SubscribeEvent public void onGuiOpen(GuiOpenEvent event){
        if(!(event.gui instanceof GuiScreenBook))return;
        syncWorld();
        UtilitySettings.load();
        if(!UtilitySettings.customVotebook){System.out.println("OTL: vote book kept vanilla (Custom Votebook is off)");return;}
        Book book=parse((GuiScreenBook)event.gui);
        if(book==null){System.out.println("OTL: vote book kept vanilla (not recognised as a map vote)");return;}
        // A newly opened vote book is a new vote cycle; keep the stats block that preceded it.
        resetVotes();
        event.setCanceled(true);
        lastUpdate=System.nanoTime();
        System.out.println("OTL: custom votebook replaced the book with "+book.cards.size()+" maps");
        MinecraftAccess.get().displayGuiScreen(new VoteScreen(book));
    }

    /** Hide the rest of the HUD while the vote screen is open; it shows everything it needs. */
    @SubscribeEvent public void hideHud(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event){
        // ALL must not be cancelled: that call also sets up the 2D projection the screen draws in.
        if(MinecraftAccess.get().currentScreen instanceof VoteScreen
            &&event.type!=net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL)event.setCanceled(true);
    }

    /** Chat is a separate event; cancelling it also silences the Post(CHAT) widgets. */
    @SubscribeEvent public void hideChat(net.minecraftforge.client.event.RenderGameOverlayEvent.Chat event){
        if(MinecraftAccess.get().currentScreen instanceof VoteScreen)event.setCanceled(true);
    }

    /** ReliableChat cancels server chat at LOWEST priority, so this must accept cancelled events. */
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true) public void receive(ClientChatReceivedEvent event){
        if(event.type==2)return;
        syncWorld();
        String plain=ChatRules.plain(event.message.getFormattedText());
        String text=plain.trim();
        if(text.contains("Match Stats")){matchStats.clear();matchLink=null;return;} // a fresh block begins
        stat(plain,event.message);
        Matcher sv=SUPERVOTES.matcher(plain);
        if(sv.find())superVotes=Integer.parseInt(sv.group(1)!=null?sv.group(1):sv.group(2));
        Matcher l=LINK.matcher(text);
        if(l.find())matchLink=l.group(1);
        Matcher m=STANDINGS.matcher(text);
        if(m.matches()){
            String key=mapKey(m.group(2));
            if(key!=null){
                standings.put(key,Integer.valueOf(m.group(1)));
                lastUpdate=System.nanoTime();
            }
        }
        // Legacy PGM sends the vote list as chat: the header, then one "✕/✔ MapName" line per
        // map. Each line normally carries a /votenext click; without one the name still works.
        if(text.contains("maps you want to play")){
            resetVotes();collecting=true;collectAt=System.nanoTime();
            if(!text.contains("\n"))return; // header alone; a combined message keeps parsing below
        }
        List<Card> found=cardsFrom(event.message);
        if(found.isEmpty()){
            for(String part:text.split("\n")){
                Card card=lineCard(part.trim());
                if(card!=null)found.add(card);
            }
        }
        if(found.isEmpty())return;
        if(!collecting){collected.clear();collecting=true;}
        for(Card card:found)merge(card);
        collectAt=System.nanoTime()+COLLECT_SETTLE_NANOS;
    }

    /** One "Most kills: 47 by Player" row; false when the line is not a stat row. */
    static boolean stat(String text){return stat(text,null);}

    /** Stats row with the chat component, so the players' names keep their colours. */
    static boolean stat(String text,IChatComponent message){
        Matcher s=STAT.matcher(text);
        if(!s.matches())return false;
        String key=s.group(1).replaceAll("\\s+"," ").replaceAll("(?i)\\s+dealt$|(?i)\\s+distance$","");
        key=Character.toUpperCase(key.charAt(0))+key.substring(1).toLowerCase(Locale.ROOT);
        String value=s.group(2);
        List<String> names=splitPlayers(message==null?s.group(3):formattedTail(message,s.start(3)));
        if(names.isEmpty())return false;
        Stat stat=matchStats.get(key);
        if(stat==null||!stat.value.equals(value)){stat=new Stat(value);matchStats.put(key,stat);}
        for(String display:names){
            // PGM tags your own row inside the name, either "(You)" or "(You: 12)" with your own
            // value; the screen draws that on its own last line instead. The tag carries its own
            // colour codes, so they are trimmed with it.
            String tagged=ChatRules.plain(display);
            String own=youTag(tagged);
            if(own!=null)stat.you=own;
            String plain=withoutYouTag(tagged).trim();
            display=withoutYouTag(display);
            if(!plain.isEmpty())stat.add(plain,display);
        }
        return true;
    }

    /** Players in a stats line, splitting ties on ", " / " and " / "&" while keeping chat colours. */
    private static List<String> splitPlayers(String styled){
        List<String> out=new ArrayList<>();
        StringBuilder current=new StringBuilder(),style=new StringBuilder();
        for(int i=0;i<styled.length();){
            char c=styled.charAt(i);
            if(c=='\u00a7'&&i+1<styled.length()){
                char code=Character.toLowerCase(styled.charAt(i+1));
                if(((code>='0'&&code<='9')||(code>='a'&&code<='f')||code=='r'))style.setLength(0);
                style.append(c).append(styled.charAt(i+1));
                // A colour change in the middle of a name belongs to that name, so it goes into
                // the current name too. Dropping it here flattened every name to its first colour.
                if(current.length()>0)current.append(c).append(styled.charAt(i+1));
                i+=2;continue;
            }
            boolean separator=c==','||c=='&'||(c==' '&&styled.regionMatches(true,i," and ",0,5));
            if(separator){
                if(current.length()>0){out.add(current.toString().trim());current.setLength(0);}
                i+=c==' '?5:1;
                while(i<styled.length()&&styled.charAt(i)==' ')i++;
                continue;
            }
            if(current.length()==0)current.append(style);
            current.append(c);i++;
        }
        if(current.length()>0)out.add(current.toString().trim());
        return out;
    }

    /** Styled tail of a chat line from a visible-character offset, so the name's colour survives. */
    private static String formattedTail(IChatComponent message,int start){
        String formatted=message.getFormattedText();
        if(start<=0)return formatted;
        StringBuilder style=new StringBuilder();
        int visible=0;
        for(int i=0;i<formatted.length();i++){
            char c=formatted.charAt(i);
            if(c=='\u00a7'&&i+1<formatted.length()){
                char code=Character.toLowerCase(formatted.charAt(i+1));
                if(((code>='0'&&code<='9')||(code>='a'&&code<='f')||code=='r'))style.setLength(0);
                style.append(c).append(formatted.charAt(i+1));
                i++;continue;
            }
            if(visible==start)return style+formatted.substring(i);
            visible++;
        }
        return "";
    }

    /**
     * True when this stat's displayed name is the local player. Stat rows carry the rank prefix
     * ("[MOD] Alice"), so the local name is matched at the end on a word boundary.
     */
    static boolean you(String plain,String local){
        if(local==null||plain==null||local.isEmpty())return false;
        String name=withoutYouTag(ChatRules.plain(plain)).trim();
        int at=name.length()-local.length();
        return at>=0&&name.regionMatches(true,at,local,0,local.length())
            &&(at==0||!Character.isLetterOrDigit(name.charAt(at-1)));
    }

    /**
     * Drops PGM's "(You)" / "(You: 12)" marker, which it writes inside the name with its own
     * colour codes. The whole tag has to go: stripping only "(You" left the value glued to the
     * name as ": 12)", which is what showed up as "Name: 12" in the vote screen.
     */
    static String withoutYouTag(String text){
        return text==null?null:text.replaceAll("(?i)(?:\u00a7.)*\\s*\\(\\s*you\\b[^)]*\\)?","");
    }

    /**
     * Your own value from that tag: "" for a plain "(You)", "12" for "(You: 12)", null when the
     * name carries no tag. The value sits after the "you" word, usually behind a colon.
     */
    static String youTag(String text){
        if(text==null)return null;
        Matcher m=YOU.matcher(text);
        if(!m.find())return null;
        String value=m.group(1)==null?"":m.group(1).trim();
        if(value.startsWith(":"))value=value.substring(1).trim();
        return value;
    }

    /** Which tied player to show as the column's model: you, when you are in the tie. */
    static int pick(List<String> names,String local){
        if(local!=null)for(int i=0;i<names.size();i++)if(you(names.get(i),local))return i;
        return 0;
    }

    /** One Match Stats category: the value and every player tied on it. */
    static final class Stat {
        final String value;
        final List<String> names=new ArrayList<>();
        final List<String> styled=new ArrayList<>();
        /** Your own value from a "(You: 12)" tag, "" for a plain "(You)", null when untagged. */
        String you;
        Stat(String value){this.value=value;}
        void add(String plain,String display){
            for(String known:names)if(known.equalsIgnoreCase(plain))return;
            names.add(plain);styled.add(display);
        }
    }

    /** One card per map name; a later line for the same map replaces the earlier one. */
    private static void merge(Card card){addCard(collected,card);}

    private static void addCard(List<Card> cards,Card card){
        String key=MapInfo.norm(card.text);
        for(int i=0;i<cards.size();i++)if(MapInfo.norm(cards.get(i).text).equals(key)){cards.set(i,card);return;}
        if(cards.size()<8)cards.add(card);
    }

    /** A vote line with no click event: "✕ MapName", optionally behind a short chat prefix. */
    static Card lineCard(String text){
        if(text.startsWith("["))return null; // standings block: "[✕ 1] Map by Author"
        Matcher m=SYMBOL.matcher(text);
        if(!m.find()||m.start()>20)return null;
        String name=text.substring(m.end()).trim();
        if(name.isEmpty()||name.length()>64)return null;
        Card card=new Card();
        card.voted=VOTED_SYMBOL.equals(m.group(1));
        card.text=name;
        card.action=ClickEvent.Action.RUN_COMMAND;
        card.click="/votenext -o "+name;
        return card;
    }

    /** Opens the custom screen once the whole chat vote list has arrived and no menu is open. */
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        syncWorld();
        if(!collecting)return;
        long now=System.nanoTime();
        if(now>collectAt+COLLECT_EXPIRE_NANOS){collecting=false;collected.clear();return;} // list never completed
        if(collected.size()<2||now<collectAt)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.currentScreen!=null||mc.thePlayer==null)return;
        UtilitySettings.load();
        collecting=false;
        if(!UtilitySettings.customVotebook){System.out.println("OTL: vote list seen but the Custom Votebook module is off");return;}
        Book book=new Book();
        book.title="Vote for the next map";
        book.cards.addAll(collected);
        readStats(book);
        lastUpdate=now;
        System.out.println("OTL: custom votebook opened with "+book.cards.size()+" maps");
        mc.displayGuiScreen(new VoteScreen(book));
    }

    /** Cards in one chat message: consecutive runs sharing a command merge into one map. */
    private static List<Card> cardsFrom(IChatComponent message){
        List<Run> runs=new ArrayList<>();
        flatten(message,null,null,runs);
        List<Card> cards=new ArrayList<>();
        Pending pending=null;
        for(Run run:runs){
            String text=ChatRules.plain(run.text.getUnformattedText()).trim();
            boolean votable=run.click!=null&&run.click.getAction()==ClickEvent.Action.RUN_COMMAND&&run.click.getValue().contains("votenext");
            if(!votable){pending=null;continue;}
            if(pending!=null&&pending.same(run.click)){pending.text.append(' ').append(text);continue;}
            if(pending!=null)cards.add(pending.card());
            pending=new Pending(text,run.click,run.hover);
        }
        if(pending!=null)cards.add(pending.card());
        return cards;
    }

    /** Catalog key for a chat map line ("Name by Authors"); null when no map matches it. */
    private static String mapKey(String line){
        MapPreview.Entry entry=MapPreview.resolveEntry(line);
        if(entry==null)return null;
        return MapInfo.norm(entry.displayName==null||entry.displayName.isEmpty()?entry.folder:entry.displayName);
    }

    /** Parses the held written book into vote cards; null when it is not a vote book. */
    static Book parse(){return parse(null);}

    /**
     * Parses the book the given screen is showing, falling back to the held item. The screen's
     * own stack matters: a book opened from any other slot is not the equipped one.
     */
    static Book parse(GuiScreenBook screen){
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null)return null;
        ItemStack stack=screenBook(screen);
        if(stack==null)stack=mc.thePlayer.getCurrentEquippedItem();
        if(stack==null||(stack.getItem()!=Items.written_book&&stack.getItem()!=Items.writable_book))return null;
        NBTTagCompound nbt=stack.getTagCompound();
        if(nbt==null)return null;
        NBTTagList pages;
        try{pages=nbt.getTagList("pages",8);}catch(Throwable bad){return null;}
        Book book=new Book();
        StringBuilder all=new StringBuilder(nbt.getString("title")).append('\n');
        List<Run> runs=new ArrayList<>();
        for(int i=0;i<pages.tagCount();i++){
            String page=pages.getStringTagAt(i);
            all.append(page).append('\n');
            IChatComponent component;
            try{component=page.trim().startsWith("{")?IChatComponent.Serializer.jsonToComponent(page):new ChatComponentText(page);}
            catch(Exception bad){component=new ChatComponentText(page);}
            flatten(component,null,null,runs);
        }
        String plain=all.toString();
        Matcher superVotes=SUPERVOTES.matcher(plain);
        if(superVotes.find())book.superVotes=Integer.parseInt(superVotes.group(1)!=null?superVotes.group(1):superVotes.group(2));
        Pending pending=null;
        for(Run run:runs){
            String text=ChatRules.plain(run.text.getUnformattedText()).trim();
            boolean clickable=run.click!=null&&(run.click.getAction()==ClickEvent.Action.RUN_COMMAND||run.click.getAction()==ClickEvent.Action.OPEN_URL);
            if(!clickable){
                if(pending==null&&book.title.isEmpty()&&text.toLowerCase(Locale.ROOT).contains("vote"))book.title=text;
                pending=null;continue;
            }
            if(pending!=null&&pending.same(run.click)){pending.text.append(text);continue;}
            if(pending!=null)book.cards.add(pending.card());
            pending=new Pending(text,run.click,run.hover);
        }
        if(pending!=null)book.cards.add(pending.card());
        if(book.cards.size()<2){
            // PGM's real vote book is often plain formatted text with no click events.
            book.cards.clear();
            StringBuilder visible=new StringBuilder();
            for(Run run:runs)visible.append(run.text.getUnformattedText());
            for(String line:visible.toString().split("\\r?\\n")){
                Card card=lineCard(line.trim());
                if(card!=null)addCard(book.cards,card);
            }
        }
        if(book.cards.size()<2)return null;
        readStats(book);
        return book;
    }

    /** The ItemStack a vanilla book screen was opened with; null when the field cannot be read. */
    private static ItemStack screenBook(GuiScreenBook screen){
        if(screen==null)return null;
        // ReflectionHelper remaps MCP names to SRG, which misses in dev; read the field directly.
        for(String name:new String[]{"bookObj","field_146474_h"}){
            try{
                java.lang.reflect.Field field=GuiScreenBook.class.getDeclaredField(name);
                field.setAccessible(true);
                return (ItemStack)field.get(screen);
            }catch(Throwable ignored){}
        }
        return null;
    }

    /** Match Stats captured live in receive(); the vote screen reads the latest block. */
    private static void readStats(Book book){
        book.stats.putAll(matchStats);
        book.matchLink=matchLink;
        book.superVotes=superVotes;
    }

    private static final class Run {
        final IChatComponent text;final ClickEvent click;final HoverEvent hover;
        Run(IChatComponent text,ClickEvent click,HoverEvent hover){this.text=text;this.click=click;this.hover=hover;}
    }

    /** Flattens the tree, passing click/hover down from parents (PGM styles only the root). */
    private static void flatten(IChatComponent component,ClickEvent click,HoverEvent hover,List<Run> out){
        ClickEvent own=component.getChatStyle().getChatClickEvent();
        HoverEvent ownHover=component.getChatStyle().getChatHoverEvent();
        ClickEvent effective=own!=null?own:click;
        HoverEvent effectiveHover=ownHover!=null?ownHover:hover;
        if(component.getUnformattedTextForChat()!=null&&!component.getUnformattedTextForChat().isEmpty())
            out.add(new Run(component,effective,effectiveHover));
        for(IChatComponent child:component.getSiblings())flatten(child,effective,effectiveHover,out);
    }

    /** World player for a Match Stats name; decorations around real names are stripped for the match. */
    static EntityPlayer findPlayer(String chatName){
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld==null||chatName==null||chatName.isEmpty())return null;
        EntityPlayer exact=mc.theWorld.getPlayerEntityByName(chatName);
        if(exact!=null)return exact;
        String want=strip(chatName);
        if(want.length()<3)return null;
        EntityPlayer partial=null;
        for(Object o:mc.theWorld.playerEntities){
            EntityPlayer p=(EntityPlayer)o;
            String have=strip(p.getName());
            if(have.equalsIgnoreCase(want))return p;
            if(partial==null&&!have.isEmpty()&&(have.contains(want)||want.contains(have)))partial=p;
        }
        return partial;
    }

    private static String strip(String name){return name.replaceAll("[^A-Za-z0-9_]","");}

    private static final class Pending {
        final StringBuilder text;final ClickEvent click;final HoverEvent hover;
        Pending(String text,ClickEvent click,HoverEvent hover){this.text=new StringBuilder(text);this.click=click;this.hover=hover;}
        boolean same(ClickEvent other){return other.getAction()==click.getAction()&&other.getValue().equals(click.getValue());}
        Card card(){
            Card card=new Card();
            String label=text.toString().trim();
            Matcher symbol=SYMBOL.matcher(label);
            if(symbol.find()&&symbol.start()==0){
                card.voted=VOTED_SYMBOL.equals(symbol.group(1));
                label=label.substring(symbol.end()).trim();
            }
            Matcher m=COUNT.matcher(label);
            if(m.find()){card.count=Integer.parseInt(m.group(1));label=label.substring(0,m.start()).trim();}
            card.text=label;
            card.action=click.getAction();
            card.click=click.getValue();
            card.tags=hover==null||hover.getAction()!=HoverEvent.Action.SHOW_TEXT?"":ChatRules.plain(hover.getValue().getUnformattedText()).replace('\n',' ').trim();
            return card;
        }
    }

    static final class Card {
        String text,click,tags="";
        ClickEvent.Action action;
        int count=-1;
        boolean voted;
    }

    static final class Book {
        String title="";
        int superVotes=-1;
        final List<Card> cards=new ArrayList<>();
        final Map<String,Stat> stats=new HashMap<>();
        String matchLink;
    }
}
