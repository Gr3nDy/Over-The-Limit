package otl.client;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilitySettingsProfileTest {
    @Test public void deadHeadOpacityPersistsClampsAndResets(){
        int before=UtilitySettings.teamBarDeadOpacity;
        try{
            JsonObject setting=new JsonObject();
            setting.addProperty("team_bar_dead_opacity",75);
            UtilitySettings.read(setting);
            assertEquals(75,UtilitySettings.snapshot().get("team_bar_dead_opacity").getAsInt());
            setting.addProperty("team_bar_dead_opacity",0);
            UtilitySettings.read(setting);
            assertEquals(25,UtilitySettings.teamBarDeadOpacity);
            UtilitySettings.restore("team_bar_dead_opacity");
            assertEquals(50,UtilitySettings.teamBarDeadOpacity);
        }finally{UtilitySettings.teamBarDeadOpacity=before;}
    }
    @Test public void factoryDefaultsAreCapturedForNewProfiles()throws Exception{
        Field field=UtilitySettings.class.getDeclaredField("DEFAULTS");field.setAccessible(true);
        JsonObject defaults=(JsonObject)field.get(null);
        assertEquals("Default",defaults.get("profile").getAsString());
        assertFalse(defaults.get("minimap").getAsBoolean());
        assertEquals(100,defaults.get("minimap_size").getAsInt());
        assertTrue(defaults.get("minimap_border").getAsBoolean());
        assertEquals(0,defaults.getAsJsonObject("hud_positions").entrySet().size());
        assertFalse(defaults.has("map_favorites"));
        assertFalse(defaults.has("selected_channels"));
        assertFalse(defaults.has("dev_hosts"));
        assertEquals(25,defaults.get("minimap_opacity").getAsInt());
        assertEquals(25,defaults.get("kill_background_opacity").getAsInt());
        assertFalse(defaults.has("chat_background_opacity"));
        assertTrue(defaults.get("chat_scrollbar").getAsBoolean());
        // Every Team Bar option is per profile, including the new colour line.
        assertTrue(defaults.get("team_bar_team_color").getAsBoolean());
        assertEquals(10,defaults.get("team_bar_players").getAsInt());
        assertEquals(150,defaults.get("team_bar_size").getAsInt());
        assertEquals(50,defaults.get("team_bar_dead_opacity").getAsInt());
        assertTrue(defaults.get("team_bar_objectives").getAsBoolean());
        assertEquals("Default",defaults.get("profile").getAsString());
    }
}
