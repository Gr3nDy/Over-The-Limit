package otl.client;

final class ChatAppearance {
    static int backgroundAlpha(int textAlpha,int percent){return Math.round(Math.max(0,Math.min(255,textAlpha))*Math.max(0,Math.min(100,percent))/100F);}
}
