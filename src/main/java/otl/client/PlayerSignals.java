package otl.client;

import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumAction;

/** The only combat signals Face Rig uses: drawing a bow and releasing it. */
final class PlayerSignals {
    static final class Data {
        boolean aiming,wasAiming;
        int firedAt=-1000000;
    }
    private static final Map<Integer,Data> BY_ID=new HashMap<>();
    private PlayerSignals(){}

    static void tick(EntityPlayer p){
        Data d=BY_ID.computeIfAbsent(p.getEntityId(),k->new Data());
        ItemStack held=p.getCurrentEquippedItem();
        boolean aiming=p.isUsingItem()&&held!=null&&held.getItemUseAction()==EnumAction.BOW;
        if(d.wasAiming&&!aiming&&held!=null&&held.getItem()==Items.bow)d.firedAt=p.ticksExisted;
        d.wasAiming=aiming;d.aiming=aiming;
    }
    static Data of(EntityPlayer p){
        Data d=BY_ID.get(p.getEntityId());
        return d==null?new Data():d;
    }
    static void clear(){BY_ID.clear();}
}
