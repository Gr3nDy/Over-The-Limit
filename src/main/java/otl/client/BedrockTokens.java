package otl.client;

/** Shared geometry and semantic colors for OTL screens (Lunar Client design tokens). */
final class BedrockTokens {
    static final int PANEL_PAD=14,HEADER_H=26,CONTROL_H=20,CHOICE_H=34,FOOTER_H=20,ICON=12,GAP=6;
    /** Surfaces keep a little world visible through the chrome, like the reference menu. */
    static final int SURFACE_PANEL=0xB412141B,SURFACE_EDGE=0xFF2A2E38,SURFACE_INSET=0xB80E1015,SURFACE_CARD=0xA81A1D24;
    static final int TEXT_PRIMARY=0xFFE8EAF0,TEXT_MUTED=0xFFA0A5B0,TEXT_DIM=0xFF8A919E;
    /** Lunar: blue primary, green enabled, red disabled. */
    static final int STATE_SELECTED=0xFF3B82F6,STATE_ACCENT=0xFF3B82F6,STATE_HOVER=0xBD232832;
    static final int STATE_ENABLED=0xFF16A34A,STATE_DISABLED=0xFFD93025,STATE_LOCKED=0xFF6B7280;
    private BedrockTokens(){}
}
