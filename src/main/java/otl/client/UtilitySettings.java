package otl.client;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Preferences only. Chat content, private names and drafts are never serialized. */
final class UtilitySettings {
    private static boolean loaded;
    static boolean chat=true, counts=true, fullscreen=false, otherFaces=true, showTabChat=true;
    static int tabChatPosition=0;
    static String tabPositionLabel(){
        return new String[]{"Bottom Left","Bottom Right","Top Left","Top Right"}[Math.max(0,Math.min(3,tabChatPosition))];
    }
    static boolean faceRig=false;
    static boolean rememberDrafts=false, unlimitedHistory=false, cycleTabs=true, bubbles=false;
    static int bubbleCharacters=80, bubbleSeconds=4;
    static int chatBackground=0;
    static final Map<String,String> selected=new HashMap<>();
    private static Path path(){return MinecraftAccess.get().mcDataDir.toPath().resolve("config/overcast_util/settings.json");}
    static void load(){
        if(loaded)return;loaded=true;
        try{
            if(!Files.exists(path()))return;
            JsonObject j=new JsonParser().parse(new String(Files.readAllBytes(path()),StandardCharsets.UTF_8)).getAsJsonObject();
            if(j.has("reliable_chat"))chat=j.get("reliable_chat").getAsBoolean();
            if(j.has("face_rig"))faceRig=j.get("face_rig").getAsBoolean();
            if(j.has("unread_counts"))counts=j.get("unread_counts").getAsBoolean();
            if(j.has("fullscreen_menu"))fullscreen=j.get("fullscreen_menu").getAsBoolean();
            if(j.has("other_faces"))otherFaces=j.get("other_faces").getAsBoolean();
            if(j.has("show_tab_chat"))showTabChat=j.get("show_tab_chat").getAsBoolean();
            if(j.has("tab_chat_position"))tabChatPosition=Math.max(0,Math.min(3,j.get("tab_chat_position").getAsInt()));
            if(j.has("remember_drafts"))rememberDrafts=j.get("remember_drafts").getAsBoolean();
            if(j.has("unlimited_history"))unlimitedHistory=j.get("unlimited_history").getAsBoolean();
            if(j.has("shift_tab"))cycleTabs=j.get("shift_tab").getAsBoolean();
            if(j.has("team_bubbles"))bubbles=j.get("team_bubbles").getAsBoolean();
            if(j.has("bubble_characters"))bubbleCharacters=Math.max(20,Math.min(200,j.get("bubble_characters").getAsInt()));
            if(j.has("bubble_seconds"))bubbleSeconds=Math.max(1,Math.min(15,j.get("bubble_seconds").getAsInt()));
            if(j.has("chat_background_opacity"))chatBackground=Math.max(0,Math.min(100,j.get("chat_background_opacity").getAsInt()));
            if(j.has("selected_channels"))for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("selected_channels").entrySet()){
                String v=e.getValue().getAsString();if(ChatRules.channel(v)!=null)selected.put(e.getKey(),v);
            }
        }catch(Exception e){System.err.println("Overcast Utilities: could not load settings: "+e.getMessage());}
    }
    static void save(){
        load();try{
            JsonObject j=new JsonObject();j.addProperty("reliable_chat",chat);j.addProperty("unread_counts",counts);j.addProperty("fullscreen_menu",fullscreen);j.addProperty("other_faces",otherFaces);j.addProperty("show_tab_chat",showTabChat);j.addProperty("tab_chat_position",tabChatPosition);
            j.addProperty("face_rig",faceRig);
            j.addProperty("remember_drafts",rememberDrafts);j.addProperty("unlimited_history",unlimitedHistory);j.addProperty("shift_tab",cycleTabs);j.addProperty("team_bubbles",bubbles);j.addProperty("bubble_characters",bubbleCharacters);j.addProperty("bubble_seconds",bubbleSeconds);
            j.addProperty("chat_background_opacity",chatBackground);
            JsonObject channels=new JsonObject();selected.forEach(channels::addProperty);j.add("selected_channels",channels);
            Files.createDirectories(path().getParent());Files.write(path(),new GsonBuilder().setPrettyPrinting().create().toJson(j).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){System.err.println("Overcast Utilities: could not save settings: "+e.getMessage());}
    }
}
