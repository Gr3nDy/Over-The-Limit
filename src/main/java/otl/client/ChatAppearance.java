package otl.client;

final class ChatAppearance {
    static int tabColor(int color,int percent){return (backgroundAlpha(color>>>24,percent)<<24)|(color&0xFFFFFF);}
    static int backgroundAlpha(int textAlpha,int percent){return Math.round(Math.max(0,Math.min(255,textAlpha))*Math.max(0,Math.min(100,percent))/100F);}
}
