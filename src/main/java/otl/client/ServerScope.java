package otl.client;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

final class ServerScope {
    /**
     * Lines only PGM sends. The known hosts below are the servers we can name up front, but PGM
     * announces itself in chat, so any server running it can be recognised instead of guessed
     * from the address.
     */
    private static final Pattern PGM=Pattern.compile("(?i)(?:time remaining\\s*:?\\s*\\d{1,3}:\\d{2}"
        +"|match starting in \\d+ second|the match has started!|playing .{1,40} by "
        +"|has ended the match\\.|sudden death ends in)");
    /** The server proved it runs PGM, and which address it was. */
    private static volatile boolean pgm;
    private static String proved="";

    static String host(String address){
        if(address==null)return "";
        String h=address.trim().toLowerCase(Locale.ROOT);
        int colon=h.indexOf(':');
        if(colon>=0){if(!h.substring(colon+1).matches("[0-9]{1,5}"))return "";h=h.substring(0,colon);}
        if(h.endsWith("."))h=h.substring(0,h.length()-1);
        return h.equals("play.oc.tc")||h.equals("vanish.oc.tc")||h.equals("spectrum.oc.tc")||h.equals("172.65.223.142")||UtilitySettings.devHosts.contains(h)?h:"";
    }
    static String current(){UtilitySettings.load();Minecraft mc=MinecraftAccess.get();ServerData s=mc.getCurrentServerData();return mc.theWorld==null||s==null?"":host(s.serverIP);}

    /** The address we are connected to right now, allowlisted or not. */
    private static String address(){
        Minecraft mc=MinecraftAccess.get();ServerData s=mc.getCurrentServerData();
        return mc.theWorld==null||s==null?"":s.serverIP;
    }

    /**
     * Every chat line passes here. The first PGM-shaped one unlocks the mod for the server we are
     * on, which is what makes it work on a server we were never told about.
     */
    static void observe(String text){
        if(pgm||text==null||!PGM.matcher(text).find())return;
        String address=address();
        if(address.isEmpty())return;
        proved=address;pgm=true;
    }

    static boolean active(){
        // Leaving the server, or joining a different one, drops what the last one proved.
        String address=address();
        if(!address.equals(proved)){pgm=false;proved=address;}
        return !current().isEmpty()||pgm;
    }
}
