package otl.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObjectivesSymbolicScoreTest {
    @Test public void symbolicScoresFollowLiveArrowTeamColoursInBothLayouts(){
        String blue="\u00ba9Pats",red="\u00bacBucs";
        List<String> teams=Arrays.asList(blue,red);
        Map<Character,String> colours=new HashMap<>();colours.put('9',blue);colours.put('c',red);
        for(String symbol:new String[]{"\u2b24","\u2b1b","\u2764","|"}){
            for(boolean reversed:new boolean[]{false,true})for(boolean compact:new boolean[]{false,true}){
                Map<String,Integer> columns=new HashMap<>();
                columns.put(MapInfo.norm(blue),reversed?1:0);columns.put(MapInfo.norm(red),reversed?0:1);
                List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
                    "\u00a7c\u2794 "+symbol+symbol+"\u00a77"+symbol+symbol+symbol,
                    "\u00a79\u2794 \u00a77"+symbol+symbol+symbol+symbol+symbol),teams,null,colours);
                assertEquals(2,entries.size());
                assertEquals(red,entries.get(0).team);assertEquals(blue,entries.get(1).team);
                List<List<List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                for(int side=0;side<2;side++){
                    boolean blueSide=side==columns.get(MapInfo.norm(blue));
                    List<Objectives.Part> row=sides.get(side).get(0);
                    Objectives.Part score=row.get(compact||side==1?0:1);
                    assertEquals(blueSide?"0/5":"2/5",ChatRules.plain(score.text));
                    assertEquals(TeamBar.chatColour(blueSide?'9':'c'),score.colour);
                    assertEquals(blueSide?"Pats":"Bucs",score.name);
                    if(!compact)assertEquals(blueSide?"Pats":"Bucs",row.get(side==0?0:1).text);
                    assertEquals(1,Objectives.compactRows(Collections.singletonList(Collections.singletonList(score))).size());
                }
            }
        }
    }

    @Test public void scoresUpdateFromZeroToWinningPointWithoutInventingALimit(){
        Map<Character,String> colours=Collections.singletonMap('9',"blue");
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        for(int score=0;score<=5;score++){
            StringBuilder row=new StringBuilder("\u00a79\u2794 ");
            for(int i=0;i<5;i++){
                if(i==score)row.append("\u00a77");
                row.append('\u2b24');
            }
            List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row.toString()),
                Collections.singletonList("blue"),null,colours,tracker);
            assertEquals(score+"/5",ChatRules.plain(entries.get(0).parts.get(0).text));
        }
    }

    @Test public void unknownOrMalformedSymbolicRowsAreNotGuessedOntoATeam(){
        Map<Character,String> colours=Collections.singletonMap('9',"blue");
        for(String row:new String[]{"\u00a7c\u2794 \u2b24","\u00a79\u2794 \u2b24\u2764",
            "\u00a79\u2794 \u2b24\u00a7c\u2b24"}){
            assertTrue(Objectives.entries(Collections.singletonList(row),Arrays.asList("blue","red"),null,colours).isEmpty());
        }
    }

    @Test public void timeBasedOrUnspecifiedTargetsShowPointsWithoutAFabricatedWinningScore(){
        Map<Character,String> colours=Collections.singletonMap('9',"blue");
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        for(int points=0;points<=7;points++){
            StringBuilder row=new StringBuilder("\u00a79\u2794 ");
            for(int i=0;i<points;i++)row.append('\u2b24');
            List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row.toString()),
                Collections.singletonList("blue"),null,colours,tracker);
            assertEquals(points+" pts",entries.get(0).parts.get(0).text);
            assertEquals(-1,tracker.limit);
        }
        assertEquals("5 pts",Objectives.entries(Collections.singletonList("\u00a79\u2794 \u2b24\u2b24\u2b24\u2b24\u2b24"),
            Collections.singletonList("blue"),null,colours).get(0).parts.get(0).text);
    }

    @Test public void confirmedTargetsResetBetweenMatchesAndUnknownRowsCannotSetThem(){
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        Map<Character,String> colours=Collections.singletonMap('9',"blue");
        tracker.observe(Collections.singletonList("\u00a79\u2794 \u00a77\u2b24\u2b24\u2b24\u2b24\u2b24"),colours);
        assertEquals(5,tracker.limit);
        tracker.clear();assertEquals("2 pts",tracker.label(2));
        tracker.observe(Collections.singletonList("\u00a7c\u2794 \u00a77\u2b24\u2b24\u2b24"),colours);
        assertEquals(-1,tracker.limit);
    }

    @Test public void scoreDisplayCanBeAHorizontalOrVerticalBarWithTheNumberOnHover(){
        Map<Character,String> colours=Collections.singletonMap('9',"blue");
        List<String> teams=Collections.singletonList("blue");
        String row="\u00a79\u2794 \u2b24\u2b24\u00a77\u2b24\u2b24\u2b24";
        int was=UtilitySettings.teamBarScoreStyle;
        try{
            for(int style:new int[]{0,1,2,3}){
                UtilitySettings.teamBarScoreStyle=style;
                Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
                Objectives.Part part=Objectives.entries(Collections.singletonList(row),teams,null,colours,tracker).get(0).parts.get(0);
                if(style==0||style==3){
                    assertEquals("2/5",ChatRules.plain(part.text));
                    assertEquals(-1F,part.fraction,0F);
                    assertEquals("blue",part.name);
                    assertEquals(style==3,part.stacked);
                }else{
                    assertNull(part.text);
                    assertEquals(.4F,part.fraction,1e-6F);
                    assertEquals("blue 2/5",part.name);
                    assertEquals(style==2?Objectives.BAR_VW:Objectives.BAR_W,part.width(null));
                }
            }
            // No confirmed target: there is nothing to fill, so the number stays as points.
            UtilitySettings.teamBarScoreStyle=1;
            Objectives.Part points=Objectives.entries(Collections.singletonList("\u00a79\u2794 \u2b24\u2b24"),
                teams,null,colours,new Objectives.ScoreTracker()).get(0).parts.get(0);
            assertEquals("2 pts",points.text);
            assertEquals(-1F,points.fraction,0F);
            UtilitySettings.teamBarScoreStyle=3;
            points=Objectives.entries(Collections.singletonList("\u00a79\u2794 \u2b24\u2b24"),
                teams,null,colours,new Objectives.ScoreTracker()).get(0).parts.get(0);
            assertEquals("2 pts",points.text);assertFalse(points.stacked);
        }finally{
            UtilitySettings.teamBarScoreStyle=was;
        }
    }
}
