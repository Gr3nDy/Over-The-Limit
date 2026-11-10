package otl.client;

import java.io.*;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinimapModelTest {
    @Test public void recognizesCurrentMapJoinAndRotationBannersWithoutUsingArbitraryMapLookups(){
        Minimap.Announcement notices=new Minimap.Announcement();long now=1000000000L;
        assertNull(notices.receive("            Allure            ",now));
        assertNull(notices.receive(" \u00a79\u00a7oCapture the enemies' wools!",now));
        assertEquals("Allure",notices.receive(" Created by jcane",now+100000000L));
        assertEquals("Trip to Cancun",notices.receive("Playing Trip to Cancun by jcane",now));
        assertNull(notices.receive("XML: OvercastCommunity/CommunityMaps ctw/allure [View]",now));
        assertNull(notices.receive("<Alex>: Playing Allure by jcane",now));
        assertNull(notices.receive("             Stale Title             ",now));
        assertNull(notices.receive(" Created by Somebody",now+6000000000L));
        notices.clear();assertNull(notices.receive(" Created by Somebody",now));
        assertEquals("Psycho Sumo",notices.receive("                Psycho Sumo               \n Get the highest amount of kills\n Created by Nidho0gg and zzuf",now));
    }
    private byte[] model(int axis,int count)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){
            out.writeInt(0x4F544C4D);out.writeByte(1);out.writeInt(-96);out.writeInt(2);out.writeInt(-62);out.writeInt(count);
            for(int positive:new int[]{1,0}){
                out.writeShort(0);out.writeShort(10);out.writeShort(0);out.writeShort(2);out.writeShort(3);
                out.writeByte(axis);out.writeByte(positive);out.writeInt(0x77AA55);
            }
        }
        return bytes.toByteArray();
    }
    @Test public void readsBothSidesOfEach3dQuadForGpuFaceCulling()throws Exception{
        MinimapModel model=MinimapModel.read(new ByteArrayInputStream(model(1,2)));
        assertEquals(2,model.faces.size());assertEquals(12,model.faces.get(0).xyz.length);
        assertEquals(0xFF77AA55,model.faces.get(0).color);
        assertEquals(-96F,model.faces.get(0).xyz[0],.0001F);
        assertEquals(12F,model.faces.get(0).xyz[1],.0001F);
        assertEquals(-62F,model.faces.get(0).xyz[2],.0001F);
        assertEquals(-94.5,model.center[0],.0001);assertEquals(12,model.center[1],.0001);assertEquals(-61,model.center[2],.0001);
    }
    @Test public void staticFitRadiusContainsEveryVertexAtEveryYaw()throws Exception{
        MinimapModel model=MinimapModel.read(new ByteArrayInputStream(model(1,2)));
        for(double angle:new double[]{0,17,45,90,123,180,270})for(MinimapModel.Face face:model.faces)for(int i=0;i<4;i++){
            double dx=face.xyz[i*3]-model.center[0],dy=face.xyz[i*3+1]-model.center[1],dz=face.xyz[i*3+2]-model.center[2];
            double distance=Math.hypot(MinimapModel.screenX(dx,dz,angle),MinimapModel.screenY(dx,dy,dz,angle));
            assertTrue(distance<=model.screenRadius+.000001);
        }
    }
    @Test public void cutawayFadesSmoothlyToTheMapBackground(){
        assertEquals(20,MinimapRenderer.cutawayRadius(4,280),.000001);
        assertEquals(72,MinimapRenderer.cutawayRadius(2,1200),.000001);
        double radius=MinimapRenderer.cutawayRadius(4,280);
        assertEquals(MinimapRenderer.CUTAWAY_STRENGTH,MinimapRenderer.cutawayFade(0,radius),.000001);
        assertEquals(MinimapRenderer.CUTAWAY_STRENGTH,MinimapRenderer.cutawayFade(radius*MinimapRenderer.CUTAWAY_CORE,radius),.000001);
        assertEquals(0,MinimapRenderer.cutawayFade(radius,radius),.000001);
        assertTrue(MinimapRenderer.cutawayFade(radius*.9,radius)>MinimapRenderer.cutawayFade(radius*.95,radius));
        assertEquals(0,MinimapRenderer.cutawayFade(0,0),.000001);
    }
    @Test public void enlargedViewCyclesBackToTheSmallMap(){
        assertEquals(1,Minimap.nextView(0));assertEquals(2,Minimap.nextView(1));assertEquals(0,Minimap.nextView(2));
    }
    @Test(expected=IOException.class) public void rejectsInvalidAxis()throws Exception{MinimapModel.read(new ByteArrayInputStream(model(3,2)));}
    @Test(expected=IOException.class) public void rejectsExcessiveCountBeforeAllocating()throws Exception{MinimapModel.read(new ByteArrayInputStream(model(1,2000001)));}
    @Test(expected=IOException.class) public void rejectsTruncatedGeometry()throws Exception{MinimapModel.read(new ByteArrayInputStream(model(1,3)));}
    @Test public void viewportIsSquareWithoutChangingTerrainScale(){
        assertEquals(140,Minimap.side(427,240,100));assertEquals(210,Minimap.side(427,240,150));
        assertEquals(92,Minimap.side(427,100,150));assertEquals(1,Minimap.side(1,1,100));
        double before=MinimapModel.screenX(10,10)-MinimapModel.screenX(2,3);
        double after=MinimapModel.screenX(10,10)-MinimapModel.screenX(3,3);
        assertEquals(-.7071067811865476,after-before,.000001);
        assertEquals(2,MinimapModel.PIXELS_PER_BLOCK,0);assertTrue(HudLayout.valid(HudLayout.MINIMAP));
    }
    @Test public void generatedAllureModelLoadsWithItsWorldCoordinates()throws Exception{
        InputStream source=getClass().getResourceAsStream("/assets/otl/maps/ctw/allure/minimap.mesh.gz");
        assertNotNull("Generate models before testing the bundled minimap",source);
        MinimapModel model=MinimapModel.read(source);
        assertTrue(model.faces.size()>1000);assertTrue(model.faces.size()<50000);
    }
    @Test public void namedWorldVariantsMatchTheirTerrainAndAmbiguousWorldsFailClosed()throws Exception{
        assertEquals("arcade/standard/the_grand_dueling_tournament_ii/modern/minimap.mesh.gz",Minimap.INSTANCE.asset("The Grand Dueling Tournament III"));
        assertEquals("ctf/shifty_sands/halloween/minimap.mesh.gz",Minimap.INSTANCE.asset("Tricky Sands"));
        assertEquals("PublicMaps/main/dtcm/grassy_mound/ctf/minimap.mesh.gz",Minimap.INSTANCE.asset("Grassy Mound CTF"));
        assertNull(Minimap.INSTANCE.asset("Runic Altar"));
        assertEquals("ctf/runic_altar/modern/minimap.mesh.gz",Minimap.INSTANCE.asset("CommunityMaps/master/ctf/runic_altar/modern"));
    }
    @Test public void minimapSettingsClampAndModuleHasItsOwnToggle()throws Exception{
        boolean enabled=UtilitySettings.minimap;int size=UtilitySettings.minimapSize,opacity=UtilitySettings.minimapOpacity,large=UtilitySettings.minimapLargeOpacity;
        try{
            com.google.gson.JsonObject settings=new com.google.gson.JsonObject();settings.addProperty("minimap",true);settings.addProperty("minimap_size",999);settings.addProperty("minimap_opacity",-3);settings.addProperty("minimap_large_opacity",999);
            java.lang.reflect.Method read=UtilitySettings.class.getDeclaredMethod("read",com.google.gson.JsonObject.class);read.setAccessible(true);read.invoke(null,settings);
            assertTrue(UtilitySettings.minimap);assertEquals(150,UtilitySettings.minimapSize);assertEquals(0,UtilitySettings.minimapOpacity);assertEquals(100,UtilitySettings.minimapLargeOpacity);
            OtlModule module=OtlModule.ALL.stream().filter(m->m.id.equals("minimap")).findFirst().get();
            assertTrue(module.settings(null) instanceof MinimapSettings);module.setEnabled(false);assertFalse(module.enabled());
        }finally{UtilitySettings.minimap=enabled;UtilitySettings.minimapSize=size;UtilitySettings.minimapOpacity=opacity;UtilitySettings.minimapLargeOpacity=large;}
    }
}
