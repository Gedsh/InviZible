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

package pan.alexander.tordnscrypt.domain.log_reader.dnscrypt

import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel
import pan.alexander.tordnscrypt.domain.log_reader.AbstractLogParser
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository
import pan.alexander.tordnscrypt.utils.session.AppSessionStore
import pan.alexander.tordnscrypt.utils.session.SessionKeys.DNSCRYPT_SERVERS_PING
import java.util.regex.Pattern
import javax.inject.Inject

private const val COUNT_DOWN_TIMER = 5

private val patternDnsCryptServerPing = Pattern.compile("^\\[.+] +\\[NOTICE] +- +(\\d+)ms +(.+)$")

class DNSCryptLogParser(
    private val modulesLogRepository: ModulesLogRepository
) : AbstractLogParser() {

    @Inject
    lateinit var sessionStore: AppSessionStore

    private var startedSuccessfully = false
    private var startedWithError = false
    private var linesSaved = listOf<String>()
    private var errorCountDownCounter = COUNT_DOWN_TIMER
    private var lastPingBlockStartFound = false
    private var lastPingBlockEndFound = false

    init {
        App.instance.daggerComponent.inject(this)
    }

    override fun parseLog(): LogDataModel {
        val lines = modulesLogRepository.getDNSCryptLog()
        lastPingBlockStartFound = false
        lastPingBlockEndFound = false

        var linesChanged = false
        if (lines.size != linesSaved.size) {
            linesChanged = true
            linesSaved = ArrayList(lines)
            sessionStore.clearMap(DNSCRYPT_SERVERS_PING)
        }

        for (i in lines.size - 1 downTo 0) {
            val line = lines[i]

            if (!linesChanged && startedSuccessfully) {
                break
            }

            if (linesChanged) {
                parseServersPing(line)
            }

            if (!startedSuccessfully) {
                if (line.contains(" OK ") || line.contains("lowest initial latency")) {
                    startedSuccessfully = true
                    startedWithError = false
                    errorCountDownCounter = COUNT_DOWN_TIMER
                    break
                } else if (line.contains("Stopped.")) {
                    startedSuccessfully = false
                    startedWithError = false
                    break
                } else if (line.contains("connect: connection refused")
                    || (line.contains("ERROR") && !line.contains("Unable to resolve"))
                    || (line.contains("[CRITICAL]") && !line.contains("Certificate hash"))
                    || line.contains("[FATAL]")
                ) {
                    if (errorCountDownCounter <= 0) {
                        startedSuccessfully = false
                        startedWithError = true
                        errorCountDownCounter = COUNT_DOWN_TIMER
                    } else {
                        errorCountDownCounter--
                    }

                    break
                }
            }
        }

        return LogDataModel(
            startedSuccessfully,
            startedWithError,
            -1,
            formatLines(linesSaved),
            linesSaved.size
        )
    }

    private fun parseServersPing(line: String) {
        if (line.contains("Server with the lowest initial latency:")) {
            lastPingBlockEndFound = true
        } else if (line.endsWith("Sorted latencies:")) {
            lastPingBlockStartFound = true
        } else if (lastPingBlockEndFound && !lastPingBlockStartFound) {
            var server = ""
            var ping = -1
            val matcher = patternDnsCryptServerPing.matcher(line)
            if (matcher.find()) {
                ping = matcher.group(1)?.toInt() ?: -1
                server = matcher.group(2) ?: ""
            }

            if (server.isNotEmpty() && ping >= 0) {
                with(sessionStore) {
                    restoreMap<String, Int>(DNSCRYPT_SERVERS_PING)
                        .toMutableMap()
                        .also {
                            it.put(server, ping)
                            save(DNSCRYPT_SERVERS_PING, it)
                        }
                }
            }
        }
    }
}
