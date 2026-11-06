package otl.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapRemoteTest {
    @Test public void hallowsUsesItsPublicMapImagePath() throws Exception {
        String dir="PublicMaps/main/ctw/hallows";
        assertEquals(dir,MapRemote.findDirectory("Hallows",Arrays.asList("PublicMaps/main/ctw/haunted_hallows",dir)));
        assertEquals("https://raw.githubusercontent.com/OvercastCommunity/PublicMaps/main/ctw/hallows/map.png",MapRemote.rawUrl(dir,"map.png"));
        assertNotNull(MapPreview.nameRange("[4] Hallows by IzJust4Fun and yellobeees","hallows"));
    }
    @Test public void repositoriesUseTheirActualBranchesAndSeparatePaths() throws Exception {
        assertArrayEquals(new String[]{"PublicMaps/main","CommunityMaps/master"},MapRemote.SOURCES);
        assertEquals("https://raw.githubusercontent.com/OvercastCommunity/PublicMaps/main/fun/blitz/100_rooms/map.xml",MapRemote.rawUrl("PublicMaps/main/fun/blitz/100_rooms","map.xml"));
        assertEquals("https://raw.githubusercontent.com/OvercastCommunity/CommunityMaps/master/dtcm/haven_tide/map.png",MapRemote.rawUrl("CommunityMaps/master/dtcm/haven_tide","map.png"));
        assertTrue(MapRemote.rawUrl("CommunityMaps/master/ctw/a map","map.xml").contains("a%20map"));
    }

    @Test public void indexExcludesIncludesAndUnsafePaths(){
        String tree="{\"truncated\":false,\"tree\":[{\"path\":\"ctw/allure/map.xml\"},{\"path\":\"includes/map.xml\"},{\"path\":\"ctw/includes/map.xml\"},{\"path\":\"../escape/map.xml\"},{\"path\":\"ctw/allure/map.png\"}]}";
        assertEquals(Collections.singletonList("CommunityMaps/master/ctw/allure"),MapRemote.parseTree("CommunityMaps/master",tree));
    }

    @Test(expected=IllegalArgumentException.class) public void refusesTruncatedIndex(){MapRemote.parseTree("PublicMaps/main","{\"truncated\":true,\"tree\":[]}");}
    @Test(expected=IllegalArgumentException.class) public void refusesPathTraversal() throws Exception {MapRemote.rawUrl("PublicMaps/main/../escape","map.xml");}

    @Test public void exactMatchInEitherRepositoryBeatsAnEarlierPartial(){
        List<String> dirs=Arrays.asList("PublicMaps/main/ctw/rooms_extra","CommunityMaps/master/ctw/rooms");
        assertEquals(dirs.get(1),MapRemote.findDirectory("Rooms",dirs));
        assertEquals(dirs.get(1),MapRemote.findDirectory(dirs.get(1),dirs));
        assertNull(MapRemote.findDirectory("missing",dirs));
    }

    @Test public void downloadsHaveABoundedSize() throws Exception {
        assertArrayEquals(new byte[]{1,2},MapRemote.readLimited(new ByteArrayInputStream(new byte[]{1,2}),2));
        try{MapRemote.readLimited(new ByteArrayInputStream(new byte[]{1,2,3}),2);fail("Expected size limit");}catch(IOException expected){}
    }
}
