package otl.client;

import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * HUD widgets draw on the CHAT stage, which comes after the chat lines so nothing paints over them.
 * That stage is missing whenever the Chat event is cancelled (Forge's renderChat returns before
 * post(CHAT)) and some launchers never post it at all, which left every widget invisible while its
 * clock and tracking kept running. The end of the frame stands in once CHAT has been missing.
 */
final class HudStage {
    /** A few frames at 20fps: long enough to be a real skip, short enough to look instant. */
    private static final long MISSING_CHAT=100_000_000L;
    private static long lastChat;
    /** Frames the HUD owed to a missing chat stage; the smoke test reads it to prove the fallback ran. */
    static long fallbacks;
    private HudStage(){}
    static boolean due(RenderGameOverlayEvent.Post event){return due(event.type,System.nanoTime());}
    static boolean due(RenderGameOverlayEvent.ElementType type,long now){
        if(type==RenderGameOverlayEvent.ElementType.CHAT){lastChat=now;return true;}
        if(!isFallback(type,now)||hidesHud())return false;
        fallbacks++;
        return true;
    }
    /** Pure timing: the end of the frame stands in, and only once CHAT has really been skipped. */
    static boolean isFallback(RenderGameOverlayEvent.ElementType type,long now){
        return type==RenderGameOverlayEvent.ElementType.ALL&&now-lastChat>MISSING_CHAT;
    }
    /** Those screens hide the HUD by cancelling the chat stage, so the fallback must honour them. */
    private static boolean hidesHud(){
        net.minecraft.client.Minecraft mc=MinecraftAccess.get();
        return mc.currentScreen instanceof ModulesScreen||mc.currentScreen instanceof SettingsScreen||mc.currentScreen instanceof VoteScreen;
    }
}
