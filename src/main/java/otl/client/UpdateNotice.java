package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * One chat line on the first join of the session when GitHub has a newer release than this build.
 * The request runs on a daemon thread, so a slow or blocked network never delays the join, and a
 * failed check stays silent rather than telling the player anything went wrong.
 */
final class UpdateNotice {
    static final UpdateNotice INSTANCE=new UpdateNotice();
    private static final String LATEST="https://api.github.com/repos/Gr3nDy/Over-The-Limit/releases/latest";
    private static final String PAGE="https://github.com/Gr3nDy/Over-The-Limit/releases/latest";
    /** One check per game session: the answer cannot change between servers. */
    private static volatile boolean asked;
    /** A newer release waiting for a world to be loaded; null when there is nothing to say. */
    private static volatile String pendingTag;

    private UpdateNotice(){}

    @SubscribeEvent public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event){
        UtilitySettings.load();
        if(asked||!UtilitySettings.updateNotice)return;
        asked=true;
        Thread worker=new Thread(this::check,"OTL Update Check");
        worker.setDaemon(true);
        worker.start();
    }

    private void check(){
        String tag;
        try{tag=tagOf(MapRemote.downloadText(LATEST));}
        catch(Exception offline){return;}
        if(tag!=null&&newer(tag,Otl.VERSION))pendingTag=tag;
    }

    /** The reply can arrive before the world does, so the line waits for a chat box to draw into. */
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        String tag=pendingTag;
        if(tag==null||event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null||mc.ingameGUI==null)return;
        pendingTag=null;
        IChatComponent line=new ChatComponentText("\u00a7bOTL \u00a7f"+tag+"\u00a77 is out \u00a78(you have "+Otl.VERSION+")\u00a7r ");
        ChatComponentText link=new ChatComponentText("\u00a7b\u00a7lDownload");
        link.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,PAGE));
        link.getChatStyle().setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ChatComponentText("\u00a77Open the releases page")));
        ReliableChat.say(line.appendSibling(link));
    }

    /** The release's tag, or null when the response is not a release this mod can read. */
    static String tagOf(String json){
        try{
            com.google.gson.JsonObject release=new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            com.google.gson.JsonElement tag=release.get("tag_name");
            return tag==null||tag.isJsonNull()?null:tag.getAsString();
        }catch(RuntimeException notJson){return null;}
    }

    /** True when latest is a strictly newer major.minor.patch than current. */
    static boolean newer(String latest,String current){
        int[] a=version(latest),b=version(current);
        if(a==null||b==null)return false;
        for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];
        return false;
    }

    private static int[] version(String tag){
        if(tag==null)return null;
        String text=tag.trim();
        if(text.startsWith("v")||text.startsWith("V"))text=text.substring(1);
        String[] parts=text.split("\\.");
        if(parts.length<2)return null;
        int[] out=new int[3];
        try{
            for(int i=0;i<3&&i<parts.length;i++){
                String digits=parts[i].replaceAll("[^0-9].*$","");
                if(digits.isEmpty())return null;
                out[i]=Integer.parseInt(digits);
            }
        }catch(NumberFormatException bad){return null;}
        return out;
    }
}
