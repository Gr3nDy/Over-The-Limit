package otl.client;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilitySettingsProfileTest {
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
        assertEquals(25,defaults.get("chat_background_opacity").getAsInt());
        assertEquals("Default",defaults.get("profile").getAsString());
    }
}
