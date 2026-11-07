package otl.client;

/** Shared geometry for drawing and dragging the chat scrollbar. Bottom means newest. */
final class ChatScroll {
    static final class Bar {
        final int x,top,height,trackTop,trackHeight,thumbTop,thumbHeight,max;
        Bar(int x,int top,int height,int visible,int total,int offset){
            this.x=x;this.top=top;this.height=height;trackTop=top+10;trackHeight=Math.max(1,height-20);max=Math.max(0,total-visible);
            thumbHeight=Math.min(trackHeight,Math.max(8,trackHeight*visible/Math.max(1,total)));
            thumbTop=trackTop+(int)Math.round((trackHeight-thumbHeight)*(1-Math.max(0,Math.min(max,offset))/(double)Math.max(1,max)));
        }
        boolean contains(int mx,int my){return mx>=x&&mx<x+9&&my>=top&&my<top+height;}
        int offset(int mouseY,int grab){return (int)Math.round(max*Math.max(0,Math.min(1,1-(mouseY-grab-trackTop)/(double)Math.max(1,trackHeight-thumbHeight))));}
    }
    static Bar bar(int screenWidth,int screenHeight,int chatWidth,float scale,int visible,int total,int offset){
        if(total<=visible||visible<=0)return null;
        int height=Math.min(screenHeight-54,Math.max(30,(int)Math.ceil(visible*9*scale)));
        return new Bar(Math.min(screenWidth-11,chatWidth+8),screenHeight-46-height,height,visible,total,offset);
    }
}
