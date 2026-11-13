package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** One settings page and an inline, transactional skin editor. */
public final class FaceScreen extends GuiScreen {
    private final GuiScreen parent;
    private boolean editing,dirty;
    private int tool=1,cell,gx,gy,leftWidth,scroll,contentX,contentW;
    private int[] original,working;
    private final Deque<int[]> undo=new ArrayDeque<>();
    private String status="";
    public FaceScreen(){this(null);}
    public FaceScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    private ResourceLocation skin(){return mc.thePlayer.getLocationSkin();}
    public void initGui(){
        FaceConfig.load();UtilitySettings.load();
        contentW=Math.min(620,width-24);contentX=(width-contentW)/2;
        leftWidth=Math.max(170,(contentW-24)/2);
        if(editing)FaceMarks.replace(skin(),working,false);
        rebuild();
    }
    private void button(int id,int x,int y,int w,String text){buttonList.add(new NeutralButton(id,x,y,w,20,text,id>=20&&id<=24&&tool==id-20));}
    private void row(int id,int y,String text){
        y=66+(id-2-scroll)*38;if(y<66||y+34>height-38)return;
        if(id>=4&&id<=9){
            String title=text.substring(0,text.indexOf(":"));
            int selected=id==4?(FaceConfig.naturalLight?0:1):id==5?FaceConfig.hat:id==6?FaceConfig.blush:id==7?FaceConfig.glow:id==8?FaceConfig.glances:FaceConfig.blinking;
            buttonList.add(new ChoiceButton(this,id,contentX,y,leftWidth,title,id==4?new String[]{"Natural","Fullbright"}:new String[]{"Only me","All","None"},selected,value->{
                if(id==4)FaceConfig.naturalLight=value==0;
                if(id==5)FaceConfig.hat=value;if(id==6)FaceConfig.blush=value;if(id==7)FaceConfig.glow=value;if(id==8)FaceConfig.glances=value;if(id==9)FaceConfig.blinking=value;
                FaceConfig.save();
            }));
        }else button(id,contentX,y,leftWidth,text);
    }
    public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();if(!editing&&wheel!=0){scroll=Math.max(0,Math.min(Math.max(0,9-(height-80)/38),scroll+(wheel>0?-1:1)));rebuild();}}
    private String on(boolean value){return value?"ON":"OFF";}
    private void rebuild(){
        buttonList.clear();
        if(editing){
            cell=Math.max(4,Math.min(18,Math.min((height-142)/8,(leftWidth-12)/8)));gx=contentX+(leftWidth-cell*8)/2;gy=65;
            int[] tools={0,1,2,4};
            int tw=(contentW-12)/4;
            for(int i=0;i<tools.length;i++)button(20+tools[i],contentX+i*(tw+4),35,tw,new String[]{"Erase","Eye","Pupil","Lid"}[i]);
            int bw=(contentW-12)/4;
            button(25,contentX,height-30,bw,"Undo");button(26,contentX+bw+4,height-30,bw,"Auto-detect");
            button(28,contentX+(bw+4)*2,height-30,bw,"Cancel");button(27,contentX+(bw+4)*3,height-30,bw,"Save");
            for(GuiButton b:buttonList)if(b.id==25)b.enabled=!undo.isEmpty();
        }else{
            buttonList.add(new ModuleReset(90,contentX+contentW-19,34,"face_rig"));
            row(2,42,"FaceRig: "+on(UtilitySettings.faceRig));
            row(3,64,"Other Player's FaceRig: "+on(UtilitySettings.otherFaces));
            row(4,92,"World lighting: "+(FaceConfig.naturalLight?"Natural":"Fullbright"));
            row(5,114,"Over the hat layer: "+FaceConfig.label(FaceConfig.hat));
            row(6,142,"Blush: "+FaceConfig.label(FaceConfig.blush));
            row(7,164,"Glowing Eyes: "+FaceConfig.label(FaceConfig.glow));
            row(8,192,"Glances: "+FaceConfig.label(FaceConfig.glances));
            row(9,214,"Blinking: "+FaceConfig.label(FaceConfig.blinking));
            row(10,236,String.format(Locale.ROOT,"Blink interval: %.1f s",FaceConfig.blinkSeconds));
            button(11,contentX,height-30,92,"Edit face");button(12,contentX+96,height-30,92,"Reset");
            for(GuiButton b:buttonList)if(b.id>=4&&b.id<=10)b.enabled=(UtilitySettings.faceRig||UtilitySettings.otherFaces)&&(b.id!=10||FaceConfig.blinking!=2);
        }
        if(!editing)button(0,contentX+contentW-80,height-30,80,"Done");
    }
    protected void actionPerformed(GuiButton b){
        switch(b.id){
            case 90:((ModuleReset)b).reset();rebuild();return;
            case 0: if(editing){cancel();}mc.displayGuiScreen(parent);return;
            case 1: return;
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
            case 26: mc.displayGuiScreen(new GuiYesNo((yes,id)->{if(yes){undo.push(working.clone());int[] auto=FaceMarks.auto(skin());working=auto==null?new int[64]:auto.clone();updateEdits();}mc.displayGuiScreen(this);},"Auto-detect face marks?","Save to keep the result, or Cancel to undo it.",0));return;
            case 27: FaceMarks.replace(skin(),working,true);if(FaceMarks.lastSaveOk){editing=false;dirty=false;status="Face saved";}else status="Could not save face";break;
            case 28: cancel();break;
            default: if(b.id==20||b.id==21||b.id==22||b.id==24)tool=b.id-20;
        }
        FaceConfig.save();rebuild();
    }
    private void begin(){
        original=FaceMarks.marks(skin());original=original==null?null:original.clone();
        int[] seed=FaceMarks.auto(skin());
        working=original!=null?original.clone():seed==null?new int[64]:seed.clone();
        undo.clear();dirty=false;editing=true;status="";
    }
    private void updateEdits(){dirty=true;FaceMarks.replace(skin(),working,false);}
    private void cancel(){FaceMarks.replace(skin(),original,false);editing=false;dirty=false;}
    private void drawPanel(int mx,int my,float partial){
        drawDefaultBackground();
        drawCenteredString(fontRendererObj,editing?"FACE MAPPING":"FACE RIG",width/2,14,0xFFFFFFFF);
        int px=contentX+leftWidth+16,pw=contentW-leftWidth-16;
        drawCenteredString(fontRendererObj,editing?"TOOLS":"SETTINGS",contentX+leftWidth/2,30,0xFFE0E0E0);
        drawCenteredString(fontRendererObj,editing?"LIVE PREVIEW":"PREVIEW",px+pw/2,30,0xFFE0E0E0);
        if(!editing&&(scroll>0||height<278))drawString(fontRendererObj,"Scroll for more settings",contentX,44,0xFF8B8C8D);
        FaceDoll.preview=true;FaceDoll.hat=true;
        try{
            FaceDoll.head(px+pw/2,editing?(height+24)/2:(height+60)/2,Math.max(24,Math.min(pw-18,height-(editing?145:112))),mc.thePlayer);
        }finally{FaceDoll.preview=false;GlStateManager.color(1,1,1,1);}
        if(editing){
            String hint=tool==0?"Erase: remove a mark.":tool==1?"Eye: mark the eye area.":tool==2?"Pupil: mark moving dark pixels.":"Lid: pick the closing skin color.";
            fontRendererObj.drawSplitString(hint,contentX,height-58,contentW,BedrockUi.MUTED);
            drawCenteredString(fontRendererObj,dirty?"Unsaved changes":"No changes",px+pw/2,44,dirty?0xFFFFAAAA:0xFFAAAAAA);
            int[] face=SkinFace.facePixels(skin()),hat=SkinFace.hatPixels(skin());
            int[] shown=face.length==64?face:hat;
            int[] colors={0,0xFF66CCFF,0xFFFFCC66,0xFFEE99CC,0xFFFFB6C1};
            if(shown.length==64)for(int r=0;r<8;r++)for(int c=0;c<8;c++){
                int x=gx+c*cell,y=gy+r*cell,rgb=shown[r*8+c];
                drawRect(x,y,x+cell-1,y+cell-1,0xFF000000|rgb);
                if(hat.length==64&&(hat[r*8+c]>>>24)>=128)drawRect(x,y,x+3,y+3,0x88000000);
                int mark=working[r*8+c];
                if(mark>0&&mark!=3&&mark<colors.length){drawRect(x,y,x+cell-1,y+1,colors[mark]);drawRect(x,y,x+1,y+cell-1,colors[mark]);}
                if(mx>=x&&mx<x+cell&&my>=y&&my<y+cell)drawRect(x,y,x+cell-1,y+cell-1,0x66FFFFFF);
            }
        }
        if(!status.isEmpty())drawCenteredString(fontRendererObj,status,width/2,height-52,0xCCCCCC);
        GlStateManager.color(1,1,1,1);
        GlStateManager.disableLighting();
        super.drawScreen(mx,my,partial);
        if(editing){int tw=(contentW-12)/4;int[] kinds={0,1,2,4};int[] colors={0xFFCCCCCC,0xFF66CCFF,0xFFFFCC66,0,0xFFFFB6C1};for(int i=0;i<4;i++)drawRect(contentX+i*(tw+4)+3,39,contentX+i*(tw+4)+6,51,colors[kinds[i]]);}
    }
    public void drawScreen(int mx,int my,float partial){
        drawPanel(mx,my,partial);
    }
    private boolean paint(int x,int y,int button){if(!editing||button>1||x<gx||y<gy||x>=gx+cell*8||y>=gy+cell*8)return false;int index=(y-gy)/cell*8+(x-gx)/cell,value=button==1?0:tool;if(working[index]!=value){undo.push(working.clone());while(undo.size()>64)undo.removeLast();working[index]=value;updateEdits();}return true;}
    protected void mouseClicked(int x,int y,int button)throws IOException{if(!paint(x,y,button))super.mouseClicked(x,y,button);}
    /** Smoke/editor compatibility entry point using the compact canvas coordinates. */
    void panelMouseClicked(int x,int y,int button)throws IOException{if(!paint(x,y,button))super.mouseClicked(x,y,button);}
    protected void mouseClickMove(int x,int y,int button,long elapsed){paint(x,y,button);}
    protected void keyTyped(char typed,int key)throws IOException{if(key==1){if(editing){cancel();rebuild();}else mc.displayGuiScreen(parent);return;}super.keyTyped(typed,key);}
    public void onGuiClosed(){if(editing&&mc.thePlayer!=null)FaceMarks.replace(skin(),original,false);FaceDoll.preview=false;}
}
