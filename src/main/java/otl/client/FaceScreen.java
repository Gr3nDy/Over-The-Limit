package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** One settings page and an inline, transactional skin editor. */
public final class FaceScreen extends CompactScreen {
    private final GuiScreen parent;
    private boolean editing,dirty;
    private int tool=1,cell,gx,gy,leftWidth,scroll;
    private int[] original,working;
    private final Deque<int[]> undo=new ArrayDeque<>();
    private String status="";
    public FaceScreen(){this(null);}
    public FaceScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    private ResourceLocation skin(){return mc.thePlayer.getLocationSkin();}
    protected void initPanel(){FaceConfig.load();UtilitySettings.load();leftWidth=Math.max(160,(width-36)/2);if(editing)FaceMarks.replace(skin(),working,false);rebuild();}
    private void button(int id,int x,int y,int w,String text){buttonList.add(new GuiButton(id,x,y,w,20,text));}
    private void row(int id,int y,String text){y=42+(id-2-scroll)*22;if(y>=42&&y+20<=height-38)button(id,12,y,leftWidth,text);}
    public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();if(!editing&&wheel!=0){scroll=Math.max(0,Math.min(Math.max(0,9-(height-80)/22),scroll+(wheel>0?-1:1)));rebuild();}}
    private String on(boolean value){return value?"ON":"OFF";}
    private void rebuild(){
        buttonList.clear();button(1,width-132,8,120,"Fullscreen: "+on(UtilitySettings.fullscreen));
        buttonList.add(new ModuleReset(90,width-19,34,"face_rig"));
        if(editing){
            cell=Math.max(6,Math.min(20,(height-125)/8));gx=12;gy=50;
            for(int i=0;i<5;i++){button(20+i,gx+cell*8+8,gy+i*22,Math.max(54,leftWidth-cell*8-8),(tool==i?"> ":"")+new String[]{"Erase","Eye","Pupil","Brow","Lid"}[i]);}
            button(25,12,height-68,70,"Undo");button(26,86,height-68,Math.max(90,leftWidth-74),"Reset edits");
            button(27,12,height-42,leftWidth/2-2,"Save");button(28,14+leftWidth/2,height-42,leftWidth/2-2,"Cancel");
        }else{
            row(2,42,"FaceRig: "+on(UtilitySettings.faceRig));
            row(3,64,"Other Player's FaceRig: "+on(UtilitySettings.otherFaces));
            row(4,92,"World lighting: "+(FaceConfig.naturalLight?"Natural":"Fullbright"));
            row(5,114,"Over the hat layer: "+FaceConfig.label(FaceConfig.hat));
            row(6,142,"Blush: "+FaceConfig.label(FaceConfig.blush));
            row(7,164,"Glowing Eyes: "+FaceConfig.label(FaceConfig.glow));
            row(8,192,"Glances: "+FaceConfig.label(FaceConfig.glances));
            row(9,214,"Blinking: "+FaceConfig.label(FaceConfig.blinking));
            row(10,236,String.format(Locale.ROOT,"Blink interval: %.1f s",FaceConfig.blinkSeconds));
            button(11,12,height-30,92,"Edit face");button(12,108,height-30,92,"Reset");
            for(GuiButton b:buttonList)if(b.id>=4&&b.id<=10)b.enabled=(UtilitySettings.faceRig||UtilitySettings.otherFaces)&&(b.id!=10||FaceConfig.blinking!=2);
        }
        button(0,width-92,height-30,80,"Done");
    }
    protected void actionPerformed(GuiButton b){
        switch(b.id){
            case 90:((ModuleReset)b).reset();rebuild();return;
            case 0: if(editing){cancel();}mc.displayGuiScreen(parent);return;
            case 1: UtilitySettings.fullscreen=!UtilitySettings.fullscreen;UtilitySettings.save();resizePanel();return;
            case 2: UtilitySettings.faceRig=!UtilitySettings.faceRig;UtilitySettings.save();break;
            case 3: UtilitySettings.otherFaces=!UtilitySettings.otherFaces;UtilitySettings.save();break;
            case 4: FaceConfig.naturalLight=!FaceConfig.naturalLight;break;
            case 5: FaceConfig.hat=(FaceConfig.hat+1)%3;break;
            case 6: FaceConfig.blush=(FaceConfig.blush+1)%3;break;
            case 7: FaceConfig.glow=(FaceConfig.glow+1)%3;break;
            case 8: FaceConfig.glances=(FaceConfig.glances+1)%3;break;
            case 9: FaceConfig.blinking=(FaceConfig.blinking+1)%3;break;
            case 10: FaceConfig.blinkSeconds=Math.max(1.5f,Math.min(8,FaceConfig.blinkSeconds+(isShiftKeyDown()?-.1f:.1f)));break;
            case 11: begin();break;
            case 12: mc.displayGuiScreen(new GuiYesNo((yes,id)->{if(yes){FaceConfig.reset();UtilitySettings.otherFaces=true;UtilitySettings.save();status="Settings reset";}mc.displayGuiScreen(this);},"Reset Face Rig settings?","Painted face edits are kept.",0));return;
            case 25: if(!undo.isEmpty()){working=undo.pop();updateEdits();}break;
            case 26: mc.displayGuiScreen(new GuiYesNo((yes,id)->{if(yes){undo.push(working.clone());int[] auto=FaceMarks.auto(skin());working=auto==null?new int[64]:auto.clone();updateEdits();}mc.displayGuiScreen(this);},"Reset painted face edits?","Save to keep the reset, or Cancel to undo it.",0));return;
            case 27: FaceMarks.replace(skin(),working,true);if(FaceMarks.lastSaveOk){editing=false;dirty=false;status="Face saved";}else status="Could not save face";break;
            case 28: cancel();break;
            default: if(b.id>=20&&b.id<=24)tool=b.id-20;
        }
        FaceConfig.save();rebuild();
    }
    private void begin(){
        original=FaceMarks.marks(skin());original=original==null?null:original.clone();
        int[] seed=FaceMarks.auto(skin());
        working=original!=null?original.clone():seed==null?new int[64]:seed.clone();
        undo.clear();dirty=false;editing=true;
    }
    private void updateEdits(){dirty=true;FaceMarks.replace(skin(),working,false);}
    private void cancel(){FaceMarks.replace(skin(),original,false);editing=false;dirty=false;}
    protected void drawPanel(int mx,int my,float partial){
        drawDefaultBackground();drawRect(0,0,width,height,0xAA202020);
        drawString(fontRendererObj,"Face Rig",12,14,0xFFFFFF);
        if(!editing&&height<278)drawString(fontRendererObj,"Scroll for more settings",12,30,0xAAAAAA);
        int px=leftWidth+24,pw=width-px-12;
        drawCenteredString(fontRendererObj,"Preview",px+pw/2,44,0xFFFFFF);
        FaceDoll.preview=true;FaceDoll.hat=true;
        try{
            FaceDoll.head(px+pw/2,(height+45)/2,Math.max(24,Math.min(pw-18,height-125)),mc.thePlayer);
        }finally{FaceDoll.preview=false;GlStateManager.color(1,1,1,1);}
        if(editing){
            drawString(fontRendererObj,dirty?"Unsaved edits":"Select a tool, then paint",12,34,0xCCCCCC);
            int[] face=SkinFace.facePixels(skin()),hat=SkinFace.hatPixels(skin());
            int[] shown=face.length==64?face:hat;
            int[] colors={0,0xFF66CCFF,0xFFFFCC66,0xFFEE99CC,0xFFFFB6C1};
            if(shown.length==64)for(int r=0;r<8;r++)for(int c=0;c<8;c++){
                int x=gx+c*cell,y=gy+r*cell,rgb=shown[r*8+c];
                drawRect(x,y,x+cell-1,y+cell-1,0xFF000000|rgb);
                if(hat.length==64&&(hat[r*8+c]>>>24)>=128)drawRect(x,y,x+3,y+3,0x88000000);
                int mark=working[r*8+c];
                if(mark>0){drawRect(x,y,x+cell-1,y+1,colors[mark]);drawRect(x,y,x+1,y+cell-1,colors[mark]);}
                if(mx>=x&&mx<x+cell&&my>=y&&my<y+cell)drawRect(x,y,x+cell-1,y+cell-1,0x66FFFFFF);
            }
        }
        if(!status.isEmpty())drawCenteredString(fontRendererObj,status,px+pw/2,height-52,0xCCCCCC);
        super.drawPanel(mx,my,partial);
    }
    private boolean paint(int x,int y,int button){if(!editing||button>1||x<gx||y<gy||x>=gx+cell*8||y>=gy+cell*8)return false;int index=(y-gy)/cell*8+(x-gx)/cell,value=button==1?0:tool;if(working[index]!=value){undo.push(working.clone());while(undo.size()>64)undo.removeLast();working[index]=value;updateEdits();}return true;}
    protected void panelMouseClicked(int x,int y,int button)throws IOException{if(!paint(x,y,button))super.panelMouseClicked(x,y,button);}
    protected void panelMouseMove(int x,int y,int button,long elapsed){paint(x,y,button);}
    protected void keyTyped(char typed,int key)throws IOException{if(key==1){if(editing){cancel();rebuild();}else mc.displayGuiScreen(parent);return;}super.keyTyped(typed,key);}
    public void onGuiClosed(){if(editing&&mc.thePlayer!=null)FaceMarks.replace(skin(),original,false);FaceDoll.preview=false;}
}
