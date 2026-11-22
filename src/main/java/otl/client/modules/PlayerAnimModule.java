package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.OtlModule;
import otl.client.PlayerAnimSettings;
import otl.client.UtilitySettings;

/** Player Animation: idle limb motion for everyone, plus local emotes and a skateboard. */
public final class PlayerAnimModule extends OtlModule {
    public PlayerAnimModule(){super("player_animation","Player Animation","VISUAL",20);}
    public boolean enabled(){return UtilitySettings.playerAnimation;}
    public void setEnabled(boolean on){UtilitySettings.playerAnimation=on;}
    public GuiScreen settings(GuiScreen back){return new PlayerAnimSettings(back);}
    public void reset(){UtilitySettings.restore("player_animation","emote_camera","board_share");}
}
