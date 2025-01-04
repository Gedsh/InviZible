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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.local

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class DnsRulesUpdateProgress : Parcelable {

    data class UpdateProgress(
        val name: String,
        val url: String? = null,
        val size: Long,
        val count: Int
    ): DnsRulesUpdateProgress()

    data class UpdateFinished(
        val name: String,
        val url: String? = null,
        val size: Long,
        val count: Int
    ): DnsRulesUpdateProgress()

    data class UpdateFailure(
        val name: String,
        val url: String? = null,
        val error: String
    ): DnsRulesUpdateProgress()
}
