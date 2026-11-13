package otl.client;

import static org.junit.Assert.*;

import org.junit.Test;

/** Region extraction from map.xml shape elements. */
public class MapRegionsTest {
    @Test public void parsesShapesToBoundingBoxes(){
        String xml="<map><regions>"
            +"<cuboid id='a' min='1,2,3' max='4,5,6'/>"
            +"<union><rectangle min='-4,-4' max='5,5'/></union>"
            +"<cylinder base='0,10,0' height='4' radius='2'/>"
            +"<point id='p'>1.5,2.5,3.5</point>"
            +"<apply region='a' enter='never'/>"
            +"</regions></map>";
        MapInfo m=MapInfo.parse(xml,"other/test",true,null,id->null);
        assertEquals(4,m.regions.size());
        MapInfo.Region a=m.regions.get(0);
        assertEquals("a",a.name);
        assertArray(a.min,new double[]{1,2,3});
        assertArray(a.max,new double[]{5,6,7}); // cuboid max block is inclusive
    }

    private static void assertArray(double[] actual,double[] expected){
        assertArrayEquals(expected,actual,0.001);
    }

    /** 2D shapes have no y level: they span the map's build limit instead of the whole world. */
    @Test public void flatShapesUseBuildHeight(){
        String xml="<map><maxbuildheight>40</maxbuildheight><regions>"
            +"<rectangle id='r' min='-4,-4' max='5,5'/>"
            +"<circle id='c' center='0,0' radius='3'/>"
            +"</regions></map>";
        MapInfo m=MapInfo.parse(xml,"other/test",true,null,id->null);
        assertEquals(2,m.regions.size());
        assertEquals(41.0,m.regions.get(0).max[1],0.001);
        assertEquals(41.0,m.regions.get(1).max[1],0.001);
        assertEquals(0.0,m.regions.get(0).min[1],0.001);
    }

    /** The HUD names the innermost region containing the player, and nothing outside all regions. */
    @Test public void hudPicksInnermostRegion(){
        String xml="<map><regions>"
            +"<cuboid id='whole' min='0,0,0' max='99,99,99'/>"
            +"<cuboid id='spawn' min='10,10,10' max='20,20,20'/>"
            +"</regions></map>";
        MapInfo m=MapInfo.parse(xml,"other/test",true,null,id->null);
        assertEquals("spawn",Regions.regionAt(m,15,15,15).name);
        assertEquals("whole",Regions.regionAt(m,50,50,50).name);
        assertNull(Regions.regionAt(m,200,200,200));
    }

    /** The region text is a movable HUD element, defaulting to the top centre. */
    @Test public void regionTextIsMovableHudElement(){
        assertTrue(HudLayout.valid(HudLayout.REGION));
        HudLayout.Position p=HudLayout.position(HudLayout.REGION);
        assertEquals(0.5,p.x,0.001);
        assertEquals(0.0,p.y,0.001);
    }

    @Test public void hudLabelHonoursPrefix(){
        boolean keep=UtilitySettings.regionHudPrefix;
        try{
            UtilitySettings.regionHudPrefix=true;
            assertEquals("Region: Spawn",Regions.hudLabel("Spawn"));
            UtilitySettings.regionHudPrefix=false;
            assertEquals("Spawn",Regions.hudLabel("Spawn"));
        }finally{UtilitySettings.regionHudPrefix=keep;}
    }
}
