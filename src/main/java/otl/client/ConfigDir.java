package otl.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/** Single owner of config/Over The Limit; moves a legacy config/overcast_util on first run. */
final class ConfigDir {
    private static Path root;

    /** config/Over The Limit, migrating the legacy folder on first access. */
    static synchronized Path root(){
        if(root==null){
            root=MinecraftAccess.get().mcDataDir.toPath().resolve("config").resolve("Over The Limit");
            migrate();
        }
        return root;
    }
    /** Resolves parts under the config root, guarding against "..". */
    static File parts(String... parts){
        File file=root().toFile();
        for(String part:parts){
            if(part.isEmpty()||part.equals(".")||part.equals(".."))return new File(root().toFile(),".invalid");
            file=new File(file,part);
        }
        return file;
    }
    private static void migrate(){
        try{
            Path legacy=root().getParent().resolve("overcast_util");
            if(!Files.isDirectory(legacy)||Files.exists(root()))return;
            try{Files.move(legacy,root());}
            catch(Exception renameFailed){copyRecursively(legacy,root());deleteRecursively(legacy);}
            System.out.println("Overcast Utilities: migrated config/overcast_util to config/Over The Limit");
        }catch(Exception e){System.err.println("Overcast Utilities: config migration failed: "+e.getMessage());}
    }
    private static void copyRecursively(Path source,Path target)throws IOException{
        try(java.util.stream.Stream<Path> stream=Files.walk(source)){
            stream.forEach(path->{
                Path relative=source.relativize(path);
                Path destination=target.resolve(relative);
                try{
                    if(Files.isDirectory(path))Files.createDirectories(destination);
                    else{Files.createDirectories(destination.getParent());Files.copy(path,destination,StandardCopyOption.REPLACE_EXISTING);}
                }catch(IOException e){throw new IllegalStateException(e);}
            });
        }
    }
    private static void deleteRecursively(Path source)throws IOException{
        try(java.util.stream.Stream<Path> stream=Files.walk(source)){
            stream.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException e){throw new IllegalStateException(e);}});
        }
    }
    /** Validates a repo-relative asset path; rejects traversal. */
    static boolean validAsset(String asset){
        if(asset==null||asset.isEmpty())return false;
        for(String part:asset.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.contains("\\")||part.indexOf(':')>=0)return false;
        return true;
    }
}
