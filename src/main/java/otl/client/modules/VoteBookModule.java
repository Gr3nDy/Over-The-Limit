package otl.client.modules;

import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Custom Votebook module: replaces the server vote book with the image-card VoteScreen. */
public final class VoteBookModule extends OtlModule {
    public VoteBookModule() { super("custom_votebook", "Custom Votebook", "VISUAL", 35); }
    public boolean enabled() { return UtilitySettings.customVotebook; }
    public void setEnabled(boolean on) { UtilitySettings.customVotebook = on; }
    public void reset() { UtilitySettings.restore("custom_votebook"); }
}
