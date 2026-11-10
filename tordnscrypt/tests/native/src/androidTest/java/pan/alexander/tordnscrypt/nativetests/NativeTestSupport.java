package pan.alexander.tordnscrypt.nativetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.BeforeClass;

/** Shared JNI bridge; protocol cases live in the source-specific JUnit classes. */
public abstract class NativeTestSupport {
    @BeforeClass
    public static void loadNativeTests() {
        System.loadLibrary("invizible_native_tests");
        assertEquals("Native sanitizer build mode", BuildConfig.NATIVE_SANITIZERS, sanitizersEnabled());
    }

    // Every source-specific class shares these native spies and checkpoint.
    protected static synchronized void check(String name) {
        assertNull(name, runNativeCase(name));
    }

    private static native String runNativeCase(String name);
    private static native boolean sanitizersEnabled();
}
