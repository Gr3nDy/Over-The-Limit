package otl.client;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Preferences only. Chat content, private names and drafts are never serialized. */
final class UtilitySettings {
    private static boolean loaded;
    static boolean chat=true, counts=true, fullscreen=false, otherFaces=true, showTabChat=true, mapPreview=true, nonPlayerChat=false;
    static boolean previewOnCommand=true;
    static boolean largeMapPreview=false;
    static boolean leftHand=false;
    static boolean mainHandLeft=false;
    static LeftHand.Type handType=LeftHand.Type.ALL;
    static int tabChatPosition=0;
    static String tabPositionLabel(){
        return new String[]{"Bottom Left","Bottom Right","Top Left","Top Right"}[Math.max(0,Math.min(3,tabChatPosition))];
    }
    static boolean faceRig=false;
    static boolean killFeed=true,hideKillChat=false;
    static int killFeedSize=75,killBorderOpacity=100,killBorderColor=0xFFBD45,deathBorderColor=0xFF5555;
    static int killBackgroundOpacity=100,tabHudSize=100;
    static boolean killFeedSlide=false;
    static boolean killConfirm=false;
    static boolean killConfirmSounds=false;
    static boolean hideKillConfirmChat=false;
    static int killConfirmSize=75,killConfirmOpacity=0;
    static int killFeedRows=5,killFeedSeconds=10;
    static int noticeOpacity=0;
    static int noticeSize=100;
    static ServerNoticeRules.Mode noticeMode=ServerNoticeRules.Mode.ALL;
    static final Map<String,HudLayout.Position> hudPositions=new HashMap<>();
    static boolean rememberDrafts=false, unlimitedHistory=false, cycleTabs=true, bubbles=false;
    static int bubbleCharacters=80, bubbleSeconds=4;
    static int chatBackground=0;
    static int tabBackgroundOpacity=100;
    static int typingTabBackgroundOpacity=100;
    static final Map<String,String> selected=new HashMap<>();
    /** Extra hosts allowed in addition to oc.tc, for a local test server. */
    static final Set<String> devHosts=new LinkedHashSet<>();
    private static Path path(){return MinecraftAccess.get().mcDataDir.toPath().resolve("config/overcast_util/settings.json");}
    static void load(){
        if(loaded)return;loaded=true;
        try{
            if(!Files.exists(path()))return;
            JsonObject j=new JsonParser().parse(new String(Files.readAllBytes(path()),StandardCharsets.UTF_8)).getAsJsonObject();
            if(j.has("reliable_chat"))chat=j.get("reliable_chat").getAsBoolean();
            if(j.has("face_rig"))faceRig=j.get("face_rig").getAsBoolean();
            if(j.has("kill_feed"))killFeed=j.get("kill_feed").getAsBoolean();
            if(j.has("kill_confirm"))killConfirm=j.get("kill_confirm").getAsBoolean();
            if(j.has("kill_confirm_sounds"))killConfirmSounds=j.get("kill_confirm_sounds").getAsBoolean();
            if(j.has("hide_kill_confirm_chat"))hideKillConfirmChat=j.get("hide_kill_confirm_chat").getAsBoolean();
            if(j.has("kill_confirm_size"))killConfirmSize=Math.max(50,Math.min(150,j.get("kill_confirm_size").getAsInt()));
            if(j.has("kill_confirm_opacity"))killConfirmOpacity=Math.max(0,Math.min(100,j.get("kill_confirm_opacity").getAsInt()));
            if(j.has("hide_kill_chat"))hideKillChat=j.get("hide_kill_chat").getAsBoolean();
            if(j.has("kill_feed_size"))killFeedSize=Math.max(50,Math.min(150,j.get("kill_feed_size").getAsInt()));
            if(j.has("kill_border_opacity"))killBorderOpacity=Math.max(0,Math.min(100,j.get("kill_border_opacity").getAsInt()));
            killBackgroundOpacity=j.has("kill_background_opacity")?Math.max(0,Math.min(100,j.get("kill_background_opacity").getAsInt())):killBorderOpacity;
            if(j.has("kill_feed_slide"))killFeedSlide=j.get("kill_feed_slide").getAsBoolean();
            if(j.has("tab_hud_size"))tabHudSize=Math.max(50,Math.min(150,j.get("tab_hud_size").getAsInt()));
            if(j.has("kill_border_color"))killBorderColor=j.get("kill_border_color").getAsInt()&0xFFFFFF;
            if(j.has("death_border_color"))deathBorderColor=j.get("death_border_color").getAsInt()&0xFFFFFF;
            if(j.has("kill_feed_rows"))killFeedRows=Math.max(1,Math.min(5,j.get("kill_feed_rows").getAsInt()));
            if(j.has("kill_feed_seconds"))killFeedSeconds=Math.max(1,Math.min(30,j.get("kill_feed_seconds").getAsInt()));
            if(j.has("hud_positions"))for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("hud_positions").entrySet()){
                if(!HudLayout.valid(e.getKey()))continue;
                try{JsonObject p=e.getValue().getAsJsonObject();hudPositions.put(e.getKey(),new HudLayout.Position(p.get("x").getAsDouble(),p.get("y").getAsDouble()));}catch(RuntimeException invalid){System.err.println("OTL: ignored invalid HUD position");}
            }
            if(j.has("unread_counts"))counts=j.get("unread_counts").getAsBoolean();
            if(j.has("fullscreen_menu"))fullscreen=j.get("fullscreen_menu").getAsBoolean();
            if(j.has("other_faces"))otherFaces=j.get("other_faces").getAsBoolean();
            if(j.has("show_tab_chat"))showTabChat=j.get("show_tab_chat").getAsBoolean();
            if(j.has("map_preview"))mapPreview=j.get("map_preview").getAsBoolean();
            if(j.has("preview_on_command"))previewOnCommand=j.get("preview_on_command").getAsBoolean();
            if(j.has("large_map_preview"))largeMapPreview=j.get("large_map_preview").getAsBoolean();
            if(j.has("left_hand"))leftHand=j.get("left_hand").getAsBoolean();
            if(j.has("main_hand_left"))mainHandLeft=j.get("main_hand_left").getAsBoolean();
            if(j.has("hand_type"))handType=LeftHand.Type.parse(j.get("hand_type").getAsString());
            if(j.has("tab_chat_position"))tabChatPosition=Math.max(0,Math.min(3,j.get("tab_chat_position").getAsInt()));
            if(j.has("remember_drafts"))rememberDrafts=j.get("remember_drafts").getAsBoolean();
            if(j.has("unlimited_history"))unlimitedHistory=j.get("unlimited_history").getAsBoolean();
            if(j.has("shift_tab"))cycleTabs=j.get("shift_tab").getAsBoolean();
            if(j.has("team_bubbles"))bubbles=j.get("team_bubbles").getAsBoolean();
            if(j.has("bubble_characters"))bubbleCharacters=Math.max(20,Math.min(200,j.get("bubble_characters").getAsInt()));
            if(j.has("bubble_seconds"))bubbleSeconds=Math.max(1,Math.min(15,j.get("bubble_seconds").getAsInt()));
            if(j.has("chat_background_opacity"))chatBackground=Math.max(0,Math.min(100,j.get("chat_background_opacity").getAsInt()));
            if(j.has("tab_background_opacity"))tabBackgroundOpacity=Math.max(0,Math.min(100,j.get("tab_background_opacity").getAsInt()));
            typingTabBackgroundOpacity=j.has("typing_tab_background_opacity")?Math.max(0,Math.min(100,j.get("typing_tab_background_opacity").getAsInt())):tabBackgroundOpacity;
            if(j.has("non_player_chat"))nonPlayerChat=j.get("non_player_chat").getAsBoolean();
            if(j.has("notice_opacity"))noticeOpacity=Math.max(0,Math.min(100,j.get("notice_opacity").getAsInt()));
            if(j.has("notice_size"))noticeSize=Math.max(50,Math.min(150,j.get("notice_size").getAsInt()));
            if(j.has("notice_mode"))noticeMode=ServerNoticeRules.Mode.parse(j.get("notice_mode").getAsString());
            if(j.has("selected_channels"))for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("selected_channels").entrySet()){
                String v=e.getValue().getAsString();if(ChatRules.channel(v)!=null)selected.put(e.getKey(),v);
            }
            if(j.has("dev_hosts"))for(JsonElement e:j.getAsJsonArray("dev_hosts")){
                String v=e.getAsString().trim().toLowerCase(java.util.Locale.ROOT);if(!v.isEmpty())devHosts.add(v);
            }
        }catch(Exception e){System.err.println("Overcast Utilities: could not load settings: "+e.getMessage());}
    }
    static void save(){
        load();try{
            JsonObject j=new JsonObject();j.addProperty("schema_version",1);j.addProperty("reliable_chat",chat);j.addProperty("unread_counts",counts);j.addProperty("fullscreen_menu",fullscreen);j.addProperty("other_faces",otherFaces);j.addProperty("show_tab_chat",showTabChat);j.addProperty("map_preview",mapPreview);j.addProperty("tab_chat_position",tabChatPosition);
            j.addProperty("face_rig",faceRig);
            j.addProperty("kill_feed",killFeed);j.addProperty("hide_kill_chat",hideKillChat);
            j.addProperty("kill_confirm",killConfirm);j.addProperty("kill_confirm_size",killConfirmSize);
            j.addProperty("kill_confirm_sounds",killConfirmSounds);
            j.addProperty("kill_confirm_opacity",killConfirmOpacity);
            j.addProperty("hide_kill_confirm_chat",hideKillConfirmChat);
            j.addProperty("kill_feed_size",killFeedSize);j.addProperty("kill_border_opacity",killBorderOpacity);
            j.addProperty("kill_background_opacity",killBackgroundOpacity);j.addProperty("kill_feed_slide",killFeedSlide);j.addProperty("tab_hud_size",tabHudSize);
            j.addProperty("kill_border_color",killBorderColor);j.addProperty("death_border_color",deathBorderColor);
            j.addProperty("kill_feed_rows",killFeedRows);j.addProperty("kill_feed_seconds",killFeedSeconds);
            JsonObject hud=new JsonObject();hudPositions.forEach((key,p)->{JsonObject value=new JsonObject();value.addProperty("x",p.x);value.addProperty("y",p.y);hud.add(key,value);});j.add("hud_positions",hud);
            j.addProperty("preview_on_command",previewOnCommand);
            j.addProperty("large_map_preview",largeMapPreview);
            j.addProperty("left_hand",leftHand);
            j.addProperty("main_hand_left",mainHandLeft);j.addProperty("hand_type",handType.name());
            j.addProperty("remember_drafts",rememberDrafts);j.addProperty("unlimited_history",unlimitedHistory);j.addProperty("shift_tab",cycleTabs);j.addProperty("team_bubbles",bubbles);j.addProperty("bubble_characters",bubbleCharacters);j.addProperty("bubble_seconds",bubbleSeconds);
            j.addProperty("chat_background_opacity",chatBackground);j.addProperty("non_player_chat",nonPlayerChat);
            j.addProperty("tab_background_opacity",tabBackgroundOpacity);
            j.addProperty("typing_tab_background_opacity",typingTabBackgroundOpacity);
            j.addProperty("notice_opacity",noticeOpacity);j.addProperty("notice_mode",noticeMode.name());
            j.addProperty("notice_size",noticeSize);
            JsonObject channels=new JsonObject();selected.forEach(channels::addProperty);j.add("selected_channels",channels);
            JsonArray hosts=new JsonArray();for(String h:devHosts)hosts.add(new JsonPrimitive(h));j.add("dev_hosts",hosts);
            AtomicFiles.write(path(),new GsonBuilder().setPrettyPrinting().create().toJson(j).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){System.err.println("Overcast Utilities: could not save settings: "+e.getMessage());}
    }
}
