package otl.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Whether the map being played is a Touchdown map. Three signals: the map index, which is
 * authoritative but only knows Overcast's maps; the Football in the inventory; and PGM's own
 * Touchdown messages, which work on any server but need someone to have thrown the ball.
 */
final class Touchdown {
    static final Touchdown INSTANCE=new Touchdown();
    /** The Playing line named a map we could resolve, so its gamemode decides. */
    private static volatile boolean mapKnown;
    private static volatile boolean mapTouchdown;
    /** This match showed a Football, by inventory or by PGM's own broadcast. */
    private static volatile boolean ballSeen;
    private static String gamemode="";
    private static int lookup;
    private static Object world;
    private Touchdown(){}

    static boolean active(){return UtilitySettings.touchdown&&(mapKnown?mapTouchdown:ballSeen);}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void chat(ClientChatReceivedEvent event){
        if(event.type==2)return;
        String text=ChatRules.plain(event.message.getFormattedText());
        // PGM decorates the line with a strikethrough rule and may send it inside the welcome
        // block, so look for it anywhere rather than only at the start of the first line.
        for(String raw:text.split("[\\r\\n]+")){
            int at=raw.indexOf("Playing ");
            if(at<0)continue;
            String query=MapPreview.serverQuery(raw.substring(at).trim());
            if(query==null)continue;
            // A new match: the previous map's Football says nothing about this one.
            reset();
            resolve(query);
            return;
        }
        // Touchdown's own lines ("... threw the Football!") identify the ball without the map index.
        if(!mapKnown&&text.contains("the Football"))ballSeen=true;
    }

    private static void resolve(String query){
        int token=++lookup;
        MapInfo local=MapInfo.findLocal(query);
        if(local!=null){apply(local.gamemode);return;}
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

    private static void reset(){mapKnown=false;mapTouchdown=false;ballSeen=false;}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=world){
            world=mc.theWorld;
            if(mc.theWorld==null){reset();gamemode="";lookup++;}
        }
        if(!mapKnown&&!ballSeen&&UtilitySettings.touchdown&&(hasFootball(mc)||sidebarTouchdown(mc)))ballSeen=true;
    }

    private static boolean sidebarTouchdown(Minecraft mc){
        if(mc.theWorld==null)return false;
        net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
        net.minecraft.scoreboard.ScoreObjective sidebar=board.getObjectiveInDisplaySlot(1);
        if(sidebar==null)return false;
        if(touchdownLabel(sidebar.getDisplayName()))return true;
        for(net.minecraft.scoreboard.Score score:board.getSortedScores(sidebar)){
            String name=score.getPlayerName();
            net.minecraft.scoreboard.ScorePlayerTeam team=board.getPlayersTeam(name);
            if(touchdownLabel(net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(team,name)))return true;
        }
        return false;
    }

    private static boolean touchdownLabel(String text){
        return ChatRules.plain(text).trim().matches("(?i)^(?:gamemode\\s*:\\s*)?touchdown$");
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
    static void announce(String gamemode){reset();apply(gamemode);}
    static String gamemode(){return gamemode;}
}
