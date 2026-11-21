package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Hides only the local player's overhead name while skating. */
public final class SkateNameplate {
    static final SkateNameplate INSTANCE=new SkateNameplate();
    public SkateNameplate(){}
    @SubscribeEvent
    public void name(RenderLivingEvent.Specials event){
        Minecraft mc=Minecraft.getMinecraft();
        if(event.entity==mc.thePlayer&&PlayerAnim.skating())event.setCanceled(true);
    }
}
