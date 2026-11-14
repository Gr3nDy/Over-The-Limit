package otl.client;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Preferences only. Chat content, private names and drafts are never serialized. */
public final class UtilitySettings {
    private static boolean loaded;
    /** Active profile name; profiles keep their own HUD positions and module settings. */
    static String profile="Default";
    public static boolean chat=true, counts=true, otherFaces=true, showTabChat=true, mapPreview=true, nonPlayerChat=false;
    static boolean previewOnCommand=true;
    static boolean largeMapPreview=false;
    public static boolean leftHand=false;
    public static boolean bowOptimizer=false;
    static boolean bowOptimization=true,bowAccuracy=true;
    static boolean bowAccuracyPrefix=true;
    static BowAccuracy.Mode bowAccuracyMode=BowAccuracy.Mode.ALWAYS;
    static boolean bowAccuracyAnimate=true;
    static int bowAccuracyOpacity=25,bowAccuracyDecimals=2,bowAccuracySize=100;
    static int bowAccuracyFadeIn=0,bowAccuracyFadeOut=50;
    public static boolean minimap=false;
    static boolean minimapStatic=false,minimapCutaway=true,minimapBorder=true;
    static boolean minimapRotate=true,minimapCardinals=true,minimapCoordinates=true;
    static int minimapSize=100,minimapOpacity=25,minimapLargeOpacity=100;
    public static int minimapLargeBackground=25;
    public static int minimapZoom=2,minimapAltZoom=4;
    public static int minimapHeadSize=125;
    static boolean bowHitSound=false,bowKillSound=false,bowHitMarker=false;
    static int bowHitMarkerSize=100;
    static boolean mainHandLeft=false;
    static LeftHand.Type handType=LeftHand.Type.ALL;
    static int tabChatPosition=0;
    static String tabPositionLabel(){
        return new String[]{"Bottom Left","Bottom Right","Top Left","Top Right"}[Math.max(0,Math.min(3,tabChatPosition))];
    }
    public static boolean faceRig=false;
    public static boolean heightLimitOverlay=false;
    static boolean heightLimitIndicator=true;
    static boolean heightLimitTopOnly=false;
    static int heightLimitOpacity=25;
    static int heightLimitIndicatorSeconds=4;
    static int heightLimitSize=75;
    /** Top-centre team bar: heads per team with the match timer in the middle. */
    public static boolean teamBar=true;
    /** Heads per side (1-75) and the bar's scale; below 10 heads they stay full size. */
    public static int teamBarPlayers=5,teamBarSize=100;
    public static boolean teamBarHideBoss=false;
    /** Keep showing the bar while watching as an observer. */
    public static boolean teamBarObserver=true;
    /** A thin vertical bar of the team's colour beside each team row. */
    public static boolean teamBarTeamColor=true;
    public static boolean killFeed=true,hideKillChat=false;
    static KillFeedRules.HeadMode killFeedHeads=KillFeedRules.HeadMode.ONLY_ME;
    static KillFeedRules.FilterMode killFeedFilter=KillFeedRules.FilterMode.ALL;
    static int killFeedSize=75,killBorderOpacity=100,killBorderColor=0xFFBD45,deathBorderColor=0xFF5555,assistBorderColor=0x55FFFF;
    static int killBackgroundOpacity=25,tabHudSize=100;
    static boolean killFeedSlide=false;
    public static boolean killConfirm=false;
    static boolean killConfirmSounds=false;
    static boolean totalExpKills=false;
    static boolean killConfirmPlayerHead=false;
    static boolean hideKillConfirmChat=false;
    static int killConfirmSize=75,killConfirmOpacity=25;
    static int killFeedRows=5,killFeedSeconds=10;
    static int noticeOpacity=25;
    static int noticeSize=100;
    static ServerNoticeRules.Mode noticeMode=ServerNoticeRules.Mode.ALL;
    public static final Map<String,HudLayout.Position> hudPositions=new HashMap<>();
    static final Set<String> mapFavorites=new LinkedHashSet<>();
    static String favoriteKey(String path){
        if(path==null)return null;
        if(path.endsWith("/map.png"))path=path.substring(0,path.length()-8);
        if(path.startsWith("PublicMaps/")&&!path.startsWith("PublicMaps/main/")||path.startsWith("CommunityMaps/")&&!path.startsWith("CommunityMaps/master/"))return null;
        if(!path.startsWith("PublicMaps/main/")&&!path.startsWith("CommunityMaps/master/"))path="CommunityMaps/master/"+path;
        if(path.length()>256||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.matches(".*[\\r\\n\\t].*")||!path.matches("(?:PublicMaps/main|CommunityMaps/master)(?:/[^/]+)+"))return null;
        for(String part:path.split("/"))if(part.equals(".")||part.equals(".."))return null;
        return path;
    }
    public static boolean rememberDrafts=false, unlimitedHistory=false, cycleTabs=true, bubbles=false, bubbleTeamAndChat=false, regionOverlay=false, customVotebook=true;
    /** Auto GG: say "gg" once when the match ends. Off by default: it speaks for the player. */
    public static boolean autoGg=false;
    /** Auto GG while watching as an observer; off keeps a spectator quiet. */
    static boolean autoGgObserver=true;
    /** Milliseconds to wait before saying gg; 0 says it on the next tick. */
    static int autoGgDelay=0;
    /** Spread the wait anywhere up to the delay instead of always using exactly it. */
    static boolean autoGgRandom=false;
    public static boolean regionHud=false;
    static boolean regionHudPrefix=true;
    static int regionHudBackground=25;
    static int bubbleCharacters=80, bubbleSeconds=4;
    static int tabBackgroundOpacity=25;
    static int typingTabBackgroundOpacity=25;
    /** Draw the chat scrollbar while browsing history. */
    static boolean chatScrollbar=true;
    static final Map<String,String> selected=new HashMap<>();
    /** Extra hosts allowed in addition to oc.tc, for a local test server. */
    static final Set<String> devHosts=new LinkedHashSet<>();
    /** Set when an unreadable settings file could not be backed up, so saving cannot overwrite it. */
    private static boolean corrupt;
    static Path path(){return ConfigDir.parts("settings.json").toPath();}
    static void load(){
        if(loaded)return;loaded=true;
        JsonObject j=null;
        Path file=path();
        try{
            if(Files.exists(file))j=new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){
            System.err.println("Overcast Utilities: could not load settings: "+e.getMessage());
            try{
                Path bad=file.resolveSibling("settings.json.bad");
                Files.move(file,bad,StandardCopyOption.REPLACE_EXISTING);
                System.err.println("Overcast Utilities: unreadable settings kept as "+bad.getFileName());
            }catch(Exception move){
                corrupt=true;
                System.err.println("Overcast Utilities: could not back up unreadable settings; saving disabled");
            }
        }
        if(j!=null)read(j);
        Profiles.ensureDefault();
    }
    static void read(JsonObject j){
        read(j,"profile",e->profile=Profiles.safeName(e.getAsString()));
        read(j,"profile_order",e->{Profiles.profileOrder.clear();for(JsonElement el:e.getAsJsonArray())Profiles.profileOrder.add(Profiles.safeName(el.getAsString()));});
        read(j,"reliable_chat",e->chat=e.getAsBoolean());
        read(j,"face_rig",e->faceRig=e.getAsBoolean());
        // Temporarily unavailable; ignore saved ON preferences.
        heightLimitOverlay=false;nonPlayerChat=false;
            read(j,"height_limit_indicator",e->heightLimitIndicator=e.getAsBoolean());
            read(j,"height_limit_top_only",e->heightLimitTopOnly=e.getAsBoolean());
        read(j,"height_limit_opacity",e->heightLimitOpacity=Math.max(0,Math.min(100,e.getAsInt())));
            read(j,"height_limit_indicator_seconds",e->heightLimitIndicatorSeconds=Math.max(1,Math.min(10,e.getAsInt())));
            read(j,"height_limit_size",e->heightLimitSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"team_bar",e->teamBar=e.getAsBoolean());
        read(j,"team_bar_players",e->teamBarPlayers=Math.max(1,Math.min(75,e.getAsInt())));
        read(j,"team_bar_size",e->teamBarSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"team_bar_hide_boss",e->teamBarHideBoss=e.getAsBoolean());
        read(j,"team_bar_observer",e->teamBarObserver=e.getAsBoolean());
        read(j,"team_bar_team_color",e->teamBarTeamColor=e.getAsBoolean());
        read(j,"kill_feed",e->killFeed=e.getAsBoolean());
        read(j,"kill_feed_heads",e->killFeedHeads=KillFeedRules.HeadMode.parse(e.getAsString()));
        read(j,"kill_feed_filter",e->killFeedFilter=KillFeedRules.FilterMode.parse(e.getAsString()));
        read(j,"kill_confirm",e->killConfirm=e.getAsBoolean());
        read(j,"kill_confirm_sounds",e->killConfirmSounds=e.getAsBoolean());
        read(j,"total_exp_kills",e->totalExpKills=e.getAsBoolean());
        read(j,"kill_confirm_player_head",e->killConfirmPlayerHead=e.getAsBoolean());
        read(j,"hide_kill_confirm_chat",e->hideKillConfirmChat=e.getAsBoolean());
        read(j,"kill_confirm_size",e->killConfirmSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"kill_confirm_opacity",e->killConfirmOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"hide_kill_chat",e->hideKillChat=e.getAsBoolean());
        read(j,"kill_feed_size",e->killFeedSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"kill_border_opacity",e->killBorderOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"kill_background_opacity",e->killBackgroundOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"kill_feed_slide",e->killFeedSlide=e.getAsBoolean());
        read(j,"tab_hud_size",e->tabHudSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"kill_border_color",e->killBorderColor=e.getAsInt()&0xFFFFFF);
        read(j,"death_border_color",e->deathBorderColor=e.getAsInt()&0xFFFFFF);
        read(j,"assist_border_color",e->assistBorderColor=e.getAsInt()&0xFFFFFF);
        read(j,"kill_feed_rows",e->killFeedRows=Math.max(1,Math.min(5,e.getAsInt())));
        read(j,"kill_feed_seconds",e->killFeedSeconds=Math.max(1,Math.min(30,e.getAsInt())));
        if(j.has("hud_positions"))for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("hud_positions").entrySet()){
            if(!HudLayout.valid(e.getKey()))continue;
            try{JsonObject p=e.getValue().getAsJsonObject();hudPositions.put(e.getKey(),new HudLayout.Position(p.get("x").getAsDouble(),p.get("y").getAsDouble()));}catch(RuntimeException invalid){System.err.println("OTL: ignored invalid HUD position");}
        }
        read(j,"unread_counts",e->counts=e.getAsBoolean());
        read(j,"other_faces",e->otherFaces=e.getAsBoolean());
        read(j,"show_tab_chat",e->showTabChat=e.getAsBoolean());
        read(j,"map_preview",e->mapPreview=e.getAsBoolean());
        read(j,"preview_on_command",e->previewOnCommand=e.getAsBoolean());
        read(j,"large_map_preview",e->largeMapPreview=e.getAsBoolean());
        read(j,"left_hand",e->leftHand=e.getAsBoolean());
        read(j,"bow_optimizer",e->bowOptimizer=e.getAsBoolean());
        read(j,"bow_optimization",e->bowOptimization=e.getAsBoolean());
        read(j,"bow_accuracy",e->bowAccuracy=e.getAsBoolean());
        read(j,"bow_accuracy_prefix",e->bowAccuracyPrefix=e.getAsBoolean());
        read(j,"bow_accuracy_on_hit",e->bowAccuracyMode=e.getAsBoolean()?BowAccuracy.Mode.ON_HIT:BowAccuracy.Mode.ALWAYS);
        read(j,"bow_accuracy_mode",e->bowAccuracyMode=BowAccuracy.Mode.valueOf(e.getAsString()));
        read(j,"bow_accuracy_animate",e->bowAccuracyAnimate=e.getAsBoolean());
        read(j,"bow_accuracy_opacity",e->bowAccuracyOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"bow_accuracy_decimals",e->bowAccuracyDecimals=Math.max(0,Math.min(2,e.getAsInt())));
        read(j,"bow_accuracy_size",e->bowAccuracySize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"bow_accuracy_fade_in",e->bowAccuracyFadeIn=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"bow_accuracy_fade_out",e->bowAccuracyFadeOut=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"minimap",e->minimap=e.getAsBoolean());
        read(j,"minimap_static",e->minimapStatic=e.getAsBoolean());
        read(j,"minimap_cutaway",e->minimapCutaway=e.getAsBoolean());
        read(j,"minimap_border",e->minimapBorder=e.getAsBoolean());
        read(j,"minimap_rotate",e->minimapRotate=e.getAsBoolean());
        read(j,"minimap_cardinals",e->minimapCardinals=e.getAsBoolean());
        read(j,"minimap_coordinates",e->minimapCoordinates=e.getAsBoolean());
        read(j,"minimap_size",e->minimapSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"minimap_opacity",e->minimapOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"minimap_large_opacity",e->minimapLargeOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"minimap_large_background",e->minimapLargeBackground=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"minimap_zoom",e->minimapZoom=Math.max(1,Math.min(5,e.getAsInt())));
        read(j,"minimap_alt_zoom",e->minimapAltZoom=Math.max(1,Math.min(5,e.getAsInt())));
        read(j,"minimap_head_size",e->minimapHeadSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"region_overlay",e->regionOverlay=e.getAsBoolean());
        read(j,"region_hud",e->regionHud=e.getAsBoolean());
        read(j,"region_hud_prefix",e->regionHudPrefix=e.getAsBoolean());
        read(j,"region_hud_background",e->regionHudBackground=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"custom_votebook",e->customVotebook=e.getAsBoolean());
        read(j,"auto_gg",e->autoGg=e.getAsBoolean());
        read(j,"auto_gg_observer",e->autoGgObserver=e.getAsBoolean());
        read(j,"auto_gg_delay",e->autoGgDelay=Math.max(0,Math.min(5000,e.getAsInt())));
        read(j,"auto_gg_random",e->autoGgRandom=e.getAsBoolean());
        read(j,"bow_hit_sound",e->bowHitSound=e.getAsBoolean());
        read(j,"bow_kill_sound",e->bowKillSound=e.getAsBoolean());
        read(j,"bow_hit_marker",e->bowHitMarker=e.getAsBoolean());
        read(j,"bow_hit_marker_size",e->bowHitMarkerSize=Math.max(50,Math.min(200,e.getAsInt())));
        read(j,"main_hand_left",e->mainHandLeft=e.getAsBoolean());
        read(j,"hand_type",e->handType=LeftHand.Type.parse(e.getAsString()));
        read(j,"tab_chat_position",e->tabChatPosition=Math.max(0,Math.min(3,e.getAsInt())));
        read(j,"remember_drafts",e->rememberDrafts=e.getAsBoolean());
        read(j,"unlimited_history",e->unlimitedHistory=e.getAsBoolean());
        read(j,"shift_tab",e->cycleTabs=e.getAsBoolean());
        read(j,"team_bubbles",e->bubbles=e.getAsBoolean());
        read(j,"bubble_team_and_chat",e->bubbleTeamAndChat=e.getAsBoolean());
        read(j,"bubble_characters",e->bubbleCharacters=Math.max(20,Math.min(200,e.getAsInt())));
        read(j,"bubble_seconds",e->bubbleSeconds=Math.max(1,Math.min(15,e.getAsInt())));
        read(j,"tab_background_opacity",e->tabBackgroundOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"typing_tab_background_opacity",e->typingTabBackgroundOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"chat_scrollbar",e->chatScrollbar=e.getAsBoolean());
        read(j,"notice_opacity",e->noticeOpacity=Math.max(0,Math.min(100,e.getAsInt())));
        read(j,"notice_size",e->noticeSize=Math.max(50,Math.min(150,e.getAsInt())));
        read(j,"notice_mode",e->noticeMode=ServerNoticeRules.Mode.parse(e.getAsString()));
        if(j.has("map_favorites"))for(JsonElement e:j.getAsJsonArray("map_favorites")){
            String key=favoriteKey(e.getAsString());if(key!=null&&mapFavorites.size()<2000)mapFavorites.add(key);
        }
        if(j.has("selected_channels"))for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("selected_channels").entrySet()){
            String v=e.getValue().getAsString();if(ChatRules.channel(v)!=null)selected.put(e.getKey(),v);
        }
        if(j.has("dev_hosts"))for(JsonElement e:j.getAsJsonArray("dev_hosts")){
            String v=e.getAsString().trim().toLowerCase(java.util.Locale.ROOT);if(!v.isEmpty())devHosts.add(v);
        }
    }
    private static void read(JsonObject j,String key,Consumer<JsonElement> apply){
        try{
            JsonElement value=j.get(key);
            if(value!=null)apply.accept(value);
        }catch(RuntimeException bad){System.err.println("Overcast Utilities: ignored invalid setting "+key+": "+bad.getMessage());}
    }
    static void save(){
        load();if(corrupt)return;
        try{
            write(path(),snapshot());
            write(Profiles.file(profile),profileSnapshot());
        }catch(Exception e){System.err.println("Overcast Utilities: could not save settings: "+e.getMessage());}
    }
    static JsonObject snapshot(){
            JsonObject j=new JsonObject();j.addProperty("schema_version",1);j.addProperty("profile",profile);j.addProperty("reliable_chat",chat);j.addProperty("unread_counts",counts);j.addProperty("other_faces",otherFaces);j.addProperty("show_tab_chat",showTabChat);j.addProperty("map_preview",mapPreview);j.addProperty("tab_chat_position",tabChatPosition);
            j.addProperty("face_rig",faceRig);
            j.addProperty("height_limit_overlay",heightLimitOverlay);
            j.addProperty("height_limit_indicator",heightLimitIndicator);
            j.addProperty("height_limit_top_only",heightLimitTopOnly);
            j.addProperty("height_limit_opacity",heightLimitOpacity);
            j.addProperty("height_limit_indicator_seconds",heightLimitIndicatorSeconds);
            j.addProperty("height_limit_size",heightLimitSize);
            j.addProperty("team_bar",teamBar);
            j.addProperty("team_bar_players",teamBarPlayers);j.addProperty("team_bar_size",teamBarSize);
            j.addProperty("team_bar_hide_boss",teamBarHideBoss);
            j.addProperty("team_bar_observer",teamBarObserver);
        j.addProperty("team_bar_team_color",teamBarTeamColor);
            j.addProperty("kill_feed",killFeed);j.addProperty("hide_kill_chat",hideKillChat);
            j.addProperty("kill_feed_heads",killFeedHeads.name());
            j.addProperty("kill_feed_filter",killFeedFilter.name());
            j.addProperty("kill_confirm",killConfirm);j.addProperty("kill_confirm_size",killConfirmSize);
            j.addProperty("kill_confirm_sounds",killConfirmSounds);
            j.addProperty("total_exp_kills",totalExpKills);
            j.addProperty("kill_confirm_player_head",killConfirmPlayerHead);
            j.addProperty("kill_confirm_opacity",killConfirmOpacity);
            j.addProperty("hide_kill_confirm_chat",hideKillConfirmChat);
            j.addProperty("kill_feed_size",killFeedSize);j.addProperty("kill_border_opacity",killBorderOpacity);
            j.addProperty("kill_background_opacity",killBackgroundOpacity);j.addProperty("kill_feed_slide",killFeedSlide);j.addProperty("tab_hud_size",tabHudSize);
            j.addProperty("kill_border_color",killBorderColor);j.addProperty("death_border_color",deathBorderColor);
            j.addProperty("assist_border_color",assistBorderColor);
            j.addProperty("kill_feed_rows",killFeedRows);j.addProperty("kill_feed_seconds",killFeedSeconds);
            JsonObject hud=new JsonObject();hudPositions.forEach((key,p)->{JsonObject value=new JsonObject();value.addProperty("x",p.x);value.addProperty("y",p.y);hud.add(key,value);});j.add("hud_positions",hud);
            j.addProperty("preview_on_command",previewOnCommand);
            j.addProperty("large_map_preview",largeMapPreview);
            j.addProperty("left_hand",leftHand);
            j.addProperty("bow_optimizer",bowOptimizer);
            j.addProperty("bow_optimization",bowOptimization);
            j.addProperty("bow_accuracy",bowAccuracy);
            j.addProperty("bow_accuracy_prefix",bowAccuracyPrefix);
            j.addProperty("bow_accuracy_mode",bowAccuracyMode.name());
            j.addProperty("bow_accuracy_animate",bowAccuracyAnimate);
            j.addProperty("bow_accuracy_opacity",bowAccuracyOpacity);
            j.addProperty("bow_accuracy_decimals",bowAccuracyDecimals);
            j.addProperty("bow_accuracy_size",bowAccuracySize);
            j.addProperty("bow_accuracy_fade_in",bowAccuracyFadeIn);
            j.addProperty("bow_accuracy_fade_out",bowAccuracyFadeOut);
            j.addProperty("minimap",minimap);
            j.addProperty("minimap_static",minimapStatic);
            j.addProperty("minimap_cutaway",minimapCutaway);
            j.addProperty("minimap_border",minimapBorder);
            j.addProperty("minimap_rotate",minimapRotate);
            j.addProperty("minimap_cardinals",minimapCardinals);
            j.addProperty("minimap_coordinates",minimapCoordinates);
            j.addProperty("minimap_size",minimapSize);
            j.addProperty("minimap_opacity",minimapOpacity);
            j.addProperty("minimap_large_opacity",minimapLargeOpacity);
            j.addProperty("minimap_large_background",minimapLargeBackground);
            j.addProperty("minimap_zoom",minimapZoom);
            j.addProperty("minimap_alt_zoom",minimapAltZoom);
            j.addProperty("minimap_head_size",minimapHeadSize);
            j.addProperty("region_overlay",regionOverlay);
            j.addProperty("region_hud",regionHud);
            j.addProperty("region_hud_prefix",regionHudPrefix);
            j.addProperty("region_hud_background",regionHudBackground);
            j.addProperty("custom_votebook",customVotebook);
            j.addProperty("auto_gg",autoGg);
            j.addProperty("auto_gg_observer",autoGgObserver);
            j.addProperty("auto_gg_delay",autoGgDelay);
            j.addProperty("auto_gg_random",autoGgRandom);
            j.addProperty("bow_hit_sound",bowHitSound);
            j.addProperty("bow_kill_sound",bowKillSound);
            j.addProperty("bow_hit_marker",bowHitMarker);
            j.addProperty("bow_hit_marker_size",bowHitMarkerSize);
            j.addProperty("main_hand_left",mainHandLeft);j.addProperty("hand_type",handType.name());
            j.addProperty("remember_drafts",rememberDrafts);j.addProperty("unlimited_history",unlimitedHistory);j.addProperty("shift_tab",cycleTabs);j.addProperty("team_bubbles",bubbles);j.addProperty("bubble_team_and_chat",bubbleTeamAndChat);j.addProperty("bubble_characters",bubbleCharacters);j.addProperty("bubble_seconds",bubbleSeconds);
            j.addProperty("non_player_chat",nonPlayerChat);
            j.addProperty("tab_background_opacity",tabBackgroundOpacity);
            j.addProperty("typing_tab_background_opacity",typingTabBackgroundOpacity);
            j.addProperty("chat_scrollbar",chatScrollbar);
            j.addProperty("notice_opacity",noticeOpacity);j.addProperty("notice_mode",noticeMode.name());
            j.addProperty("notice_size",noticeSize);
            JsonArray favorites=new JsonArray();for(String key:mapFavorites)favorites.add(new JsonPrimitive(key));j.add("map_favorites",favorites);
            JsonArray order=new JsonArray();for(String n:Profiles.profileOrder)order.add(new JsonPrimitive(n));j.add("profile_order",order);
            JsonObject channels=new JsonObject();selected.forEach(channels::addProperty);j.add("selected_channels",channels);
            JsonArray hosts=new JsonArray();for(String h:devHosts)hosts.add(new JsonPrimitive(h));j.add("dev_hosts",hosts);
            return j;
    }
    /** Restores only the given keys from the factory snapshot, leaving other modules alone. */
    public static void restore(String... keys){
        JsonObject j=new JsonObject();
        for(String key:keys)if(DEFAULTS.has(key))j.add(key,DEFAULTS.get(key));
        read(j);
    }
    /** HUD positions and settings return to defaults; favorites, channels and dev hosts are shared. */
    static void resetDefaults(){hudPositions.clear();read(DEFAULTS);}
    static JsonObject profileSnapshot(){
        JsonObject j=snapshot();
        j.remove("map_favorites");j.remove("selected_channels");j.remove("dev_hosts");
        j.addProperty("profile",profile);
        return j;
    }
    static void write(Path file,JsonObject j)throws Exception{
        byte[] data=new GsonBuilder().setPrettyPrinting().create().toJson(j).getBytes(StandardCharsets.UTF_8);
        if(Files.isRegularFile(file)&&Arrays.equals(Files.readAllBytes(file),data))return;
        AtomicFiles.write(file,data);
    }
    /** Captured before any load, so a new profile always starts from factory settings. */
    static final JsonObject DEFAULTS=profileSnapshot();
}
