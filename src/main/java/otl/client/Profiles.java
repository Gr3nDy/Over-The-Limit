package otl.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Profile files: list, create, switch, rename, delete, export and import. The keys live in UtilitySettings. */
final class Profiles {
    private Profiles(){}

    private static Path dir(){return UtilitySettings.path().getParent().resolve("profiles");}
    static Path file(String name){return dir().resolve(safeName(name)+".json");}
    static String safeName(String name){
        String value=name==null?"":name.replaceAll("[^A-Za-z0-9 _()-]","").trim();
        if(value.isEmpty())value="Profile";
        return value.length()>24?value.substring(0,24):value;
    }
    /** Saved rail ordering; profile files missing from it are appended in directory order. */
    static List<String> profileOrder=new ArrayList<>();
    static List<String> profileNames(){
        List<String> names=new ArrayList<>();
        try{if(Files.isDirectory(dir()))try(DirectoryStream<Path> list=Files.newDirectoryStream(dir(),"*.json")){
            for(Path p:list){String name=p.getFileName().toString();names.add(name.substring(0,name.length()-5));}
        }}catch(Exception e){System.err.println("Overcast Utilities: could not list profiles: "+e.getMessage());}
        if(!names.contains(UtilitySettings.profile))names.add(UtilitySettings.profile);
        List<String> out=new ArrayList<>();
        for(String n:profileOrder)if(names.remove(n))out.add(n);
        out.addAll(names);
        return out;
    }
    /** Saves the current profile, then loads another one; a missing or invalid file changes nothing. */
    static void switchProfile(String name){
        UtilitySettings.load();String target=safeName(name);
        if(target.equals(UtilitySettings.profile))return;
        JsonObject next=readProfile(file(target));
        if(next==null)return;
        UtilitySettings.save();
        // Speech-bubble prefs are global: preserve them across profile switches.
        boolean keepBubbles=UtilitySettings.bubbles,keepBubbleChat=UtilitySettings.bubbleTeamAndChat;
        int keepChars=UtilitySettings.bubbleCharacters,keepSeconds=UtilitySettings.bubbleSeconds;
        UtilitySettings.resetDefaults();
        UtilitySettings.read(next);
        UtilitySettings.bubbles=keepBubbles;UtilitySettings.bubbleTeamAndChat=keepBubbleChat;
        UtilitySettings.bubbleCharacters=keepChars;UtilitySettings.bubbleSeconds=keepSeconds;
        UtilitySettings.profile=target;
        UtilitySettings.save();
        ReliableChat.applyPreferences();
    }
    static String newProfile(){
        UtilitySettings.load();
        for(int i=1;i<=999;i++){
            String name="Profile "+i;
            if(Files.exists(file(name)))continue;
            JsonObject fresh=new JsonParser().parse(UtilitySettings.DEFAULTS.toString()).getAsJsonObject();
            fresh.addProperty("profile",name);
            try{UtilitySettings.write(file(name),fresh);}catch(Exception e){System.err.println("Overcast Utilities: could not create profile: "+e.getMessage());return null;}
            switchProfile(name);
            return name;
        }
        return null;
    }
    static boolean deleteProfile(String name){
        String target=safeName(name);
        if(target.equals(UtilitySettings.profile)||target.equals("Default"))return false;
        try{Files.deleteIfExists(file(target));return true;}catch(Exception e){System.err.println("Overcast Utilities: could not delete profile: "+e.getMessage());return false;}
    }
    /** Persists the rail order; names are sanitized and duplicates dropped. */
    static void saveProfileOrder(List<String> names){
        profileOrder.clear();
        for(String n:names){String s=safeName(n);if(!profileOrder.contains(s))profileOrder.add(s);}
        UtilitySettings.save();
    }
    /** Renames a profile file; a taken name gets a number. Returns the resulting name. */
    static String renameProfile(String from,String to){
        UtilitySettings.load();
        String source=safeName(from),target=safeName(to);
        if(target.equals(source))return source;
        target=uniqueName(target);
        final String dest=target;
        profileOrder.replaceAll(n->n.equals(source)?dest:n);
        try{
            Files.move(file(source),file(target));
            if(UtilitySettings.profile.equals(source)){UtilitySettings.profile=target;UtilitySettings.save();}
            return target;
        }catch(Exception e){System.err.println("Overcast Utilities: could not rename profile: "+e.getMessage());return source;}
    }
    /** Pretty JSON for the clipboard; the active profile is saved first so the copy is current. */
    static String profileExport(String name){
        UtilitySettings.load();
        String target=safeName(name);
        if(target.equals(UtilitySettings.profile))UtilitySettings.save();
        JsonObject j=readProfile(file(target));
        return j==null?null:new GsonBuilder().setPrettyPrinting().create().toJson(j);
    }
    /** Creates and activates a profile from clipboard JSON. Returns its name, or null. */
    static String importProfile(String json){
        UtilitySettings.load();
        if(json==null||json.length()>65536)return null;
        try{
            JsonObject j=new JsonParser().parse(json).getAsJsonObject();
            String name=uniqueName(j.has("profile")?j.get("profile").getAsString():"Imported");
            j.addProperty("profile",name);
            UtilitySettings.write(file(name),j);
            switchProfile(name);
            return name;
        }catch(Exception e){System.err.println("Overcast Utilities: could not import profile: "+e.getMessage());return null;}
    }
    private static String uniqueName(String name){
        String base=safeName(name),candidate=base;
        if(!Files.exists(file(candidate)))return candidate;
        String stem=base.length()>18?base.substring(0,18):base;
        for(int i=2;i<1000;i++){candidate=stem+" ("+i+")";if(!Files.exists(file(candidate)))break;}
        return candidate;
    }
    private static JsonObject readProfile(Path p){
        try{
            if(!Files.exists(p))return null;
            return new JsonParser().parse(new String(Files.readAllBytes(p),StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){System.err.println("Overcast Utilities: could not read profile "+p.getFileName()+": "+e.getMessage());return null;}
    }
    /** The Default profile always exists: recreates it with factory settings if deleted. */
    static void ensureDefault(){
        if(Files.exists(file("Default")))return;
        try{
            JsonObject fresh=new JsonParser().parse(UtilitySettings.DEFAULTS.toString()).getAsJsonObject();
            fresh.addProperty("profile","Default");
            UtilitySettings.write(file("Default"),fresh);
        }catch(Exception e){System.err.println("Overcast Utilities: could not recreate Default profile: "+e.getMessage());}
    }
}
