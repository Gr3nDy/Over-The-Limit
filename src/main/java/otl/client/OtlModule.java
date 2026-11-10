package otl.client;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Modifier;
import java.util.jar.JarFile;
import net.minecraft.client.gui.GuiScreen;

/**
 * One product feature. Modules live in otl.client.modules and are discovered by
 * scanning the package at startup - dropping in a new module file is all it takes.
 *
 * Rules for implementations:
 * - enabled() reads the flag at the point of use, so toggling needs no lifecycle calls.
 * - setEnabled() saves nothing; the caller persists once, after the toggle.
 * - Toggling must be safe mid-game (no leaked GL state, no unregistered listeners).
 */
public abstract class OtlModule {
    final String id, name, category;
    private final int order;

    protected OtlModule(String id, String name) {
        this(id, name, "HUD", 100);
    }

    protected OtlModule(String id, String name, String category) {
        this(id, name, category, 100);
    }

    protected OtlModule(String id, String name, String category, int order) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.order = order;
    }

    public abstract boolean enabled();

    public abstract void setEnabled(boolean on);

    /** Restores this module's options to factory values; other modules and shared data stay put. */
    public void reset(){setEnabled(false);}

    public boolean available(){return true;}

    /** Options screen for this module, or null when it has none. */
    public GuiScreen settings(GuiScreen back) {
        return null;
    }

    /** Find a module by its id; null when unknown. */
    public static OtlModule byId(String id){for(OtlModule m:ALL)if(m.id.equals(id))return m;return null;}

    public static final List<OtlModule> ALL = discover();

    // shortcut: classpath scan happens once at mod startup; not a hot path
    private static List<OtlModule> discover(){
        List<OtlModule> found=new ArrayList<>();
        try{
            String pkg="otl.client.modules";
            URL url=OtlModule.class.getClassLoader().getResource(pkg.replace('.','/'));
            if(url!=null){
                for(String className:classNames(url,pkg)){
                    try{
                        Class<?> cls=Class.forName(className,true,OtlModule.class.getClassLoader());
                        if(Modifier.isAbstract(cls.getModifiers())||cls.isInterface()||!OtlModule.class.isAssignableFrom(cls))continue;
                        found.add((OtlModule)cls.getConstructor().newInstance());
                    }catch(Throwable inner){System.err.println("Overcast Utilities: could not register module "+className+": "+inner);}
                }
            }
        }catch(Throwable t){System.err.println("Overcast Utilities: module discovery failed: "+t);}
        Collections.sort(found,Comparator.comparingInt(m->m.order));
        return Collections.unmodifiableList(found);
    }

    private static List<String> classNames(URL url,String pkg)throws Exception{
        List<String> out=new ArrayList<>();
        String prefix=pkg.replace('.','/')+"/";
        if("file".equals(url.getProtocol())){
            File dir=new File(url.toURI());
            for(File file:dir.listFiles()){
                String n=file.getName();
                if(n.endsWith(".class")&&!n.contains("$"))out.add(pkg+"."+n.substring(0,n.length()-".class".length()));
            }
            return out;
        }
        JarFile jar=((JarURLConnection)url.openConnection()).getJarFile();
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=jar.entries();
        while(entries.hasMoreElements()){
            String name=entries.nextElement().getName();
            if(name.startsWith(prefix)&&name.endsWith(".class")&&name.indexOf('$',prefix.length())<0)
                out.add(name.substring(0,name.length()-".class".length()).replace('/','.'));
        }
        return out;
    }
}
