package otl.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.event.ClickEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import org.lwjgl.input.Keyboard;

/**
 * Bundled CommunityMaps previews for map voting books.
 *
 * The book screen is obfuscated on Lunar/Ichor, so the hovered page lines are found by
 * scanning the screen fields by type instead of by name (names are remapped at runtime).
 */
final class MapPreview {
    private static final Pattern NAME_TAG=Pattern.compile("<name>(.*?)</name>",Pattern.DOTALL);
    private static final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private static final Map<String,ResourceLocation> galleryTextures=new ConcurrentHashMap<>();
    private static final Map<String,String> nameCache=new ConcurrentHashMap<>();
    private static final Map<String,ResourceLocation> fileTextures=new ConcurrentHashMap<>();
    static volatile boolean namesReady;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"OTL Map Names");t.setDaemon(true);return t;});
    private final List<Entry> all=new ArrayList<>();
    private List<String> hoverCatalog;
    private final List<Entry> remoteEntries=new ArrayList<>();
    private final Map<String,String> chatMaps=new HashMap<>();
    static final MapPreview INSTANCE=new MapPreview();
    private String pendingQuery, responseTitle;
    private long waitingUntil;
    private Object pendingConnection;
    private GuiScreen commandScreen, inputScreen;

    static final CommandBase COMMAND=new CommandBase(){
        public String getCommandName(){return "map";}
        public String getCommandUsage(ICommandSender sender){return "/map [name]";}
        public int getRequiredPermissionLevel(){return 0;}
        public void processCommand(ICommandSender sender,String[] args){
            String command="/map"+(args.length==0?"":" "+String.join(" ",args));
            if(!INSTANCE.queueCommand(command))MinecraftAccess.get().thePlayer.sendChatMessage(command);
        }
    };

    private MapPreview(){
        for(String path:MapPreviewIndex.PATHS)if(path.endsWith("/map.png")){
            String dir=path.substring(0,path.length()-8);
            String folder=dir.substring(dir.lastIndexOf('/')+1);
            Entry e=new Entry(folder,dir);
            e.lowerFolder=folder.toLowerCase(Locale.ROOT).replace('_',' ');
            all.add(e);
            entries.put(dir,e);
        }
        worker.execute(this::loadNames);
    }

    /** Reads the display name of every bundled map in the background so hovering stays instant. */
    private void loadNames(){
        for(Entry e:all){
            String name=readMapName(e.path);
            e.displayName=name;
            e.lowerName=name==null?null:name.toLowerCase(Locale.ROOT);
        }
        namesReady=true;
    }

    private static String readMapName(String dir){
        try(InputStream in=MapPreview.class.getResourceAsStream("/assets/otl/maps/"+dir+"/map.xml")){
            if(in==null)return null;
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buf=new byte[4096];
            int read;
            while((read=in.read(buf))>0)out.write(buf,0,read);
            String xml=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            Matcher m=NAME_TAG.matcher(xml);
            if(m.find())return m.group(1).trim();
        }catch(Exception ignored){}
        return null;
    }

    /** Display name of a map folder, read from its map.xml (cached). */
    static String displayName(String path){
        if(path.startsWith("CommunityMaps/master/"))path=path.substring("CommunityMaps/master/".length());
        Entry e=entries.get(path);
        if(e!=null)return e.displayName==null?"":e.displayName;
        String cached=nameCache.get(path);
        if(cached!=null)return cached;
        String name=readMapName(path);
        nameCache.put(path,name==null?"":name);
        return name;
    }

    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post e){
        if(!bookPreview())return;
        GuiScreen screen=e.gui;
        if(screen instanceof GuiChat){
            String query=chatQueryAt(screen,e.mouseX,e.mouseY);
            if(query==null)return;
            ResourceLocation texture=chatTexture(query);
            if(texture!=null)drawPreview(screen,e.mouseX,e.mouseY,texture);
            return;
        }
        if(!(screen instanceof GuiScreenBook))return;
        MapRemote.requestCatalog();
        Hover hit=hover(screen,e.mouseX,e.mouseY);
        if(hit==null)return;
        Entry entry=hit.entry;
        ResourceLocation texture=galleryTexture(entry.path+"/map.png");
        if(texture!=null)drawPreview(screen,e.mouseX,e.mouseY,texture);
    }

    static String chatQuery(IChatComponent component){
        if(component==null||component.getChatStyle().getColor()!=EnumChatFormatting.GOLD)return null;
        ClickEvent click=component.getChatStyle().getChatClickEvent();
        if(click==null||(click.getAction()!=ClickEvent.Action.RUN_COMMAND&&click.getAction()!=ClickEvent.Action.SUGGEST_COMMAND))return null;
        String query=commandQuery(click.getValue());
        return query==null||query.isEmpty()?null:query;
    }

    static String chatQueryAt(GuiScreen screen,int mx,int my){
        if(!(screen instanceof GuiChat))return null;
        int scale=new ScaledResolution(MinecraftAccess.get()).getScaleFactor();
        // GuiNewChat hit testing takes display pixels, including Reliable Chat's scroll/offset handling.
        return chatQuery(MinecraftAccess.get().ingameGUI.getChatGUI().getChatComponent(mx*scale,MinecraftAccess.get().displayHeight-my*scale-1));
    }

    ResourceLocation chatTexture(String query){
        String dir=chatMaps.get(query);
        if(dir==null){
            MapRemote.requestCatalog();
            MapInfo local=MapInfo.findLocal(query);
            if(local!=null&&local.exact(query))dir=local.dir;
            else dir=MapRemote.findDirectory(query,MapRemote.catalog);
            if(dir!=null)chatMaps.put(query,dir);
        }
        return dir==null?null:galleryTexture(dir+"/map.png");
    }

    /** Keyboard fallback; the registered client command also covers clicks and custom chat screens. */
    @SubscribeEvent public void chatKey(GuiScreenEvent.KeyboardInputEvent.Pre e){
        if(!commandPreview())return;
        if(!(e.gui instanceof GuiChat))return;
        if(!Keyboard.getEventKeyState())return;
        int key=Keyboard.getEventKey();
        if(key!=Keyboard.KEY_RETURN&&key!=Keyboard.KEY_NUMPADENTER)return;
        String text=chatText(e.gui);
        if(INSTANCE.queueCommand(text)){
            MinecraftAccess.get().ingameGUI.getChatGUI().addToSentMessages(text.trim());e.setCanceled(true);
        }
    }

    static boolean bookPreview(){return ServerScope.active()&&UtilitySettings.mapPreview;}
    static boolean commandPreview(){return bookPreview()&&UtilitySettings.previewOnCommand;}
    static String commandQuery(String text){
        if(text==null)return null;
        text=text.trim();
        if(text.equalsIgnoreCase("/map"))return "";
        if(text.length()<6||!text.regionMatches(true,0,"/map",0,4)||!Character.isWhitespace(text.charAt(4)))return null;
        String query=text.substring(5).trim();
        return query.isEmpty()?null:query;
    }
    boolean queueCommand(String text){
        if(!commandPreview())return false;
        String query=commandQuery(text);
        if(query==null)return false;
        pendingConnection=MinecraftAccess.get().getNetHandler();
        inputScreen=MinecraftAccess.get().currentScreen;
        pendingQuery=query.isEmpty()?null:query;responseTitle=null;commandScreen=null;
        waitingUntil=System.currentTimeMillis()+8000;
        // Keep the server command authoritative for aliases, variants and repository identity.
        MinecraftAccess.get().thePlayer.sendChatMessage(text.trim());
        return true;
    }

    static String serverQuery(String text){
        Matcher xml=Pattern.compile("^XML:\\s*OvercastCommunity/(PublicMaps|CommunityMaps)\\s+/?([^\\s]+).*$").matcher(text.trim());
        if(xml.matches())return (xml.group(1).equals("PublicMaps")?"PublicMaps/main/":"CommunityMaps/master/")+xml.group(2);
        Matcher playing=Pattern.compile("^Playing (.+) by .+$").matcher(text.trim());
        return playing.matches()?playing.group(1):null;
    }

    @SubscribeEvent public void mapResponse(ClientChatReceivedEvent event){
        if(waitingUntil==0||!commandPreview())return;
        String text=ChatRules.plain(event.message.getUnformattedText());
        for(String line:text.split("[\\r\\n]+")){
            String query=serverQuery(line);
            if(query!=null){pendingQuery=query;waitingUntil=0;return;}
            if(line.matches("^\\s{4,}.+\\s{4,}$"))responseTitle=line.trim().replaceFirst("\\s+\\d+\\.\\d+(?:\\.\\d+)*$","");
        }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(pendingConnection!=MinecraftAccess.get().getNetHandler()||!commandPreview()){
            pendingQuery=null;waitingUntil=0;commandScreen=null;inputScreen=null;pendingConnection=null;return;
        }
        if(commandScreen==null&&waitingUntil!=0&&MinecraftAccess.get().currentScreen!=null&&MinecraftAccess.get().currentScreen!=inputScreen){pendingQuery=null;waitingUntil=0;inputScreen=null;return;}
        if(commandScreen!=null&&MinecraftAccess.get().currentScreen!=commandScreen){pendingQuery=null;waitingUntil=0;commandScreen=null;return;}
        if(waitingUntil!=0&&System.currentTimeMillis()>=waitingUntil){
            waitingUntil=0;
            if(commandScreen==null){
                pendingQuery=responseTitle;
                if(pendingQuery==null)MinecraftAccess.get().ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.ChatComponentText("OTL: server did not identify the current map; use /map <name>."));
            }
        }
        if(pendingQuery!=null){
            String query=pendingQuery;pendingQuery=null;
            // GuiChat closes itself after sending; open on the next tick, not inside sendChatMessage.
            GuiScreen screen=new MapInfoScreen(query);MinecraftAccess.get().displayGuiScreen(screen);
            commandScreen=waitingUntil==0?null:screen;inputScreen=null;
        }
    }

    /** The chat input lives in a GuiTextField whose name is remapped on Lunar. */
    private static String chatText(GuiScreen screen){
        for(Class<?> c=screen.getClass();c!=null&&c!=Object.class;c=c.getSuperclass()){
            Field[] fields;
            try{fields=c.getDeclaredFields();}catch(Exception ignored){continue;}
            for(Field f:fields){
                if(!GuiTextField.class.isAssignableFrom(f.getType()))continue;
                try{
                    f.setAccessible(true);
                    Object value=f.get(screen);
                    if(value instanceof GuiTextField)return ((GuiTextField)value).getText();
                }catch(Exception ignored){}
            }
        }
        return null;
    }

    /** Preview texture for a downloaded (non-bundled) map. */
    static ResourceLocation fileTexture(String key,File file){
        ResourceLocation ready=fileTextures.get(key);
        if(ready!=null)return ready;
        try(InputStream in=new java.io.FileInputStream(file)){
            BufferedImage image=readSquare(in);
            if(image==null)return null;
            ResourceLocation r=MinecraftAccess.get().getTextureManager().getDynamicTextureLocation("otl_"+key,new DynamicTexture(image));
            fileTextures.put(key,r);
            return r;
        }catch(Exception ignored){return null;}
    }

    static ResourceLocation galleryTexture(String path){
        if(path.startsWith("CommunityMaps/master/"))path=path.substring("CommunityMaps/master/".length());
        ResourceLocation ready=galleryTextures.get(path);
        if(ready!=null)return ready;
        try(InputStream in=MapPreview.class.getResourceAsStream("/assets/otl/maps/"+path)){
            if(in==null){
                String dir=path.substring(0,path.length()-8);
                String remote=dir.startsWith("PublicMaps/main/")?dir:"CommunityMaps/master/"+dir;
                File file=MapRemote.preview(remote);
                ResourceLocation texture=file==null?null:fileTexture(remote,file);
                if(texture!=null)galleryTextures.put(path,texture);
                return texture;
            }
            BufferedImage image=readSquare(in);
            if(image==null)return null;
            ResourceLocation r=MinecraftAccess.get().getTextureManager().getDynamicTextureLocation("map_gallery_"+path.replace('/','_'),new DynamicTexture(image));
            galleryTextures.put(path,r);
            return r;
        }catch(Exception ignored){return null;}
    }

    // Keep these shared textures across reconnects; TextureManager still owns them.
    private static BufferedImage readSquare(InputStream in) throws java.io.IOException {
        try(javax.imageio.stream.ImageInputStream stream=ImageIO.createImageInputStream(in)){
            if(stream==null)return null;
            Iterator<javax.imageio.ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())return null;
            javax.imageio.ImageReader reader=readers.next();
            try{
                reader.setInput(stream);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>2048||h>2048)throw new java.io.IOException("Map preview exceeds 2048 pixels");
                return square(reader.read(0));
            }finally{reader.dispose();}
        }
    }

    static BufferedImage square(BufferedImage image){
        if(image.getWidth()==image.getHeight())return image;
        int side=Math.max(image.getWidth(),image.getHeight());
        BufferedImage result=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=result.createGraphics();
        try{g.drawImage(image,(side-image.getWidth())/2,(side-image.getHeight())/2,null);}finally{g.dispose();}
        return result;
    }

    /** Finds the map name under the cursor on the open book page. */
    Hover hover(GuiScreen screen,int mx,int my){
        if(!(screen instanceof GuiScreenBook))return null;
        List<?> lines=pageLines(screen);
        if(lines==null||lines.isEmpty())return null;
        FontRenderer font=MinecraftAccess.get().fontRendererObj;
        int rowHeight=font.FONT_HEIGHT;
        int rows=Math.min(128/rowHeight,lines.size());
        int left=(screen.width-192)/2+36,top=34;
        int offset=my-top;
        if(offset<0||offset>=rowHeight*rows+rows)return null;
        int row=offset/rowHeight;
        if(row<0||row>=lines.size())return null;
        Object raw=lines.get(row);
        if(!(raw instanceof IChatComponent))return null;
        String text=((IChatComponent)raw).getUnformattedText();
        if(text==null||text.isEmpty())return null;
        Entry best=null;
        int start=-1,len=0;
        List<String> catalog=MapRemote.catalog;
        if(hoverCatalog!=catalog){
            hoverCatalog=catalog;remoteEntries.clear();
            for(String dir:catalog)remoteEntries.add(new Entry(dir.substring(dir.lastIndexOf('/')+1),dir));
        }
        for(List<Entry> source:Arrays.asList(all,remoteEntries))for(Entry entry:source){
            for(String name:new String[]{entry.displayName,entry.folder}){
                int[] range=nameRange(text,name);
                if(range!=null&&range[1]-range[0]>len){best=entry;start=range[0];len=range[1]-range[0];}
            }
        }
        if(best==null||start<0)return null;
        int x0=left+font.getStringWidth(text.substring(0,start));
        int x1=x0+font.getStringWidth(text.substring(start,start+len));
        if(mx<x0||mx>x1)return null;
        return new Hover(best,text.substring(start,start+len));
    }

    /** Match server separators/formatting without losing the original mouse-hit coordinates. */
    static int[] nameRange(String text,String name){
        String q=MapInfo.norm(name);
        if(q.length()<2)return null;
        StringBuilder normalized=new StringBuilder();List<Integer> offsets=new ArrayList<>();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='\u00a7'&&i+1<text.length()){i++;continue;}
            if(Character.isLetterOrDigit(c)){normalized.append(Character.toLowerCase(c));offsets.add(i);}
        }
        for(int at=normalized.indexOf(q);at>=0;at=normalized.indexOf(q,at+1)){
            int start=offsets.get(at),end=offsets.get(at+q.length()-1)+1;
            String before=ChatRules.plain(text.substring(0,start)),after=ChatRules.plain(text.substring(end));
            if((before.isEmpty()||!Character.isLetterOrDigit(before.charAt(before.length()-1)))&&(after.isEmpty()||!Character.isLetterOrDigit(after.charAt(0))))return new int[]{start,end};
        }
        return null;
    }

    /** The rendered page lines of the book screen, found by field type because names are remapped. */
    private static List<?> pageLines(GuiScreen screen){
        for(Class<?> c=screen.getClass();c!=null&&c!=Object.class;c=c.getSuperclass()){
            Field[] fields;
            try{fields=c.getDeclaredFields();}catch(Exception ignored){continue;}
            for(Field f:fields){
                if(!List.class.isAssignableFrom(f.getType()))continue;
                try{
                    f.setAccessible(true);
                    Object value=f.get(screen);
                    if(!(value instanceof List))continue;
                    List<?> list=(List<?>)value;
                    if(list.isEmpty())continue;
                    if(list.get(0) instanceof IChatComponent)return list;
                }catch(Exception ignored){}
            }
        }
        return null;
    }

    static final class PreviewBox {
        final int x,y,side;
        PreviewBox(int width,int height,int mx,int my,boolean large,int tooltipWidth,int tooltipHeight){
            side=Math.max(1,Math.min(large?170:80,Math.min(width-8,height-8)));
            int tx=mx+12,ty=Math.min(my-12,height-tooltipHeight-6);
            int extent=tooltipHeight>0?tooltipWidth:side;
            if(tx+extent+4>width)tx=mx-16-extent;
            int py=tooltipHeight>0?ty+tooltipHeight+8:ty;
            if(tooltipHeight>0&&py+side+4>height)py=ty-side-8;
            x=Math.max(4,Math.min(tx,width-side-4));
            y=Math.max(4,Math.min(py,height-side-4));
        }
    }

    private void drawPreview(GuiScreen screen,int mx,int my,ResourceLocation texture){
        IChatComponent component;
        if(screen instanceof GuiScreenBook)component=((GuiScreenBook)screen).func_175385_b(mx,my);
        else{
            int scale=new ScaledResolution(MinecraftAccess.get()).getScaleFactor();
            component=MinecraftAccess.get().ingameGUI.getChatGUI().getChatComponent(mx*scale,MinecraftAccess.get().displayHeight-my*scale-1);
        }
        int tooltipWidth=0,tooltipHeight=0;
        net.minecraft.event.HoverEvent hover=component==null?null:component.getChatStyle().getChatHoverEvent();
        if(hover!=null&&hover.getAction()==net.minecraft.event.HoverEvent.Action.SHOW_TEXT){
            String[] lines=hover.getValue().getFormattedText().split("\n");
            for(String line:lines)tooltipWidth=Math.max(tooltipWidth,MinecraftAccess.get().fontRendererObj.getStringWidth(line));
            tooltipHeight=8+(lines.length>1?2+(lines.length-1)*10:0);
        }
        PreviewBox box=new PreviewBox(screen.width,screen.height,mx,my,UtilitySettings.largeMapPreview,tooltipWidth,tooltipHeight);
        int x=box.x,y=box.y,w=box.side;
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        Gui.drawRect(x-4,y-4,x+w+4,y+w+4,0xF0100010);
        Gui.drawRect(x-3,y-3,x+w+3,y+w+3,0x505000FF);
        Gui.drawRect(x-2,y-2,x+w+2,y+w+2,0xF0100010);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.color(1f,1f,1f,1f);
        MinecraftAccess.get().getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,w,w,w,w);
        net.minecraft.client.renderer.GlStateManager.enableDepth();
    }

    static final class Hover{
        final Entry entry;final String label;
        Hover(Entry e,String l){entry=e;label=l;}
    }

    static final class Entry{
        final String folder,path;
        volatile String displayName,lowerName;
        volatile String lowerFolder;
        Entry(String f,String p){folder=f;path=p;}
    }
}
