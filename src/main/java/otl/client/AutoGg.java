package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto GG: says "gg" once per match when the server announces the end.
 *
 * Only a match this client saw start can produce a gg, and map-start lines are matched before
 * end lines, so the leftovers PGM broadcasts while cycling maps can never trigger one.
 *
 * The chat event fires on the netty thread, so the message itself is sent from the client
 * tick. Reliable Chat cancels server chat at LOWEST, hence receiveCanceled here.
 */
final class AutoGg {
    static final AutoGg INSTANCE=new AutoGg();
    /** PGM keeps announcing the previous result for a moment after the next map loads. */
    private static final long SETTLE=5_000_000_000L;
    /** Set by chat, consumed by the next client tick. */
    private boolean pending,sent;
    /** A start line has been seen for this match; nothing else may produce a gg. */
    private boolean armed;
    private long startedAt;
    /** Messages actually sent this session; the smoke test asserts the once-per-match rule. */
    int sentCount;
    /** How long after a map starts a result line is treated as a leftover; the smoke test lowers it. */
    long settle=SETTLE;
    private Object world;
    private AutoGg(){}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void receive(ClientChatReceivedEvent event){
        if(event.type==2)return; // action bar
        Minecraft mc=MinecraftAccess.get();
        if(world!=mc.theWorld){world=mc.theWorld;pending=false;sent=false;armed=false;startedAt=0;}
        for(String line:ChatRules.plain(event.message.getUnformattedText()).split("[\\r\\n]+")){
            String text=line.trim();
            // Start lines come first: one of them can look like a result and must never say gg.
            if(MatchEnd.started(text)){
                pending=false;sent=false;armed=true;startedAt=System.nanoTime();
                continue;
            }
            if(!MatchEnd.is(text))continue;
            if(!armed)continue; // joined mid-match, or a result left over from before this match
            if(System.nanoTime()-startedAt<settle)continue; // the map start, not its end
            if(!sent){pending=true;sent=true;}
        }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!pending)return;
        pending=false;
        Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.autoGg||mc.thePlayer==null)return;
        mc.thePlayer.sendChatMessage("gg");
        sentCount++;
    }
}
