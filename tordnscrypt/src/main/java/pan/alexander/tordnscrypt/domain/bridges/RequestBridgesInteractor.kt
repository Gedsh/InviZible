/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.bridges

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import pan.alexander.tordnscrypt.utils.Constants.TOR_BRIDGES_ADDRESS
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager
import pan.alexander.tordnscrypt.utils.web.HttpsConnectionManager
import java.lang.Exception
import java.util.concurrent.Future
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

@ExperimentalCoroutinesApi
class RequestBridgesInteractor @Inject constructor(
    private val context: Context,
    private val requestBridgesRepository: RequestBridgesRepository,
    private val cachedExecutor: CachedExecutor,
    private val httpsConnectionManager: HttpsConnectionManager
) {

    private val wakeLocksManager by lazy { WakeLocksManager.getInstance() }

    @Volatile
    private var wakeLockIsHeld = false

    suspend fun requestCaptchaChallenge(transport: String, ipv6: Boolean): Pair<Bitmap, String> =

        suspendCancellableCoroutine { continuation ->

            val captchaChallengeTask = try {
                tryGetCaptchaImage(transport, ipv6, continuation)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                null
            }

            continuation.invokeOnCancellation {
                captchaChallengeTask?.cancel(true)
            }
        }

    private fun tryGetCaptchaImage(
        transport: String,
        ipv6: Boolean,
        continuation: CancellableContinuation<Pair<Bitmap, String>>
    ): Future<*>? = cachedExecutor.submit {
        try {

            lockWakeLock()

            val url = if (ipv6) {
                "${TOR_BRIDGES_ADDRESS}bridges?transport=${transport}&ipv6=yes"
            } else {
                "${TOR_BRIDGES_ADDRESS}bridges?transport=$transport"
            }

            httpsConnectionManager.get(url) { inputStream ->
                val captchaToSecret =
                    requestBridgesRepository.parseCaptchaChallengeImage(inputStream)
                continuation.resume(captchaToSecret, null)
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        } finally {
            releaseWakelock()
        }
    }

    suspend fun requestBridges(
        transport: String,
        ipv6: Boolean,
        captchaText: String,
        secretCode: String
    ): ParseBridgesResult =
        suspendCancellableCoroutine { continuation ->
            val requestBridgesTask = try {
                tryGetBridges(transport, ipv6, captchaText, secretCode, continuation)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                null
            }

            continuation.invokeOnCancellation {
                requestBridgesTask?.cancel(true)
            }
        }

    private fun tryGetBridges(
        transport: String,
        ipv6: Boolean,
        captchaText: String,
        secretCode: String,
        continuation: CancellableContinuation<ParseBridgesResult>
    ): Future<*>? = cachedExecutor.submit {
        try {

            lockWakeLock()

            val query = if (secretCode.isNotEmpty() && captchaText.isNotEmpty()) {
                linkedMapOf<String, String>().apply {
                    put("captcha_challenge_field", secretCode)
                    put("captcha_response_field", captchaText)
                    put("submit", "submit")
                }
            } else {
                linkedMapOf()
            }

            val url = if (ipv6) {
                "${TOR_BRIDGES_ADDRESS}bridges?transport=${transport}&ipv6=yes"
            } else {
                "${TOR_BRIDGES_ADDRESS}bridges?transport=$transport"
            }

            httpsConnectionManager.post(url, query) {
                continuation.resume(requestBridgesRepository.parseBridges(it), null)
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        } finally {
            releaseWakelock()
        }
    }

    private fun lockWakeLock() {
        if (!wakeLocksManager.isWiFiWakeLockHeld || !wakeLocksManager.isPowerWakeLockHeld) {
            wakeLockIsHeld = true
            wakeLocksManager.manageWiFiLock(context, true)
            wakeLocksManager.managePowerWakelock(context, true)
        }
    }

    private fun releaseWakelock() {
        if (wakeLockIsHeld) {
            wakeLockIsHeld = false
            wakeLocksManager.manageWiFiLock(context, false)
            wakeLocksManager.managePowerWakelock(context, false)
        }
    }
}
