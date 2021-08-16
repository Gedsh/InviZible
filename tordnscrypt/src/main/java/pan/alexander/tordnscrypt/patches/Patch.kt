package pan.alexander.tordnscrypt.patches

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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.BuildConfig
import pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_41
import pan.alexander.tordnscrypt.utils.PrefManager
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.lang.Exception
import java.util.concurrent.atomic.AtomicBoolean

private const val SAVED_VERSION_CODE = "SAVED_VERSION_CODE"

class Patch(private val context: Context) {

    private companion object {
        val patchingIsInProgress = AtomicBoolean(false)
    }

    private val dnsCryptConfigPatches = mutableListOf<PatchLine>()
    private val torConfigPatches = mutableListOf<PatchLine>()
    private val itpdConfigPatches = mutableListOf<PatchLine>()

    @WorkerThread
    fun checkPatches() {

        if (patchingIsInProgress.compareAndSet(false, true)) {
            try {
                tryCheckPatches()
            } finally {
                patchingIsInProgress.getAndSet(false)
            }
        }
    }

    private fun tryCheckPatches() {
        val currentVersion = BuildConfig.VERSION_CODE
        val currentVersionSaved = PrefManager(context).getIntPref(SAVED_VERSION_CODE)

        if (currentVersionSaved != 0 && currentVersion > currentVersionSaved) {
            try {
                val configUtil = ConfigUtil(context)

                removeQuad9FromBrokenImplementation()
                changeV2DNSCryptUpdateSourcesToV3()
                replaceBlackNames()
                updateITPDAddressBookDefaultUrl()
                fallbackResolverToBootstrapResolvers()

                if (dnsCryptConfigPatches.isNotEmpty()) {
                    configUtil.patchDNSCryptConfig(dnsCryptConfigPatches)
                }

                if (torConfigPatches.isNotEmpty()) {
                    configUtil.patchTorConfig(torConfigPatches)
                }

                if (itpdConfigPatches.isNotEmpty()) {
                    configUtil.patchItpdConfig(itpdConfigPatches)
                }

                configUtil.updateTorGeoip()

                PrefManager(context).setIntPref(SAVED_VERSION_CODE, currentVersion)
            } catch (e: Exception) {
                Log.e(
                    LOG_TAG,
                    "Patch checkPatches exception ${e.message} ${e.cause} ${e.stackTrace}"
                )
            }

        } else if (currentVersionSaved == 0) {
            PrefManager(context).setIntPref(SAVED_VERSION_CODE, currentVersion)
        }
    }

    private fun removeQuad9FromBrokenImplementation() {
        dnsCryptConfigPatches.add(
            PatchLine(
                "[broken_implementations]",
                Regex("fragments_blocked =.*quad9-dnscrypt.*"),
                "fragments_blocked = ['cisco', 'cisco-ipv6', 'cisco-familyshield'," +
                        " 'cisco-familyshield-ipv6', 'cleanbrowsing-adult', 'cleanbrowsing-family-ipv6'," +
                        " 'cleanbrowsing-family', 'cleanbrowsing-security']"
            )
        )
    }

    private fun changeV2DNSCryptUpdateSourcesToV3() {
        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex(".*v2/public-resolvers.md.*"),
                "urls = ['https://raw.githubusercontent.com/DNSCrypt/dnscrypt-resolvers/master/v3/public-resolvers.md'," +
                        " 'https://download.dnscrypt.info/resolvers-list/v3/public-resolvers.md']"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex(".*v2/relays.md.*"),
                "urls = ['https://raw.githubusercontent.com/DNSCrypt/dnscrypt-resolvers/master/v3/relays.md'," +
                        " 'https://download.dnscrypt.info/resolvers-list/v3/relays.md']"
            )
        )
    }

    private fun replaceBlackNames() {
        dnsCryptConfigPatches.add(
            PatchLine(
                "[blacklist]",
                Regex("blacklist_file = 'blacklist.txt'"), "blocked_names_file = 'blacklist.txt'"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "[ip_blacklist]",
                Regex("blacklist_file = 'ip-blacklist.txt'"),
                "blocked_ips_file = 'ip-blacklist.txt'"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "[whitelist]",
                Regex("whitelist_file = 'whitelist.txt'"), "allowed_names_file = 'whitelist.txt'"
            )
        )

        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex("\\[blacklist]"), "[blocked_names]"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex("\\[ip_blacklist]"), "[blocked_ips]"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex("\\[whitelist]"), "[allowed_names]"
            )
        )
    }

    private fun updateITPDAddressBookDefaultUrl() {
        itpdConfigPatches.add(
            PatchLine(
                "[addressbook]",
                Regex("defaulturl = http://joajgazyztfssty4w2on5oaqksz6tqoxbduy553y34mf4byv6gpq.b32.i2p/export/alive-hosts.txt"),
                "defaulturl = http://shx5vqsw7usdaunyzr2qmes2fq37oumybpudrd4jjj4e4vk4uusa.b32.i2p/hosts.txt"
            )
        )
    }

    private fun fallbackResolverToBootstrapResolvers() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (sharedPreferences.contains("bootstrap_resolvers")) {
            return
        }

        val ipRegex =
            Regex("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)")


        var fallbackResolver = QUAD_DNS_41
        if (sharedPreferences.contains("fallback_resolvers")) {
            val fallbackResolversPreference = sharedPreferences
                .getString("fallback_resolvers", QUAD_DNS_41)?.trim() ?: QUAD_DNS_41
            val matcher = ipRegex.toPattern().matcher(fallbackResolversPreference)
            if (matcher.find()) {
                fallbackResolver = matcher.group()
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("bootstrap_resolvers", fallbackResolver)
                .apply()
        } else if (sharedPreferences.contains("fallback_resolver")) {
            val fallbackResolverPreference = sharedPreferences
                .getString("fallback_resolver", QUAD_DNS_41)?.trim() ?: QUAD_DNS_41
            val matcher = ipRegex.toPattern().matcher(fallbackResolverPreference)

            if (matcher.find()) {
                fallbackResolver = matcher.group()
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("bootstrap_resolvers", fallbackResolver)
                .apply()
        }

        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex("fallback_resolver =.+"), "bootstrap_resolvers = ['$fallbackResolver:53']"
            )
        )
        dnsCryptConfigPatches.add(
            PatchLine(
                "",
                Regex("fallback_resolvers =.+"), "bootstrap_resolvers = ['$fallbackResolver:53']"
            )
        )
    }

}
