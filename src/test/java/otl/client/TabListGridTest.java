package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

/** The clickable player list keeps vanilla's grid, so every player stays on screen without scrolling. */
public class TabListGridTest {
    @Test public void gridFitsEveryListWithinTwentyRows(){
        for(int players=0;players<=200;players++){
            int[] grid=TabListMenu.grid(players);
            assertTrue("Too many rows for "+players+": "+grid[1],grid[1]<=20);
            assertTrue("Less than one column",grid[0]>=1);
            if(players==0){
                assertEquals(0,grid[1]);
                continue;
            }
            assertTrue("Rows lost players: "+grid[0]+"x"+grid[1]+" for "+players,grid[0]*grid[1]>=players);
            // A trailing column that holds nobody would widen the panel for nothing.
            assertTrue("An empty column was counted: "+players,(grid[0]-1)*grid[1]<players);
        }
    }

    @Test public void smallListsStayInOneColumn(){
        int[] one=TabListMenu.grid(1),twenty=TabListMenu.grid(20),many=TabListMenu.grid(21);
        assertEquals(1,one[0]);
        assertEquals(1,twenty[0]);
        assertEquals(20,twenty[1]);
        assertEquals("The twenty-first player did not start a second column",2,many[0]);
    }

    /**
     * PGM draws its tab list from fake entries whose display text is the real name, and pads it with
     * rows that only carry colour codes or a score. The drawn text is what a click must resolve.
     */
    @Test public void theDrawnTextDecidesWhichRowsArePlayers(){
        // A PGM player row: colour codes around the account name.
        assertEquals("Player62",TabListMenu.target("\u00a7r\u00a7b\u00a7lPlayer62\u00a7r",null));
        // Padding rows: blank, a team score, or a label. None of them is a player.
        assertNull(TabListMenu.target("\u00a7r",null));
        assertNull(TabListMenu.target("\u00a7r\u00a7f0\u00a7r\u00a78/\u00a7r\u00a775\u00a7r \u00a7r\u00a74\u00a7lRed\u00a7r",null));
        // The profile name is the fallback only when nothing is drawn.
        assertEquals("Notch",TabListMenu.target(null,"Notch"));
        assertNull(TabListMenu.target(null,"|95-80f34791e431"));
    }

    /** A rank or guild tag around a name must not hide the player behind it. */
    @Test public void ranksAndTagsDoNotHideThePlayer(){
        assertEquals("Notch",TabListMenu.target("\u00a7c[MOD] \u00a7fNotch",null));
        assertEquals("Notch",TabListMenu.target("\u00a77[VIP] \u00a7bNotch \u00a78\u00a7l1","Notch"));
        assertEquals("Notch",TabListMenu.target("\u00a7fNotch \u00a76\u2726","Notch"));
        assertEquals("Notch",TabListMenu.target("(Admin) Notch",null));
        // A tag alone is not a player, and neither is a score row that ends in a colour name.
        assertNull(TabListMenu.target("\u00a7c[MOD]",null));
        assertNull(TabListMenu.target("0/75 Red",null));
        // A word after the name is only trusted when it is a name this client actually knows.
        java.util.Set<String> known=new java.util.HashSet<>(java.util.Collections.singletonList("Notch"));
        assertEquals("Notch",TabListMenu.target("\u00a7fNotch \u00a78Legend",null,known));
        assertNull(TabListMenu.target("\u00a7fOther \u00a78Legend",null,known));
    }
}
