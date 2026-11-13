package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.OtlModule;
import otl.client.TeamBarSettings;
import otl.client.UtilitySettings;

/** Team Bar: heads per team at the top centre, with the match timer in the middle. */
public final class TeamBarModule extends OtlModule {
    public TeamBarModule() { super("team_bar", "Team Bar", "HUD", 45); }
    public boolean enabled() { return UtilitySettings.teamBar; }
    public void setEnabled(boolean on) { UtilitySettings.teamBar = on; }
    public GuiScreen settings(GuiScreen back) { return new TeamBarSettings(back); }
    public void reset() { UtilitySettings.restore("team_bar","team_bar_players","team_bar_size","team_bar_hide_boss","team_bar_observer","team_bar_team_color"); }
}
