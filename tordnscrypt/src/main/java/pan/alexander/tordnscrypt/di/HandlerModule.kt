package pan.alexander.tordnscrypt.di

import android.os.Handler
import android.os.Looper
import dagger.Module
import dagger.Provides

@Module
class HandlerModule {
    @Provides
    fun provideMainHandler() = Handler(Looper.getMainLooper())
}
