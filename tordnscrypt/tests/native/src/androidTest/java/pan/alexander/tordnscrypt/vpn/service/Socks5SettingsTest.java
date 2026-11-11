package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import pan.alexander.tordnscrypt.nativetests.NativeTestSupport;

/** Compares preflight validation with the real JNI encoding and production setters. */
@RunWith(AndroidJUnit4.class)
public final class Socks5SettingsTest {
    @BeforeClass public static void loadLibrary() { NativeTestSupport.loadNativeTests(); }
    private static native void configureProxy(String address, String user, String password);
    private static native void validateProxy(String address, String user, String password);
    private static native int encodedLength(String value);

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) text.append(value);
        return text.toString();
    }

    private static void accepted(String address, String user, String password) {
        validateProxy(address, user, password);
        configureProxy(address, user, password);
    }

    private static void rejected(String address, String user, String password) {
        assertThrows(IllegalArgumentException.class, () -> validateProxy(address, user, password));
        assertThrows(IllegalArgumentException.class, () -> configureProxy(address, user, password));
    }

    @Test public void asciiByteBoundary() {
        accepted("127.0.0.1", repeat("a", 127), repeat("b", 127));
        rejected("127.0.0.1", repeat("a", 128), "");
        rejected("127.0.0.1", "", repeat("b", 128));
    }

    @Test public void twoByteCharactersAreLimitedByEncodedLength() {
        accepted("::1", repeat("é", 63), repeat("é", 63));
        rejected("::1", repeat("é", 64), "");
        rejected("::1", "", repeat("é", 64));
    }

    @Test public void threeByteCharactersAreLimitedByEncodedLength() {
        accepted("::1", repeat("中", 42), "");
        rejected("::1", repeat("中", 43), "");
    }

    @Test public void supplementaryCharactersUseModifiedUtf8() {
        String emoji = "\uD83D\uDE00";
        int width = encodedLength(emoji);
        assertTrue(width == 4 || width == 6);
        accepted("::1", repeat(emoji, 127 / width), "");
        rejected("::1", repeat(emoji, 127 / width + 1), "");
    }

    @Test public void nulUsesTwoModifiedUtf8Bytes() {
        accepted("::1", repeat("\0", 63), "");
        rejected("::1", repeat("\0", 64), "");
    }

    @Test public void addressByteBoundary() {
        accepted(repeat("a", 46), "", "");
        rejected(repeat("a", 47), "", "");
        rejected(repeat("é", 24), "", "");
    }

    @Test public void nullFieldsAreRejected() {
        rejected(null, "", "");
        rejected("::1", null, "");
        rejected("::1", "", null);
    }

    @Test public void disabledProxyUsesEmptyStrings() { accepted("", "", ""); }
}
