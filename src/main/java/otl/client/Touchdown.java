package otl.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Whether the map being played is a Touchdown map. Two signals: the map index, which is
 * authoritative but only knows Overcast's maps, and the Football in the inventory, which
 * works on any PGM server but only while one is carried.
 */
final class Touchdown {
    static final Touchdown INSTANCE=new Touchdown();
    /** The Playing line named a map we could resolve, so its gamemode decides. */
    private static volatile boolean mapKnown;
    private static volatile boolean mapTouchdown;
    /** A Football was carried on this map. Latched so a thrown one does not blink the flag ball. */
    private static volatile boolean itemFootball;
    private static String gamemode="";
    private static int lookup;
    private static Object world;
    private Touchdown(){}

    static boolean active(){return UtilitySettings.touchdown&&(mapKnown?mapTouchdown:itemFootball);}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void chat(ClientChatReceivedEvent event){
        if(event.type==2)return;
        String text=ChatRules.plain(event.message.getFormattedText()).trim();
        if(!text.startsWith("Playing "))return;
        String query=MapPreview.serverQuery(text);
        if(query==null)return;
        // A new match: the previous map's Football says nothing about this one.
        itemFootball=false;mapKnown=false;mapTouchdown=false;
        resolve(query);
    }

    private static void resolve(String query){
        MapInfo local=MapInfo.findLocal(query);
        if(local!=null){apply(local.gamemode);return;}
        int token=++lookup;
        MapRemote.lookup(query,map->MinecraftAccess.get().addScheduledTask(()->{
            if(lookup==token)apply(map==null?"":map.gamemode);
        }));
    }

    private static void apply(String gamemode){
        String resolved=gamemode==null?"":gamemode.trim();
        Touchdown.gamemode=resolved;
        // An unknown map must not lock the Football out, or servers without our map index stay dark.
        mapKnown=!resolved.isEmpty();
        mapTouchdown=mapKnown&&MapInfo.norm(resolved).equals("touchdown");
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=world){
            world=mc.theWorld;
            if(mc.theWorld==null){mapKnown=false;mapTouchdown=false;itemFootball=false;gamemode="";lookup++;}
        }
        if(!mapKnown&&!itemFootball&&UtilitySettings.touchdown&&hasFootball(mc))itemFootball=true;
    }

    private static boolean hasFootball(Minecraft mc){
        if(mc.thePlayer==null)return false;
        for(ItemStack stack:mc.thePlayer.inventory.mainInventory){
            if(stack==null||stack.getItem()!=net.minecraft.init.Items.snowball||!stack.hasDisplayName())continue;
            if(ChatRules.plain(stack.getDisplayName()).toLowerCase(Locale.ROOT).contains("football"))return true;
        }
        return false;
    }

    /** Smoke hook: the map the server just announced, or "" for a map we cannot resolve. */
    static void announce(String gamemode){
        itemFootball=false;mapKnown=false;mapTouchdown=false;
        apply(gamemode);
    }
    static String gamemode(){return gamemode;}
}
