package pan.alexander.tordnscrypt.nativetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Compares valid-input behavior with an explicitly selected Git revision. */
@RunWith(AndroidJUnit4.class)
public final class NativeBaselineComparisonTest {
    private static synchronized native String runCurrentCompatibility(String path);
    private static synchronized native String runBaselineCompatibility(String path);

    @Test public void validInputsMatchBaseline() throws Exception {
        assumeTrue("Supply -PnativeBaseline=HEAD to compare revisions", BuildConfig.HAS_NATIVE_BASELINE);
        System.loadLibrary("invizible_native_tests");
        System.loadLibrary("invizible_native_baseline_tests");
        File cache = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File transcript = File.createTempFile("native-compatibility", ".txt", cache);
        try {
            String current = runCurrentCompatibility(transcript.getAbsolutePath());
            String baseline = runBaselineCompatibility(transcript.getAbsolutePath());
            assertEquals("Packet fingerprints, callbacks and session transitions", baseline, current);
        } finally {
            transcript.delete();
        }
    }
}
