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
        migrateOvercastUtil();
        migrateOtl();
    }
    private static void migrateOvercastUtil(){
        try{
            Path legacy=root().getParent().resolve("overcast_util");
            if(!Files.isDirectory(legacy)||Files.exists(root()))return;
            Path staging=Files.createTempDirectory(legacy.getParent(),".otl-migrate-");
            try{
                copyRecursively(legacy,staging);
                deleteRecursively(legacy);
                Files.move(staging,root());
                System.out.println("Overcast Utilities: migrated config/overcast_util to config/Over The Limit");
            }catch(Exception migrationFailed){
                try{deleteRecursively(staging);}catch(Exception ignored){}
                throw migrationFailed;
            }
        }catch(Exception e){System.err.println("Overcast Utilities: config migration failed: "+e.getMessage());}
    }
    /** Face Rig settings and marks moved into the shared root; missing names merge, nothing is overwritten. */
    private static void migrateOtl(){
        try{
            Path legacy=root().getParent().resolve("otl");
            if(!Files.isDirectory(legacy))return;
            java.util.List<Path> files=new java.util.ArrayList<>();
            try(java.util.stream.Stream<Path> stream=Files.walk(legacy)){
                stream.filter(Files::isRegularFile).forEach(files::add);
            }
            for(Path source:files){
                Path target=root().resolve(legacy.relativize(source).toString());
                if(Files.exists(target))continue;
                Files.createDirectories(target.getParent());
                Files.copy(source,target);
            }
            deleteRecursively(legacy);
            System.out.println("OTL: migrated config/otl into config/Over The Limit");
        }catch(Exception e){System.err.println("OTL: config/otl migration failed: "+e.getMessage());}
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
}
