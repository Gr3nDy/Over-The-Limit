package otl.client;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Local feedback only: never changes bow use, inventory or network packets. */
public final class BowOptimizer {
    /** Drops echoed shot noise; part of the module's factory reset and toggle. */
    public static void clearEchoes(){INSTANCE.echoes.clear();}
    static final BowOptimizer INSTANCE=new BowOptimizer();
    final Echoes echoes=new Echoes();
    private ISound predicted;
    private static Field renderedStack,renderedSlot;
    private static boolean rendererFailed;
    static boolean active(){UtilitySettings.load();return UtilitySettings.bowOptimizer&&UtilitySettings.bowOptimization&&ServerScope.active();}

    @SubscribeEvent(priority=EventPriority.LOWEST) public void releaseSound(PlaySoundAtEntityEvent event){
        Minecraft mc=MinecraftAccess.get();
        if(!active()||mc.thePlayer==null||event.entity!=mc.thePlayer||!event.entity.worldObj.isRemote||mc.thePlayer.isDead||!"random.bow".equals(event.name)
            ||mc.thePlayer.getHeldItem()==null||!(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow))return;
        // ItemBow emits this only after validating draw strength and ammunition, with vanilla's pitch.
        ISound sound=new PositionedSoundRecord(new ResourceLocation("random.bow"),event.newVolume,event.newPitch,
            (float)event.entity.posX,(float)event.entity.posY,(float)event.entity.posZ);
        echoes.add(event.entity.posX,event.entity.posY,event.entity.posZ,System.nanoTime());
        predicted=sound;
        try{mc.getSoundHandler().playSound(sound);}finally{predicted=null;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void sound(PlaySoundEvent event){
        if(!active()){echoes.clear();return;}
        if(event.sound==null||event.sound==predicted||event.result==null||!new ResourceLocation("random.bow").equals(event.sound.getSoundLocation()))return;
        Minecraft mc=MinecraftAccess.get();if(mc.theWorld==null||mc.thePlayer==null)return;
        boolean ambiguous=false;
        for(net.minecraft.entity.player.EntityPlayer other:mc.theWorld.playerEntities){
            if(other!=mc.thePlayer&&other.getDistanceSq(event.sound.getXPosF(),event.sound.getYPosF(),event.sound.getZPosF())<=1){ambiguous=true;break;}
        }
        if(echoes.consume(event.sound.getXPosF(),event.sound.getYPosF(),event.sound.getZPosF(),System.nanoTime(),ambiguous))event.result=null;
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)echoes.clear();}

    static boolean sameBow(ItemStack rendered,ItemStack held,int oldSlot,int newSlot){
        return oldSlot==newSlot&&rendered!=null&&held!=null&&rendered.getItem() instanceof ItemBow
            &&rendered.getItem()==held.getItem()&&rendered.stackSize==held.stackSize&&held.stackSize>0
            &&ItemStack.areItemStackTagsEqual(rendered,held);
    }
    static void stabilize(ItemRenderer renderer,ItemStack held,int slot){
        if(rendererFailed||held==null||!(held.getItem() instanceof ItemBow))return;
        try{
            if(renderedStack==null){
                renderedStack=ReflectionHelper.findField(ItemRenderer.class,"itemToRender","field_78453_b");
                renderedSlot=ReflectionHelper.findField(ItemRenderer.class,"equippedItemSlot","field_78450_g");
            }
            ItemStack previous=(ItemStack)renderedStack.get(renderer);
            if(previous!=held&&sameBow(previous,held,renderedSlot.getInt(renderer),slot))renderedStack.set(renderer,held);
        }catch(Exception ex){rendererFailed=true;System.err.println("OTL: Bow Optimizer animation fix unavailable: "+ex.getMessage());}
    }

    static final class Echoes {
        private static final long WINDOW=1500000000L;
        private final Deque<Echo> pending=new ArrayDeque<>();
        void add(double x,double y,double z,long now){
            expire(now);pending.addLast(new Echo(x,y,z,now));while(pending.size()>16)pending.removeFirst();
        }
        boolean consume(double x,double y,double z,long now,boolean ambiguous){
            expire(now);
            for(Iterator<Echo> it=pending.iterator();it.hasNext();){
                Echo echo=it.next();
                if(now>=echo.at&&Math.abs(x-echo.x)<=.125&&Math.abs(y-echo.y)<=.125&&Math.abs(z-echo.z)<=.125){
                    it.remove();return !ambiguous;
                }
            }
            return false;
        }
        private void expire(long now){while(!pending.isEmpty()&&now-pending.peekFirst().at>WINDOW)pending.removeFirst();}
        void clear(){pending.clear();}
        private static final class Echo {
            final double x,y,z;final long at;
            Echo(double x,double y,double z,long at){this.x=x;this.y=y;this.z=z;this.at=at;}
        }
        // shortcut: 1.8.9 sounds have no shooter ID; ambiguous or unmatched echoes stay audible rather than muting others.
    }
}
