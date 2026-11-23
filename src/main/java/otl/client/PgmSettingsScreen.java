package otl.client;

import net.minecraft.client.gui.*;

/**
 * Settings reached from the title screen and the module browser.
 *
 * SERVER sends the same /toggle PGM expects, so the server stays the source of truth and its chat
 * reply confirms the change; the highlighted value is only what OTL last sent. CLIENT covers the
 * OTL features that hook into PGM, which read the same module flags as the module browser.
 */
final class PgmSettingsScreen extends SettingsScreen {
    private static final String[] CATS={"CLIENT","SERVER"};
    private static final Row[] ROWS={
        new Row("chat","Chat","team","global","admin"),
        new Row("death","Death messages","all","own","friends"),
        new Row("picker","Picker","auto","on","off"),
        new Row("join","Join messages","all","friends","none"),
        new Row("message","Direct messages","all","friends","none"),
        new Row("observers","Observers","all","friends","none"),
        new Row("sounds","Sounds","all","messages","none"),
        new Row("vote","Vote book","on","off"),
        new Row("stats","Stats","on","off"),
        new Row("effects","Effects","on","off"),
        new Row("time","Time","auto","dark","light")
    };
    /** One PGM setting: its /toggle name, its label, and the value names PGM accepts, in PGM's order. */
    private static final class Row{
        final String key,label;final String[] values;
        Row(String key,String label,String...values){this.key=key;this.label=label;this.values=values;}
    }
    /** Client-side extras. Only one so far: the player list's middle-click menu. */
    private static final int CLIENT_ID=30,NOTICE_ID=31;

    private final GuiScreen parent;
    private String status="";
    /** SERVER first: it is the page PGM itself exposes. */
    private int page=1;

    PgmSettingsScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "SETTINGS";}

    protected void buildPanel(){
        if(page==1)buildServer();else buildClient();
        back(0);
    }

    /** The CLIENT tab; also the entry point for anything linking straight to the client options. */
    void showClient(){page=0;initGui();}

    private void buildServer(){
        boolean live=ServerScope.active();
        panel.note(live?"Pick a value to send /toggle for it.":"Join a PGM server to change these.");
        for(int i=0;i<ROWS.length;i++){
            Row r=ROWS[i];
            ChoiceButton b=new ChoiceButton(this,10+i,panel.left(),panel.choice(),panel.width()-8,
                r.label,r.values,last(r.key),index->apply(r,index));
            b.enabled=live;
            buttonList.add(b);
        }
    }

    private void buildClient(){
        buttonList.add(new NeutralButton(CLIENT_ID,panel.left(),panel.row(),panel.width(),20,
            "Player list middle-click: "+(UtilitySettings.tabPlayerMenu?"ON":"OFF"),false));
        panel.note("Middle-click while the player list is open, then click a name.");
        buttonList.add(new NeutralButton(NOTICE_ID,panel.left(),panel.row(),panel.width(),20,
            "Update notice on join: "+(UtilitySettings.updateNotice?"ON":"OFF"),false));
        panel.note("Every module's own options live under MODS.");
    }

    /** Index of the last value sent, or -1 while nothing is known, so nothing is highlighted. */
    private static int last(String key){
        Integer value=UtilitySettings.pgmSettings.get(key);
        return value==null?-1:value;
    }
    private void apply(Row r,int index){
        UtilitySettings.pgmSettings.put(r.key,index);
        status="Sent /toggle "+r.key+" "+r.values[index];
        if(ServerScope.active()&&mc.thePlayer!=null)mc.thePlayer.sendChatMessage("/toggle "+r.key+" "+r.values[index]);
    }

    private int catY(){return panelY()+(menu==null?HEADER+1:52);}
    private int catW(int i){return fontRendererObj.getStringWidth(CATS[i])+16;}
    private int catX(int i){return panelX()+PAD+(i==0?0:catW(0)+6);}

    protected void drawFixed(int mx,int my,float partial){
        for(int i=0;i<CATS.length;i++){
            int tx=catX(i),tw=catW(i);
            BedrockUi.chip(fontRendererObj,tx,catY(),tw,15,CATS[i],i==page,BedrockUi.hit(mx,my,tx,catY(),tw,15));
        }
        if(!status.isEmpty()){
            int left=catX(CATS.length-1)+catW(CATS.length-1)+8;
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(status,panelX()+panelW()-PAD-left),
                left,catY()+4,BedrockUi.DIM);
        }
    }

    protected void mouseClicked(int mx,int my,int button)throws java.io.IOException{
        if(button==0)for(int i=0;i<CATS.length;i++){
            int tx=catX(i),tw=catW(i);
            if(BedrockUi.hit(mx,my,tx,catY(),tw,15)){
                if(page!=i){page=i;status="";initGui();}
                return;
            }
        }
        super.mouseClicked(mx,my,button);
    }

    protected void actionPerformed(GuiButton b){
        if(b.id==0){
            if(parent!=null)mc.displayGuiScreen(parent);
            else mc.displayGuiScreen(new GuiMainMenu());
            return;
        }
        if(b.id==CLIENT_ID){
            UtilitySettings.tabPlayerMenu=!UtilitySettings.tabPlayerMenu;
            UtilitySettings.save();initGui();
        }
        if(b.id==NOTICE_ID){
            UtilitySettings.updateNotice=!UtilitySettings.updateNotice;
            UtilitySettings.save();initGui();
        }
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
