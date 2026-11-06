package otl.client;

import java.util.*;

/**
 * Finds eye rectangles in the 8x8 face texels. An eye is the bounding rectangle of
 * its dark pupil plus the lighter eye-white column beside it, with the pupil column
 * kept separate so the renderer can replay the skin's own pixels there.
 * Brows are darker cells one or two rows above each eye. Pure heuristic, unit-testable.
 */
final class EyeDetector {
    static final int NONE=0,EYE=1,PUPIL=2,BROW=3;
    private static final int WHITE_RISE=6,WHITE_DIFF=60;
    /** A pupil/iris is clearly darker than the rest of its own row and fairly neutral. */
    private static final int ROW_DROP=35,INK_CHROMA=110;
    private EyeDetector(){}

    private static final class Blob {
        int minC=8,maxC=-1,minR=8,maxR=-1,size;
    }

    /**
     * Reads the face plane first; only when that finds nothing does it fall back to the
     * skin as it is drawn (hat layer over face), so eyes designed on the hat still work.
     */
    static int[] detect(int[] face,int[] hat){
        if(face==null||face.length<64)return null;
        int[] marks=detectOn(face);
        if(marks!=null)return marks;
        if(hat==null||hat.length<64)return null;
        int[] shown=new int[64];
        for(int i=0;i<64;i++)shown[i]=((hat[i]>>>24)>=128)?hat[i]:face[i];
        return detectOn(shown);
    }

    private static int[] detectOn(int[] plane){
        int skin=skinTone(plane),skinLum=lum(skin);
        boolean[] ink=new boolean[64];
        int[] rowLum=rowLuminance(plane);
        for(int r=1;r<=6;r++)for(int c=0;c<8;c++){
            int i=r*8+c;
            if((plane[i]>>>24)<64||rowLum[r]<0)continue;
            int p=plane[i];
            // Compare with the row, not a global tone: hair rows stay flat, dark skin stays dark.
            ink[i]=lum(p)<rowLum[r]-ROW_DROP&&chroma(p)<=INK_CHROMA;
        }
        List<Blob> blobs=new ArrayList<>();
        collect(ink,blobs);
        int[] pair=pair(blobs);
        if(pair==null)return null;
        int[] marks=new int[64];
        Blob a=blobs.get(pair[0]),b=blobs.get(pair[1]);
        if(a.minC>b.minC){Blob tmp=a;a=b;b=tmp;}
        paint(marks,plane,skin,skinLum,a);
        paint(marks,plane,skin,skinLum,b);
        brow(marks,ink,a);
        brow(marks,ink,b);
        return hasMark(marks)?marks:null;
    }

    /** Dark connected regions, small enough to be a pupil. */
    private static void collect(boolean[] ink,List<Blob> out){
        boolean[] seen=new boolean[64];
        for(int r=1;r<=6;r++)for(int c=0;c<8;c++){
            int i=r*8+c;
            if(seen[i]||!ink[i])continue;
            Blob b=new Blob();
            Deque<Integer> stack=new ArrayDeque<>();
            stack.push(i);seen[i]=true;
            while(!stack.isEmpty()){
                int cell=stack.pop();
                int cr=cell/8,cc=cell%8;
                b.size++;
                b.minC=Math.min(b.minC,cc);b.maxC=Math.max(b.maxC,cc);
                b.minR=Math.min(b.minR,cr);b.maxR=Math.max(b.maxR,cr);
                int[][] near={{cc-1,cr},{cc+1,cr},{cc,cr-1},{cc,cr+1}};
                for(int[] n:near){
                    if(n[0]<0||n[0]>7||n[1]<1||n[1]>6)continue;
                    int j=n[1]*8+n[0];
                    if(!seen[j]&&ink[j]){seen[j]=true;stack.push(j);}
                }
            }
            if(b.size>=1&&b.size<=8&&b.maxC-b.minC<=3)out.add(b);
        }
    }

    /** Two blobs on the same line, a short nose bridge apart, reading as a pair of eyes. */
    private static int[] pair(List<Blob> blobs){
        int bestA=-1,bestB=-1,best=0;
        for(int a=0;a<blobs.size();a++)for(int b=a+1;b<blobs.size();b++){
            Blob A=blobs.get(a),B=blobs.get(b);
            int left=A.minC<=B.minC?a:b,right=left==a?b:a;
            Blob L=blobs.get(left),R=blobs.get(right);
            int gap=R.minC-L.maxC-1;
            if(gap<1||gap>3)continue;
            int rowL=(L.minR+L.maxR)/2,rowR=(R.minR+R.maxR)/2;
            if(Math.abs(rowL-rowR)>1)continue;
            if(Math.abs((L.maxC-L.minC)-(R.maxC-R.minC))>1)continue;
            int heightL=L.maxR-L.minR,heightR=R.maxR-R.minR;
            if(Math.abs(heightL-heightR)>1)continue;
            int score=6*Math.min(L.size,R.size)+3*(3-gap)-5*Math.abs(rowL-rowR)-4*Math.abs(heightL-heightR)+(Math.min(L.minR,R.minR)>=4?5:0);
            if(score>best){best=score;bestA=left;bestB=right;}
        }
        return bestA<0?null:new int[]{bestA,bestB};
    }

    /** Rectangle = pupil blob plus one sclera column on the outer side; pupil column stays separate. */
    private static void paint(int[] marks,int[] plane,int skin,int skinLum,Blob b){
        int minC=b.minC,maxC=b.maxC;
        boolean left=(minC+maxC)<7;
        int outer=left?minC-1:maxC+1;
        if(outer>=0&&outer<=7&&whiteColumn(plane,skin,skinLum,outer,b.minR,b.maxR)){if(left)minC=outer;else maxC=outer;}
        int pupilCol=darkestColumn(plane,minC,maxC,b.minR,b.maxR);
        for(int r=b.minR;r<=b.maxR;r++)for(int c=minC;c<=maxC;c++)marks[r*8+c]=c==pupilCol?PUPIL:EYE;
    }

    /** True when the column reads as eye white on every eye row: clearly lighter or different from skin. */
    private static boolean whiteColumn(int[] plane,int skin,int skinLum,int col,int minR,int maxR){
        for(int r=minR;r<=maxR;r++){
            int p=plane[r*8+col];
            if(lum(p)<=skinLum+WHITE_RISE&&diff(p,skin)<=WHITE_DIFF)return false;
        }
        return true;
    }

    /** Column with the lowest average brightness; the iris/pupil lives there. */
    private static int darkestColumn(int[] plane,int minC,int maxC,int minR,int maxR){
        int best=minC;long bestSum=Long.MAX_VALUE,bestN=0,brightestSum=-1,brightestN=0;
        for(int c=minC;c<=maxC;c++){
            long sum=0;int n=0;
            for(int r=minR;r<=maxR;r++){sum+=lum(plane[r*8+c]);n++;}
            if(sum<bestSum){bestSum=sum;best=c;bestN=n;}
            if(sum>brightestSum){brightestSum=sum;brightestN=n;}
        }
        if(bestN==0||brightestN==0)return best;
        long avgDark=bestSum/bestN,avgBright=brightestSum/brightestN;
        return avgBright-avgDark>=25?best:minC;
    }

    private static void brow(int[] marks,boolean[] ink,Blob b){
        for(int top=Math.max(0,b.minR-2);top<b.minR;top++){
            for(int c=Math.max(0,b.minC-1);c<=Math.min(7,b.maxC+1);c++){
                int i=top*8+c;
                if(marks[i]!=NONE||!ink[i])continue;
                marks[i]=BROW;
            }
        }
    }

    private static boolean hasMark(int[] marks){for(int m:marks)if(m!=NONE)return true;return false;}

    /** Median brightness of each face row, or -1 when the row is mostly transparent. */
    private static int[] rowLuminance(int[] plane){
        int[] out=new int[8];
        for(int r=0;r<8;r++){
            int[] lums=new int[8];int n=0;
            for(int c=0;c<8;c++){
                int p=plane[r*8+c];
                if((p>>>24)>=64)lums[n++]=lum(p);
            }
            if(n<4){out[r]=-1;continue;}
            Arrays.sort(lums,0,n);
            out[r]=(lums[(n-1)/2]+lums[n/2])/2;
        }
        return out;
    }

    /** Most common colour bucket in the lower face; the skin, not hair or a single bright texel. */
    private static int skinTone(int[] plane){
        Map<Integer,int[]> buckets=new HashMap<>();
        for(int i=3*8;i<64;i++){
            int c=plane[i];
            if((c>>>24)<64)continue;
            int r=c>>16&255,g=c>>8&255,b=c&255;
            int key=(r>>3)<<10|(g>>3)<<5|(b>>3);
            int[] e=buckets.get(key);
            if(e==null){e=new int[]{0,0,0,0};buckets.put(key,e);}
            e[0]++;e[1]+=r;e[2]+=g;e[3]+=b;
        }
        int[] best=null;int bestCount=0,bestLum=-1;
        for(int[] e:buckets.values()){
            int avg=(e[1]/e[0])<<16|(e[2]/e[0])<<8|e[3]/e[0];
            int l=lum(avg);
            if(e[0]>bestCount||e[0]==bestCount&&l>bestLum){bestCount=e[0];bestLum=l;best=e;}
        }
        if(best==null)return 0xFFC8A078;
        return 0xFF000000|(best[1]/best[0])<<16|(best[2]/best[0])<<8|best[3]/best[0];
    }
    private static int diff(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
    private static int chroma(int c){
        int r=c>>16&255,g=c>>8&255,b=c&255;
        int hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));
        return hi-lo;
    }
    private static int lum(int c){return ((c>>16&255)*30+(c>>8&255)*59+(c&255)*11)/100;}
}
