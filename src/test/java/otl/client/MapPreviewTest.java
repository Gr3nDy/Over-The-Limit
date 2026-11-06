package otl.client;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapPreviewTest {
    @Test public void cardFadeNeverWrapsBackToTransparent(){
        int previous=0;
        for(int row=0;row<110;row++){
            int color=MapInfoScreen.bannerShade(row),alpha=color>>>24;
            assertEquals(0,color&0xFFFFFF);assertTrue(alpha>=previous);previous=alpha;
        }
        assertEquals(255,previous);
    }
    @Test public void hoverPreviewSizesAlignWithTooltipsAndStayOnScreen(){
        MapPreview.PreviewBox small=new MapPreview.PreviewBox(854,480,100,100,false,120,8);
        assertEquals(80,small.side);assertEquals(112,small.x);assertEquals(104,small.y);
        MapPreview.PreviewBox large=new MapPreview.PreviewBox(854,480,100,100,true,120,8);
        assertEquals(170,large.side);assertEquals(small.x,large.x);assertEquals(small.y,large.y);
        MapPreview.PreviewBox plain=new MapPreview.PreviewBox(854,480,100,100,false,0,0);
        assertEquals(112,plain.x);assertEquals(88,plain.y);
        MapPreview.PreviewBox bottom=new MapPreview.PreviewBox(854,480,100,470,false,120,8);
        assertEquals(112,bottom.x);assertTrue(bottom.y+bottom.side<458);
        for(int[] size:new int[][]{{320,240},{427,240},{854,480},{100,80}})for(boolean big:new boolean[]{false,true}){
            for(int mx:new int[]{0,size[0]/2,size[0]-1})for(int my:new int[]{0,size[1]/2,size[1]-1}){
                MapPreview.PreviewBox box=new MapPreview.PreviewBox(size[0],size[1],mx,my,big,120,8);
                assertTrue(box.x>=4&&box.y>=4);assertTrue(box.x+box.side+4<=size[0]);assertTrue(box.y+box.side+4<=size[1]);
            }
        }
    }
    @Test public void onlyGoldNamedMapLinksGetChatPreviews(){
        net.minecraft.util.ChatComponentText map=new net.minecraft.util.ChatComponentText("100\u2508Rooms");
        net.minecraft.util.ChatStyle style=new net.minecraft.util.ChatStyle().setColor(net.minecraft.util.EnumChatFormatting.GOLD);
        map.setChatStyle(style);
        assertNull(MapPreview.chatQuery(map));
        style.setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND,"/map 100\u2508Rooms"));
        assertEquals("100\u2508Rooms",MapPreview.chatQuery(map));
        net.minecraft.util.ChatComponentText child=new net.minecraft.util.ChatComponentText("Rooms");map.appendSibling(child);
        assertEquals("100\u2508Rooms",MapPreview.chatQuery(child));
        style.setColor(net.minecraft.util.EnumChatFormatting.WHITE);assertNull(MapPreview.chatQuery(map));
        style.setColor(net.minecraft.util.EnumChatFormatting.GOLD);
        for(String command:new String[]{"/map","/vote 1","/maps Allure"}){
            style.setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND,command));
            assertNull(MapPreview.chatQuery(map));
        }
        style.setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND,"/map Promenade"));
        assertEquals("Promenade",MapPreview.chatQuery(map));
        assertNull(MapPreview.chatQuery(null));
    }
    @Test public void namedAndCurrentMapCommandsAreRecognized(){
        assertEquals("Allure",MapPreview.commandQuery(" /MAP   Allure "));
        assertEquals("Haven Tide",MapPreview.commandQuery("/map Haven Tide"));
        assertEquals("100\u2508Rooms",MapPreview.commandQuery("/map 100\u2508Rooms"));
        assertEquals("100rooms",MapInfo.norm(MapPreview.commandQuery("/map 100\u2508Rooms")));
        assertEquals("",MapPreview.commandQuery("/map"));assertEquals("",MapPreview.commandQuery("/map "));
        for(String text:new String[]{"/maple Allure","hello /map Allure","/maps Allure"})assertNull(MapPreview.commandQuery(text));
        assertNull(MapPreview.commandQuery(null));
    }
    @Test public void bookNamesKeepHitCoordinatesAcrossServerSeparatorsAndFormatting(){
        String text="1. \u00a7b100\u2508Rooms\u00a7r (vote)";
        int[] range=MapPreview.nameRange(text,"100_rooms");
        assertNotNull(range);assertEquals("100\u2508Rooms",text.substring(range[0],range[1]));
        assertNotNull(MapPreview.nameRange("Infection: Cataclysm","Infection Cataclysm"));
        assertNull(MapPreview.nameRange("NotAllure","Allure"));
        assertNull(MapPreview.nameRange("Allured","Allure"));
        assertNull(MapPreview.nameRange("Vote","100 Rooms"));
    }
    @Test public void serverResponsesKeepTheExactRepository(){
        assertEquals("PublicMaps/main/fun/blitz/100_rooms",MapPreview.serverQuery("XML: OvercastCommunity/PublicMaps /fun/blitz/100_rooms [View]"));
        assertEquals("CommunityMaps/master/dtcm/haven_tide",MapPreview.serverQuery("XML: OvercastCommunity/CommunityMaps /dtcm/haven_tide [View]"));
        assertEquals("Xyth KotF",MapPreview.serverQuery("Playing Xyth KotF by Unknown"));
        assertEquals("Home by the Sea",MapPreview.serverQuery("Playing Home by the Sea by Alice"));
        assertNull(MapPreview.serverQuery("Alex: XML: OvercastCommunity/PublicMaps /foo"));
    }
    @Test public void galleryIncludesBothRepositoriesWithoutMergingTheirIdentities(){
        java.util.List<String> before=MapRemote.catalog;
        try{
            MapRemote.catalog=java.util.Arrays.asList("PublicMaps/main/ctw/shared","CommunityMaps/master/ctw/shared","CommunityMaps/master/ctw/allure");
            java.util.List<String> paths=new MapGalleryScreen(null).paths();
            assertTrue(paths.contains("PublicMaps/main/ctw/shared/map.png"));
            assertTrue(paths.contains("CommunityMaps/master/ctw/shared/map.png"));
            assertEquals(1,java.util.Collections.frequency(paths,"CommunityMaps/master/ctw/allure/map.png"));
        }finally{MapRemote.catalog=before;}
    }
    @Test public void everyGalleryCellFitsAndUsesTheSameClickCoordinates(){
        for(int[] size:new int[][]{{320,240},{427,240},{640,360},{854,480},{1920,1080}}){
            MapGalleryScreen.Grid grid=new MapGalleryScreen.Grid(size[0],size[1]);
            Set<Integer> indices=new HashSet<>();
            for(int i=0;i<grid.perPage();i++){
                int x=grid.x(i),y=grid.y(i);
                assertTrue(x>=0);assertTrue(x+grid.cellWidth<=size[0]);
                assertTrue(y>=48);assertTrue(y+grid.cellHeight<=size[1]-48);
                assertEquals(i,grid.indexAt(x+grid.cellWidth/2,y+grid.cellHeight/2));
                indices.add(grid.indexAt(x+grid.cellWidth/2,y+grid.cellHeight/2));
            }
            assertEquals(grid.perPage(),indices.size());
            assertEquals(-1,grid.indexAt(grid.left-1,48));
            assertEquals(-1,grid.indexAt(grid.left,size[1]-28));
        }
        assertEquals(12,new MapGalleryScreen.Grid(854,480).perPage());
    }

    @Test public void thumbnailAndExactBundledPathReferToTheSameMap(){
        MapInfo info=MapInfo.findLocal("ctw/allure");
        assertNotNull(info);assertEquals("ctw/allure",info.dir);assertEquals("Allure",info.name);
        assertEquals(24,info.players);assertTrue(info.objective.contains("wool"));
    }

    @Test public void squarePreviewKeepsAllSourcePixelsWithoutStretching(){
        BufferedImage source=new BufferedImage(4,2,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0xffff0000);source.setRGB(3,1,0xff00ff00);
        BufferedImage result=MapPreview.square(source);
        assertEquals(4,result.getWidth());assertEquals(4,result.getHeight());
        assertEquals(0xffff0000,result.getRGB(0,1));assertEquals(0xff00ff00,result.getRGB(3,2));
        assertEquals(0,result.getRGB(0,0));assertEquals(0,result.getRGB(3,3));
        assertSame(result,MapPreview.square(result));
    }

    @Test public void squareCardImageFitsBesideMetadataAndAboveFooter(){
        for(int[] size:new int[][]{{320,240},{427,240},{640,360},{854,480}}){
            int side=MapInfoScreen.imageSide(size[0],size[1]);
            assertTrue(side>0);assertTrue(side+88<=size[1]);
            assertTrue(side*2+40<=Math.min(620,size[0]-20));
        }
    }
}
