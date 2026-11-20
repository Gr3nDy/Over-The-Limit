package otl.client;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamBarHeadMenuTest {
    @Test public void menuListsProfileTeleportFriendAndMessage(){
        TeamBar.HeadMenu menu=new TeamBar.HeadMenu("Player_1",120,40,16,120,427,240);
        assertEquals(43,menu.y);
        assertEquals(4,TeamBar.HeadMenu.LABELS.length);
        int top=menu.y+TeamBar.HeadMenu.TITLE;
        assertEquals(72,TeamBar.HeadMenu.HEIGHT-TeamBar.HeadMenu.TITLE);
        // The title row names the player and is not an action.
        assertEquals(-1,menu.actionAt(menu.x+1,menu.y+2));
        assertEquals(0,menu.actionAt(menu.x+1,top));
        assertEquals(0,menu.actionAt(menu.x+1,top+17));
        assertEquals(1,menu.actionAt(menu.x+1,top+18));
        assertEquals(2,menu.actionAt(menu.x+1,top+36));
        assertEquals(3,menu.actionAt(menu.x+1,top+54));
        assertEquals(3,menu.actionAt(menu.x+1,top+71));
        assertEquals(-1,menu.actionAt(menu.x-1,top));
        assertEquals(-1,menu.actionAt(menu.x+menu.width,top));
        assertEquals(-1,menu.actionAt(menu.x,menu.y+TeamBar.HeadMenu.HEIGHT));
    }

    @Test public void menuStaysOnScreenAtEitherEdge(){
        for(int x:new int[]{0,426})for(int y:new int[]{0,239}){
            TeamBar.HeadMenu menu=new TeamBar.HeadMenu("Player_1",x,y,16,120,427,240);
            assertTrue(menu.x>=4);assertTrue(menu.x+menu.width<=423);
            assertTrue(menu.y>=4);assertTrue(menu.y+TeamBar.HeadMenu.HEIGHT<=236);
        }
    }

    @Test public void anchorFollowsTheActualHeadOnEitherSideAndOnLowerRows(){
        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Arrays.asList("Alex","Sam")),
            Arrays.asList(Collections.singletonList("Kai"),Collections.singletonList("Pat")),16,20,null,"");
        for(String name:Arrays.asList("Alex","Sam","Kai","Pat")){
            int[] head=TeamBar.headBounds(bar,name);
            assertNotNull(head);assertEquals(name,TeamBar.headAt(bar,head[0]+1,head[1]+1));
        }
        assertNull(TeamBar.headBounds(bar,"Gone"));
        assertEquals(bar.band+TeamBar.ROW_GAP,TeamBar.headBounds(bar,"Pat")[1]);
    }

    @Test public void actionsUseOnlyValidAccountNames(){
        assertEquals("https://oc.tc/Player_1",TeamBar.profileUrl("Player_1"));
        assertEquals("/tp Player_1",TeamBar.teleportCommand("Player_1"));
        assertEquals("/friend add Player_1",TeamBar.friendCommand("Player_1"));
        for(String name:new String[]{null,"","../admin","name/extra","name\n/kill","[MOD] Player", "NameLongerThanSixteen"}){
            assertNull(TeamBar.profileUrl(name));assertNull(TeamBar.teleportCommand(name));assertNull(TeamBar.friendCommand(name));
        }
    }
}
