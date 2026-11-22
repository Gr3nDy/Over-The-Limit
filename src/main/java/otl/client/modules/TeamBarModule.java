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
    public void reset() { UtilitySettings.restore("team_bar","team_bar_players","team_bar_size","team_bar_hide_boss","team_bar_hide_vanilla_boss_health","team_bar_hide_on_tab","team_bar_hide_scoreboard","team_bar_hide_heads","team_bar_rounded_heads","team_bar_count","team_bar_observer","team_bar_team_color","team_bar_objectives","team_bar_objectives_compact","team_bar_control_points","team_bar_score_style","team_bar_header","team_bar_dead_opacity","team_bar_background_opacity","team_bar_outline_opacity"); }
}
