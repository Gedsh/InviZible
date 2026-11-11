package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for util.c. */
@RunWith(AndroidJUnit4.class)
public final class UtilTest extends NativeTestSupport {
    @Test public void longLogsAreBoundedAndHexAllocationCanFail() { check("boundedLogging"); }
}
