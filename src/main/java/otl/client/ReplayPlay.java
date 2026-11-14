package otl.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which recorded seconds a replay shows. Pure selection over plain snapshots, so the rules are
 * unit-tested without Minecraft: the play is the last ball flight in the buffer, the span starts
 * a moment before the throw and runs to the touchdown, and the scorer is the named player, or
 * whoever the ball ended up beside when the title did not name anyone the client knows.
 */
final class ReplayPlay {
    /** One recorded tick: entity id -> {x,y,z,yaw,pitch}. */
    static final class Frame{
        final int tick;final Map<Integer,double[]> entities=new HashMap<>();
        Frame(int tick){this.tick=tick;}
    }
    /** A throw or a catch, as PGM announced it in chat. */
    static final class Event{
        final int tick;final boolean thrown;final String player;
        Event(int tick,boolean thrown,String player){this.tick=tick;this.thrown=thrown;this.player=player;}
    }
    /** The chosen play: the ball, its scorer, and the tick span to play. Solo runs have ballId == scorerId. */
    static final class Play{
        final int ballId,scorerId,fromTick,toTick;final String scorerName;final Integer catchTick;final boolean solo;
        Play(int ballId,int scorerId,int fromTick,int toTick,String scorerName,Integer catchTick,boolean solo){
            this.ballId=ballId;this.scorerId=scorerId;this.fromTick=fromTick;this.toTick=toTick;
            this.scorerName=scorerName;this.catchTick=catchTick;this.solo=solo;
        }
    }

    /** How far back the play may reach: eight seconds of recording before the touchdown. */
    private static final int WINDOW=160;
    /** Half a second of run-up before the throw, so the replay starts on the play, not on the throw. */
    private static final int LEAD=20;
    /** Ticks a ball may be missing before its flight counts as over. */
    private static final int GAP=5;

    static Play build(List<Frame> frames,List<Event> events,int touchTick,String scorerName,Map<String,Integer> ids,java.util.Set<Integer> balls){
        if(frames.isEmpty())return null;
        int last=frames.get(frames.size()-1).tick;
        if(touchTick>last)touchTick=last; // the trigger raced ahead of the recorder: play what we have
        // The scorer: the player the end-zone line names, or whoever stood beside the ball when it
        // was caught. A scorer we cannot name still replays, following the ball alone.
        int scorer=scorerName!=null&&ids.containsKey(scorerName)?ids.get(scorerName).intValue():-1;
        // The play is the ball flight that ends latest at or before the touchdown and no older than
        // the window. Only true balls count: a player's own track also runs to the touchdown, and
        // picking it would replay a person standing still instead of the throw.
        int ballId=-1,flightStart=-1,flightEnd=-1;
        java.util.Set<Integer> seen=new java.util.HashSet<>(balls);
        for(Frame f:frames)seen.addAll(f.entities.keySet());
        for(int id:seen){
            if(!balls.contains(id))continue;
            int start=-1,end=-1;
            for(Frame f:frames){
                if(f.entities.containsKey(id)){if(start<0)start=f.tick;end=f.tick;}
                else if(start>=0&&f.tick-end>GAP)break;
            }
            if(start<0||end<=start)continue;
            if(end>flightEnd&&end<=touchTick&&touchTick-end<=WINDOW){ballId=id;flightStart=start;flightEnd=end;}
        }
        if(ballId<0){
            // Solo score: the Football was never thrown inside the window, so the play is the
            // carrier's own run. Without a named scorer there is nothing recorded to replay.
            if(scorer<0)return null;
            int start=-1,end=-1;
            for(Frame f:frames){
                if(f.entities.containsKey(scorer)){if(start<0)start=f.tick;end=f.tick;}
                else if(start>=0&&f.tick-end>GAP)break;
            }
            if(start<0||end<=start)return null;
            ballId=scorer;flightStart=start;flightEnd=end;
        }else if(scorer<0){
            double[] end=null;
            for(Frame f:frames)if(f.entities.containsKey(ballId))end=f.entities.get(ballId);
            if(end!=null)scorer=nearest(frames,touchTick,end,balls);
        }
        // The catch is the pickup the scorer made at or just after the flight ended.
        Integer catchTick=null;
        for(Event e:events){
            if(e.thrown||e.tick<flightEnd||e.tick>flightEnd+60)continue;
            if(scorerName!=null&&!scorerName.equals(e.player))continue;
            if(catchTick==null||e.tick<catchTick)catchTick=e.tick;
        }
        int from=Math.max(firstFrame(frames).tick,Math.min(flightStart,flightEnd)-LEAD);
        return new Play(ballId,scorer,from,touchTick,scorerName,catchTick,ballId==scorer);
    }
    private static Frame firstFrame(List<Frame> frames){return frames.get(0);}
    private static Frame lastFrame(List<Frame> frames){return frames.get(frames.size()-1);}
    /** The player closest to a point on a tick, ignoring the balls. */
    private static int nearest(List<Frame> frames,int tick,double[] point,java.util.Set<Integer> balls){
        Frame best=null;
        for(Frame f:frames)if(f.tick<=tick&&(best==null||f.tick>=best.tick))best=f; // freshest frame at a tick wins
        if(best==null)return -1;
        int id=-1;double dist=Double.MAX_VALUE;
        for(Map.Entry<Integer,double[]> e:best.entities.entrySet()){
            if(balls.contains(e.getKey()))continue;
            double[] p=e.getValue();
            double d=(p[0]-point[0])*(p[0]-point[0])+(p[1]-point[1])*(p[1]-point[1])+(p[2]-point[2])*(p[2]-point[2]);
            if(d<dist){dist=d;id=e.getKey();}
        }
        return id;
    }
    /** Frames between two ticks, interpolated; null when the id is not in the recording. */
    static double[] lerp(List<Frame> frames,int id,double tick){
        Frame before=null,after=null;
        for(Frame f:frames){
            if(!f.entities.containsKey(id))continue;
            if(f.tick<=tick)before=f;else if(after==null)after=f;
        }
        if(before==null){
            // Before the recording starts, hold the first position instead of losing the entity.
            for(Frame f:frames)if(f.entities.containsKey(id))return f.entities.get(id);
            return null;
        }
        if(after==null||after.tick==before.tick)return before.entities.get(id);
        double f=(tick-before.tick)/(double)(after.tick-before.tick);
        double[] a=before.entities.get(id),b=after.entities.get(id),out=new double[a.length];
        for(int i=0;i<a.length;i++){
            double d=b[i]-a[i];
            if(i==3||i==4){ // yaw and pitch take the short way round
                while(d>180)d-=360;while(d<-180)d+=360;
            }
            out[i]=a[i]+d*f;
        }
        return out;
    }
    static List<Frame> frames(int first){List<Frame> out=new ArrayList<>();for(int i=0;i<8;i++)out.add(new Frame(first+i));return out;}
}
