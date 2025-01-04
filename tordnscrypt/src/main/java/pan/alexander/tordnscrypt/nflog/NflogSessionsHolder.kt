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

package pan.alexander.tordnscrypt.nflog

import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_KERNEL
import javax.inject.Inject

private const val SESSIONS_MAX_SIZE = 256

class NflogSessionsHolder @Inject constructor() {

    private val sessionToUids = HashMap<Session, Int>(SESSIONS_MAX_SIZE / 2)

    fun addSession(
        uid: Int,
        protocol: String,
        saddr: String,
        sport: Int,
        daddr: String,
        dport: Int
    ) {
        sessionToUids[Session(System.currentTimeMillis(), protocol, saddr, sport, daddr, dport)] =
            uid

        if (sessionToUids.size >= SESSIONS_MAX_SIZE) {
            clearOldSessions()
        }
    }

    fun getUid(
        protocol: String,
        saddr: String,
        sport: Int,
        daddr: String,
        dport: Int
    ): Int = sessionToUids[Session(0, protocol, saddr, sport, daddr, dport)] ?: SPECIAL_UID_KERNEL


    private fun clearOldSessions() {
        sessionToUids.keys.sortedBy { it.time }.forEachIndexed { index, session ->
            if (index < SESSIONS_MAX_SIZE / 3) {
                sessionToUids.remove(session)
            } else {
                return
            }
        }
    }

    private class Session(
        val time: Long,
        val protocol: String,
        val saddr: String,
        val sport: Int,
        val daddr: String,
        val dport: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Session

            if (protocol != other.protocol) return false
            if (saddr != other.saddr) return false
            if (sport != other.sport) return false
            if (daddr != other.daddr) return false
            if (dport != other.dport) return false

            return true
        }

        override fun hashCode(): Int {
            var result = protocol.hashCode()
            result = 31 * result + saddr.hashCode()
            result = 31 * result + sport
            result = 31 * result + daddr.hashCode()
            result = 31 * result + dport
            return result
        }
    }
}
