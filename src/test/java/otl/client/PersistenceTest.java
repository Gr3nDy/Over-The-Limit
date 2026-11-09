package otl.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PersistenceTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void heightOverlayStartsOffEvenWithASavedOnPreference() throws Exception {
        boolean previous=UtilitySettings.heightLimitOverlay;
        try{
            UtilitySettings.heightLimitOverlay=true;
            com.google.gson.JsonObject settings=new com.google.gson.JsonObject();
            settings.addProperty("height_limit_overlay",true);
            java.lang.reflect.Method read=UtilitySettings.class.getDeclaredMethod("read",com.google.gson.JsonObject.class);
            read.setAccessible(true);read.invoke(null,settings);
            assertFalse(UtilitySettings.heightLimitOverlay);
        }finally{UtilitySettings.heightLimitOverlay=previous;}
    }
    @Test public void heightOverlayIsUnavailableAndCannotBeEnabledThroughItsModule(){
        boolean previous=UtilitySettings.heightLimitOverlay;
        try{
            OtlModule height=OtlModule.ALL.stream().filter(module->module.id.equals("height_limit_overlay")).findFirst().get();
            assertFalse(height.available());assertFalse(height.enabled());assertNull(height.settings(null));
            height.setEnabled(true);assertFalse(height.enabled());assertFalse(UtilitySettings.heightLimitOverlay);
            assertTrue(OtlModule.ALL.stream().filter(module->module.id.equals("bow_optimizer")).findFirst().get().available());
        }finally{UtilitySettings.heightLimitOverlay=previous;}
    }

    @Test public void replacesCompleteFileAndCleansTemporaryFiles() throws Exception {
        Path p=temp.getRoot().toPath().resolve("nested/settings.json");
        AtomicFiles.write(p,"old".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(p,"new complete value".getBytes(StandardCharsets.UTF_8));
        assertEquals("new complete value",new String(Files.readAllBytes(p),StandardCharsets.UTF_8));
        try(java.util.stream.Stream<Path> files=Files.list(p.getParent())){assertEquals(1,files.count());}
    }

    @Test public void failedWriteKeepsPreviousFile() throws Exception {
        Path p=temp.newFile().toPath();
        AtomicFiles.write(p,new byte[]{1,2,3});
        try{AtomicFiles.write(p,null);fail("Expected failed write");}catch(NullPointerException expected){}
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(p));
        try(java.util.stream.Stream<Path> files=Files.list(p.getParent())){assertEquals(1,files.count());}
    }

    @Test public void erasedFaceRemainsAnExplicitOverrideAfterReload() throws Exception {
        Path p=temp.getRoot().toPath().resolve("marks/skin.txt");
        assertEquals(0,FaceMarks.load(p).length);
        int[] marks=new int[64];marks[42]=4;
        assertTrue(FaceMarks.save(p,marks));assertArrayEquals(marks,FaceMarks.load(p));
        assertTrue(FaceMarks.save(p,new int[64]));assertArrayEquals(new int[64],FaceMarks.load(p));
        assertTrue(FaceMarks.save(p,null));assertFalse(Files.exists(p));assertEquals(0,FaceMarks.load(p).length);
    }
}
