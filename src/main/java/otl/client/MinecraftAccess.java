package otl.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/** Resolves the Minecraft singleton across MCP, SRG and Lunar/Ichor runtimes. */
final class MinecraftAccess {
    private static final Method GETTER=findGetter();
    private MinecraftAccess(){}
    private static Method findGetter(){
        for(String name:new String[]{"getMinecraft","func_71410_x"})try{
            Method m=Minecraft.class.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        }catch(Exception ignored){}
        return null;
    }
    static Minecraft get(){
        if(GETTER==null)throw new IllegalStateException("Minecraft singleton method was not found");
        try{return (Minecraft)GETTER.invoke(null);}catch(Exception e){throw new IllegalStateException("Could not access Minecraft singleton",e);}
    }
}
