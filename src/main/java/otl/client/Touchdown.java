package otl.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Keeps track of whether the map being played is a Touchdown map; every football visual reads this. */
final class Touchdown {
    static final Touchdown INSTANCE=new Touchdown();
    private static volatile boolean mapTouchdown;
    private static String gamemode="";
    private static int lookup;
    private static Object world;
    private Touchdown(){}

    static boolean active(){return UtilitySettings.touchdown&&mapTouchdown;}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void chat(ClientChatReceivedEvent event){
        if(event.type==2)return;
        String text=ChatRules.plain(event.message.getFormattedText()).trim();
        if(!text.startsWith("Playing "))return;
        String query=MapPreview.serverQuery(text);
        if(query!=null)resolve(query);
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
        Touchdown.gamemode=gamemode==null?"":gamemode;
        mapTouchdown=MapInfo.norm(gamemode).equals("touchdown");
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=world){
            world=mc.theWorld;
            if(mc.theWorld==null){mapTouchdown=false;gamemode="";lookup++;}
        }
        // Joining mid-match never shows the "Playing" line, but the kit's Football is in the inventory.
        if(!mapTouchdown&&UtilitySettings.touchdown&&hasFootball(mc))mapTouchdown=true;
    }

    private static boolean hasFootball(Minecraft mc){
        if(mc.thePlayer==null)return false;
        for(ItemStack stack:mc.thePlayer.inventory.mainInventory){
            if(stack==null||stack.getItem()!=net.minecraft.init.Items.snowball||!stack.hasDisplayName())continue;
            if(ChatRules.plain(stack.getDisplayName()).toLowerCase(Locale.ROOT).contains("football"))return true;
        }
        return false;
    }

    /** Smoke hook: the map the server just announced. */
    static void announce(String gamemode){apply(gamemode);}
    static String gamemode(){return gamemode;}
}
