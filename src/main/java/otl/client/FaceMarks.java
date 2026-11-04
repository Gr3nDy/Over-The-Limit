package otl.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** Painted face marks with automatic detection as the fallback; stored as plain text under config/otl. */
final class FaceMarks {
    private static final Map<String,int[]> PAINTED=new HashMap<>();
    private static final Map<String,int[]> AUTO=new HashMap<>();
    private static final int[] NONE=new int[0];
    static boolean lastSaveOk=true;
    private FaceMarks(){}

    static String key(ResourceLocation texture){return texture.toString();}
    /** User-painted marks for this skin, or null when none were painted. */
    static int[] marks(ResourceLocation texture){
        int[] painted=PAINTED.computeIfAbsent(key(texture),FaceMarks::load);
        return painted.length==0?null:painted;
    }
    /** Painted marks if any, else the detector's reading for this skin. */
    static int[] effective(ResourceLocation texture){
        int[] painted=marks(texture);
        if(painted!=null)return painted;
        return auto(texture);
    }
    static int[] auto(ResourceLocation texture){
        String k=key(texture);
        if(AUTO.containsKey(k))return AUTO.get(k).length==0?null:AUTO.get(k);
        if(!SkinFace.loaded(texture))return null;
        int[] detected=EyeDetector.detect(SkinFace.facePixels(texture),SkinFace.hatPixels(texture));
        // Do not permanently cache a miss: another player's skin may still be uploading.
        if(detected==null)return null;
        AUTO.put(k,(int[])detected.clone());
        return detected;
    }
    /** Editor entry point: swap the painted marks for a skin; persist only on save. */
    static void replace(ResourceLocation texture,int[] value,boolean persist){
        int[] copy=value==null?NONE:(int[])value.clone();
        PAINTED.put(key(texture),copy);AUTO.remove(key(texture));
        if(persist)lastSaveOk=save(key(texture),copy);
    }
    static void clear(ResourceLocation texture){replace(texture,null,true);}
    static void clearCache(){PAINTED.clear();AUTO.clear();}

    private static Path file(String key){return MinecraftAccess.get().mcDataDir.toPath().resolve("config/otl/marks/"+sanitize(key)+".txt");}
    private static String sanitize(String key){return key.replaceAll("[^A-Za-z0-9._-]","_");}

    private static int[] load(String k){
        try{
            Path p=file(k);
            if(!Files.exists(p))return NONE;
            int[] marks=new int[64];int count=0;
            for(String line:Files.readAllLines(p)){
                int sep=line.indexOf(':');
                if(sep<=0)continue;
                int cell=Integer.parseInt(line.substring(0,sep).trim());
                int kind=Integer.parseInt(line.substring(sep+1).trim());
                if(cell>=0&&cell<64&&kind>=1&&kind<=4){marks[cell]=kind;count++;}
            }
            return count==0?NONE:marks;
        }catch(Exception e){return NONE;}
    }
    private static boolean save(String k,int[] value){
        try{
            Path p=file(k);
            Files.createDirectories(p.getParent());
            if(value==null||value.length==0){Files.deleteIfExists(p);return true;}
            StringBuilder text=new StringBuilder();
            for(int i=0;i<64;i++)if(value[i]!=0)text.append(i).append(':').append(value[i]).append('\n');
            Files.write(p,text.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        }catch(Exception e){
            System.err.println("OTL: could not save face marks: "+e.getMessage());
            return false;
        }
    }
}
