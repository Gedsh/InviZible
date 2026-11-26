package pan.alexander.tordnscrypt.utils.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;

import androidx.annotation.RequiresApi;
import androidx.core.content.IntentCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;

import static org.junit.Assert.*;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK;

/** Records service dispatch without starting a service, running su or changing permissions. */
@RunWith(AndroidJUnit4.class)
public class RootCommandsDispatchTest {
    private final PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();
    private boolean originalRoot;
    private RecordingContext context;

    private static class RecordingContext extends ContextWrapper {
        Intent sent;
        boolean reject, fail;
        RecordingContext(Context context) { super(context); }
        ComponentName record(Intent intent) {
            sent = intent;
            if (fail) throw new IllegalStateException("dispatch regression");
            return reject ? null : intent.getComponent();
        }
        @Override public ComponentName startService(Intent intent) { return record(intent); }
    }
    @RequiresApi(26)
    private static final class ModernContext extends RecordingContext {
        ModernContext(Context context) { super(context); }
        @Override public ComponentName startForegroundService(Intent intent) { return record(intent); }
    }
    @Before public void setUp() {
        originalRoot = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
        preferences.setBoolPreference(ROOT_IS_AVAILABLE, true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = Build.VERSION.SDK_INT >= 26 ? new ModernContext(target) : new RecordingContext(target);
    }
    @After public void tearDown() {
        preferences.setBoolPreference(ROOT_IS_AVAILABLE, originalRoot);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void completionParcel() {
        AtomicInteger completed = new AtomicInteger();
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int code, android.os.Bundle data) { completed.incrementAndGet(); }
        };
        assertTrue(RootCommands.execute(context, Collections.singletonList("echo test"), NULL_MARK, receiver));
        assertEquals(RootExecService.RUN_COMMAND, context.sent.getAction());
        assertEquals(RootExecService.class.getName(), context.sent.getComponent().getClassName());
        Parcel parcel = Parcel.obtain();
        try {
            context.sent.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Intent delivered = Intent.CREATOR.createFromParcel(parcel);
            delivered.setExtrasClassLoader(RootCommands.class.getClassLoader());
            RootCommands commands = (RootCommands) delivered.getSerializableExtra("Commands");
            assertEquals(Collections.singletonList("echo test"), commands.getCommands());
            assertEquals(NULL_MARK, delivered.getIntExtra("Mark", -1));
            ResultReceiver completion = IntentCompat.getParcelableExtra(delivered,
                    RootCommands.RESULT_RECEIVER, ResultReceiver.class);
            assertNotNull(completion);
            completion.send(0, null);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(1, completed.get());
        } finally { parcel.recycle(); }
    }

    @Test public void failedOrRejectedStartReportsFailure() {
        context.reject = true;
        assertFalse(RootCommands.execute(context, Collections.singletonList("echo test"), NULL_MARK));
        context.reject = false;
        context.fail = true;
        assertFalse(RootCommands.execute(context, Collections.singletonList("echo test"), NULL_MARK));
    }

    @Test public void unavailableRootDoesNotDispatch() {
        preferences.setBoolPreference(ROOT_IS_AVAILABLE, false);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(RootCommands.execute(context, Collections.singletonList("echo test"), NULL_MARK));
        assertNull(context.sent);
    }
}
