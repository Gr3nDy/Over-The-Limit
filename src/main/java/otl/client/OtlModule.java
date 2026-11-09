package otl.client;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;

/**
 * One product feature. Adding an entry to ALL is all it takes for the module to
 * show up in the modules screen and be toggleable.
 *
 * Rules for implementations:
 * - enabled() reads the flag at the point of use, so toggling needs no lifecycle calls.
 * - setEnabled() saves nothing; the caller persists once, after the toggle.
 * - Toggling must be safe mid-game (no leaked GL state, no unregistered listeners).
 */
abstract class OtlModule {
    final String id, name;

    OtlModule(String id, String name) {
        this.id = id;
        this.name = name;
    }

    abstract boolean enabled();

    abstract void setEnabled(boolean on);

    boolean available(){return true;}

    /** Options screen for this module, or null when it has none. */
    GuiScreen settings(GuiScreen back) {
        return null;
    }

    static final List<OtlModule> ALL = Arrays.asList(
            new OtlModule("face_rig", "Face Rig") {
                boolean enabled() { return UtilitySettings.faceRig; }
                void setEnabled(boolean on) { UtilitySettings.faceRig = on; }
                GuiScreen settings(GuiScreen back) { return new FaceScreen(back); }
            },
            new OtlModule("reliable_chat", "Reliable Chat") {
                boolean enabled() { return UtilitySettings.chat; }
                void setEnabled(boolean on) { UtilitySettings.chat = on; ReliableChat.applyPreferences(); }
                GuiScreen settings(GuiScreen back) { return new UtilityScreen(true,back); }
            },
            new OtlModule("map_preview", "Map Preview") {
                boolean enabled() { return UtilitySettings.mapPreview; }
                void setEnabled(boolean on) { UtilitySettings.mapPreview = on; }
                GuiScreen settings(GuiScreen back) { return new MapPreviewSettings(back); }
            },
            new OtlModule("left_hand", "Prefered Hand") {
                boolean enabled() { return UtilitySettings.leftHand; }
                void setEnabled(boolean on) { UtilitySettings.leftHand = on; }
                GuiScreen settings(GuiScreen back) { return new PreferedHandSettings(back); }
            },
            new OtlModule("kill_feed", "Kill Feed") {
                boolean enabled() { return UtilitySettings.killFeed; }
                void setEnabled(boolean on) { UtilitySettings.killFeed=on; }
                GuiScreen settings(GuiScreen back) { return new KillFeedSettings(back); }
            },
            new OtlModule("kill_confirm", "Kill Confirm") {
                boolean enabled() { return UtilitySettings.killConfirm; }
                void setEnabled(boolean on) { UtilitySettings.killConfirm=on;KillConfirm.state.clear(); }
                GuiScreen settings(GuiScreen back) { return new KillConfirmSettings(back); }
            },
            new OtlModule("server_notices", "Server Notices") {
                boolean enabled() { return UtilitySettings.nonPlayerChat; }
                void setEnabled(boolean on) { UtilitySettings.nonPlayerChat=on; }
                GuiScreen settings(GuiScreen back) { return new ServerNoticeSettings(back); }
            },
            new OtlModule("bow_optimizer", "Bow Optimizer") {
                boolean enabled() { return UtilitySettings.bowOptimizer; }
                void setEnabled(boolean on) { UtilitySettings.bowOptimizer=on;BowOptimizer.INSTANCE.echoes.clear();BowAccuracy.INSTANCE.reset(); }
                GuiScreen settings(GuiScreen back) { return new BowOptimizerSettings(back); }
            },
            new OtlModule("height_limit_overlay", "Height Limit Overlay") {
                boolean available() { return false; }
                boolean enabled() { return false; }
                void setEnabled(boolean on) { UtilitySettings.heightLimitOverlay=false; }
            }
    );
}
