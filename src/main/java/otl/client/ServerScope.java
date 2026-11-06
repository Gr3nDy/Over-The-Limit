package otl.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

final class ServerScope {
    static String host(String address){
        if(address==null)return "";
        String h=address.trim().toLowerCase(Locale.ROOT);
        int colon=h.indexOf(':');
        if(colon>=0){if(!h.substring(colon+1).matches("[0-9]{1,5}"))return "";h=h.substring(0,colon);}
        if(h.endsWith("."))h=h.substring(0,h.length()-1);
        return h.equals("play.oc.tc")||h.equals("vanish.oc.tc")||UtilitySettings.devHosts.contains(h)?h:"";
    }
    static String current(){UtilitySettings.load();Minecraft mc=MinecraftAccess.get();ServerData s=mc.getCurrentServerData();return mc.theWorld==null||s==null?"":host(s.serverIP);}
    static boolean active(){return !current().isEmpty();}
}
