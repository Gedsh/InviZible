package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for invizible.c. */
@RunWith(AndroidJUnit4.class)
public final class InvizibleTest extends NativeTestSupport {
    @Test public void socksCredentialByteLimits() { check("memorySocks5"); }
}
