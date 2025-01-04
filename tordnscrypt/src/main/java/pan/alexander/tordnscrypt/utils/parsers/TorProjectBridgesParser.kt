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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.parsers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.annotation.Keep
import pan.alexander.tordnscrypt.domain.bridges.ParseBridgesResult
import pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_BOUNDS
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern
import javax.inject.Inject

@Keep
class TorProjectBridgesParser @Inject constructor() {

    private val captchaPattern by lazy {
        Pattern.compile("data:image/(?:gif|png|jpeg|bmp|webp)(?:;charset=utf-8)?;base64,([A-Za-z0-9+/]+?={0,2})\"")
    }

    private val captchaChallengeFieldPattern by lazy {
        Pattern.compile("input.+?type=\"hidden\".+?name=\"\\w+?\".+?value=\"([A-Za-z0-9-_]+?)\"")
    }

    private val ipv4BridgeBase = "(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+"
    private val ipv6BridgeBase = "\\[$IPv6_REGEX_NO_BOUNDS]:\\d+ +\\w+"

    private val vanillaBridgePatternIPv4 by lazy { Pattern.compile(ipv4BridgeBase) }
    private val vanillaBridgePatternIPv6 by lazy { Pattern.compile(ipv6BridgeBase) }

    private val obfs4BridgePatternIPv4 by lazy {
        Pattern.compile("obfs4 +$ipv4BridgeBase +cert=.+ +iat-mode=\\d")
    }
    private val obfs4BridgePatternIPv6 by lazy {
        Pattern.compile("obfs4 +$ipv6BridgeBase +cert=.+ +iat-mode=\\d")
    }

    private val webTunnelBridgePatternIPv4 by lazy {
        Pattern.compile("webtunnel +$ipv4BridgeBase +url=http(s)?://[\\w./-]+")
    }
    private val webTunnelBridgePatternIPv6 by lazy {
        Pattern.compile("webtunnel +$ipv6BridgeBase +url=http(s)?://[\\w./-]+")
    }

    @Throws(IOException::class)
    fun parseCaptchaImage(inputStream: InputStream): Pair<Bitmap, String> {

        var captchaImage: Bitmap? = null
        var captchaChallengeField: String? = null

        inputStream.bufferedReader().use {
            var line = it.readLine()

            val captchaInputForm = StringBuilder()

            while (line != null && !Thread.currentThread().isInterrupted) {

                if (captchaImage == null && line.contains("data:image")) {
                    captchaImage = parseCaptcha(line)
                } else if (captchaChallengeField == null) {
                    if (line.contains("button type=\"submit\"") && captchaInputForm.isNotEmpty()) {
                        captchaChallengeField =
                            parseCaptchaChallengeField(captchaInputForm.toString())
                        captchaInputForm.clear()
                    } else if (line.contains("method=\"POST\"") || captchaInputForm.isNotEmpty()) {
                        captchaInputForm.append(line).append(" ")
                    }
                } else if (captchaImage != null) {
                    break
                }

                line = it.readLine()
            }
        }

        if (captchaImage != null && captchaChallengeField != null) {
            return Pair(captchaImage!!, captchaChallengeField!!)
        }

        throw IllegalStateException("No bridges. Try later.")
    }

    @Throws(IOException::class)
    fun parseBridges(inputStream: InputStream): ParseBridgesResult {

        val newBridges = arrayListOf<String>()
        var captchaToChallengeField: Pair<Bitmap, String>? = null
        val savedLines = arrayListOf<String>()

        inputStream.bufferedReader().use {
            var line = it.readLine()

            while (line != null && !Thread.currentThread().isInterrupted) {

                if (vanillaBridgePatternIPv4.matcher(line).find()
                    || vanillaBridgePatternIPv6.matcher(line).find()
                ) {
                    parseBridge(line)?.let { bridge ->
                        newBridges.add(bridge)
                    }
                } else if (newBridges.isNotEmpty() && line.contains("</div>")) {
                    break
                }

                if (line.isNotBlank()) {
                    savedLines.add(line)
                }

                line = it.readLine()
            }

            if (newBridges.isEmpty()) {
                val baos = ByteArrayOutputStream().apply {
                    use { bs ->
                        savedLines.forEach { savedLine ->
                            bs.write(savedLine.toByteArray())
                            bs.write("\n".toByteArray())
                        }
                    }
                }

                captchaToChallengeField =
                    parseCaptchaImage(ByteArrayInputStream(baos.toByteArray()))
            }

            return when {
                newBridges.isNotEmpty() -> {
                    ParseBridgesResult.BridgesReady(newBridges.joinToString("\n"))
                }

                captchaToChallengeField != null -> {
                    ParseBridgesResult.RecaptchaChallenge(
                        captchaToChallengeField!!.first,
                        captchaToChallengeField!!.second
                    )
                }

                else -> throw IllegalStateException("No bridges. Try later.")
            }
        }
    }

    private fun parseCaptcha(line: String): Bitmap? {
        val matcher = captchaPattern.matcher(line)
        return if (matcher.find()) {
            val data = Base64.decode(matcher.group(1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } else {
            null
        }
    }

    private fun parseCaptchaChallengeField(form: String): String? {
        val matcher = captchaChallengeFieldPattern.matcher(form)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    private fun parseBridge(line: String): String? =
        if (containsObfs4Bridge(line)) {
            parseObfs4Bridge(line)
        } else if (containsWebTunnelBridge(line)) {
            parseWebTunnelBridge(line)
        } else {
            parseVanillaBridge(line)
        }


    private fun parseObfs4Bridge(line: String): String? {
        val matcherIPv4 = obfs4BridgePatternIPv4.matcher(line)
        if (matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = obfs4BridgePatternIPv6.matcher(line)
        if (matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("TorProjectBridgesParser parseObfs4Bridge failed $line")

        return null
    }

    private fun parseWebTunnelBridge(line: String): String? {
        val matcherIPv4 = webTunnelBridgePatternIPv4.matcher(line)
        if (matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = webTunnelBridgePatternIPv6.matcher(line)
        if (matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("TorProjectBridgesParser parseWebTunnelBridge failed $line")

        return null
    }

    private fun parseVanillaBridge(line: String): String? {
        val matcherIPv4 = vanillaBridgePatternIPv4.matcher(line)
        if (matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = vanillaBridgePatternIPv6.matcher(line)
        if (matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("TorProjectBridgesParser parseVanillaBridge failed $line")

        return null
    }

    private fun containsObfs4Bridge(line: String): Boolean = line.contains("obfs4")

    private fun containsWebTunnelBridge(line: String): Boolean = line.contains("webtunnel")
}
