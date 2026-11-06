package otl.client;

import java.awt.image.BufferedImage;
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
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

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
    private static final Map<String,int[]> gallerySizes=new ConcurrentHashMap<>();
    private static final Map<String,String> nameCache=new ConcurrentHashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"OTL Map Names");t.setDaemon(true);return t;});
    private final List<Entry> all=new ArrayList<>();
    static final MapPreview INSTANCE=new MapPreview();

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
        Entry e=entries.get(path);
        if(e!=null)return e.displayName==null?"":e.displayName;
        String cached=nameCache.get(path);
        if(cached!=null)return cached;
        String name=readMapName(path);
        nameCache.put(path,name==null?"":name);
        return name;
    }

    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post e){
        if(!UtilitySettings.mapPreview)return;
        GuiScreen screen=e.gui;
        if(screen==null)return;
        Hover hit=hover(screen,e.mouseX,e.mouseY);
        if(hit==null)return;
        Entry entry=hit.entry;
        if(entry.texture==null)load(entry);
        if(entry.texture==null)return;
        drawPreview(screen,e.mouseX,e.mouseY,entry,hit.label);
    }

    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e){
        galleryTextures.clear();
        gallerySizes.clear();
    }

    static int[] gallerySize(String path){return gallerySizes.get(path);}
    static ResourceLocation galleryTexture(String path){
        ResourceLocation ready=galleryTextures.get(path);
        if(ready!=null)return ready;
        try(InputStream in=MapPreview.class.getResourceAsStream("/assets/otl/maps/"+path)){
            if(in==null)return null;
            BufferedImage image=ImageIO.read(in);
            if(image==null)return null;
            ResourceLocation r=MinecraftAccess.get().getTextureManager().getDynamicTextureLocation("map_gallery_"+path.replace('/','_'),new DynamicTexture(image));
            galleryTextures.put(path,r);
            gallerySizes.put(path,new int[]{image.getWidth(),image.getHeight()});
            return r;
        }catch(Exception ignored){return null;}
    }

    /** Finds the map name under the cursor on the open book page. */
    Hover hover(GuiScreen screen,int mx,int my){
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
        String lower=text.toLowerCase(Locale.ROOT);
        Entry best=null;
        int start=-1,len=0;
        for(Entry entry:all){
            String name=entry.lowerName;
            if(name==null||name.length()<2)continue;
            int at=lower.indexOf(name);
            if(at>=0&&name.length()>len){best=entry;start=at;len=name.length();}
        }
        if(best==null)for(Entry entry:all){
            String name=entry.lowerFolder;
            if(name==null||name.length()<3)continue;
            int at=lower.indexOf(name);
            if(at>=0&&name.length()>len){best=entry;start=at;len=name.length();}
        }
        if(best==null||start<0)return null;
        int x0=left+font.getStringWidth(text.substring(0,start));
        int x1=x0+font.getStringWidth(text.substring(start,start+len));
        if(mx<x0||mx>x1)return null;
        return new Hover(best,text.substring(start,start+len));
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

    private void load(Entry e){
        if(e.loading||e.texture!=null)return;
        e.loading=true;
        try(InputStream in=MapPreview.class.getResourceAsStream("/assets/otl/maps/"+e.path+"/map.png")){
            if(in==null)return;
            BufferedImage image=ImageIO.read(in);
            if(image==null)return;
            e.width=image.getWidth();
            e.height=image.getHeight();
            DynamicTexture texture=new DynamicTexture(image);
            e.resource=new ResourceLocation("otl","map_preview_"+Integer.toHexString(e.path.hashCode()));
            MinecraftAccess.get().getTextureManager().loadTexture(e.resource,texture);
            e.texture=texture;
        }catch(Exception ignored){}finally{e.loading=false;}
    }

    private void drawPreview(GuiScreen screen,int mx,int my,Entry e,String label){
        FontRenderer font=MinecraftAccess.get().fontRendererObj;
        int w=170,h=Math.max(96,Math.round(170f*e.height/Math.max(1,e.width)));
        if(h>240){h=240;w=Math.round(240f*e.width/Math.max(1,e.height));}
        int x=mx+14,y=my+14;
        if(x+w>screen.width-4)x=mx-w-14;
        if(y+h+18>screen.height-4)y=screen.height-h-22;
        if(x<4)x=4;
        if(y<4)y=4;
        Gui.drawRect(x-3,y-3,x+w+3,y+h+20,0xF0101010);
        MinecraftAccess.get().getTextureManager().bindTexture(e.resource);
        Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,w,h,e.width,e.height);
        font.drawStringWithShadow(label,x+2,y+h+6,0xFFFFFFFF);
    }

    static final class Hover{
        final Entry entry;final String label;
        Hover(Entry e,String l){entry=e;label=l;}
    }

    static final class Entry{
        final String folder,path;
        volatile String displayName,lowerName;
        volatile String lowerFolder;
        volatile boolean loading;
        volatile int width=128,height=128;
        volatile DynamicTexture texture;
        volatile ResourceLocation resource;
        Entry(String f,String p){folder=f;path=p;}
    }
}
