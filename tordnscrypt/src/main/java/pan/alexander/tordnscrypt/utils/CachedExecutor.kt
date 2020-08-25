package pan.alexander.tordnscrypt.utils

import android.util.Log
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CachedExecutor {

    @Volatile private var instance: ExecutorService? = null

    fun getExecutorService(): ExecutorService {

        if (instance == null || instance?.isShutdown == true) {
            synchronized(CachedExecutor::class) {
                if (instance == null || instance?.isShutdown == true) {
                    instance = Executors.newCachedThreadPool()
                    Log.i(LOG_TAG, "CachedExecutor is restarted")
                }
            }
        }

        return instance ?: Executors.newCachedThreadPool()
    }

    fun startExecutorService() {
        if (instance == null || instance?.isShutdown == true) {
            synchronized(CachedExecutor::class) {
                if (instance == null || instance?.isShutdown == true) {
                    instance = Executors.newCachedThreadPool()
                    Log.i(LOG_TAG, "CachedExecutor is started")
                }
            }
        }
    }

    fun stopExecutorService() {
        Thread {
            if (instance != null && instance?.isShutdown == false) {
                instance?.shutdown()
                try {
                    instance?.awaitTermination(10, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    instance?.shutdownNow()
                    Log.w(LOG_TAG, "CachedExecutor awaitTermination has interrupted " + e.message)
                }
                Log.i(LOG_TAG, "CachedExecutor is stopped")
            }
        }.start()
    }
}