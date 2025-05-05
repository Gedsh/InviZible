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

import android.util.Base64
import javax.inject.Inject

class DnsCryptSDNSParser @Inject constructor() {

    fun getRelayAddress(sdns: String): String {
        val bin = Base64.decode(sdns.toByteArray(), Base64.URL_SAFE)
        return when (val type = bin[0].toInt() and 0xFF) {
            ProtoType.RELAY_DNSCRYPT.magic -> handleDnsCryptRelay(bin)
            ProtoType.RELAY_ODOH.magic -> handleODoHRelay(bin)
            else -> throw IllegalArgumentException("SDNS type $type handling is not implemented")
        }
    }

    private fun handleDnsCryptRelay(bin: ByteArray): String {

        var address = ""

        if (bin.size < 9) {
            throw IllegalArgumentException("Stamp is too short")
        }

        var pos = 1
        val length = bin[pos].toInt() and 0xFF
        val binLen = bin.size

        if (1 + length > binLen - pos) {
            throw IllegalArgumentException("Invalid stamp")
        }

        pos++
        address = bin.copyOfRange(pos, pos + length).toString(Charsets.UTF_8)
        pos += length

        if (pos != binLen) {
            throw IllegalArgumentException("Invalid stamp (garbage after end)")
        }

        return getAddressWithPort(address)
    }

    private fun handleODoHRelay(bin: ByteArray): String {

        var address = ""

        if (bin.size < 13) {
            throw IllegalArgumentException("Stamp is too short")
        }

        var pos = 9
        val binLen = bin.size

        var length = bin[pos].toInt() and 0xFF
        if (1 + length >= binLen - pos) {
            throw IllegalArgumentException("Invalid sdns address")
        }
        pos++
        address = bin.copyOfRange(pos, pos + length).toString(Charsets.UTF_8)
        pos += length

        // Hashes
        while (true) {
            val vlen = bin[pos].toInt() and 0xFF
            length = vlen and vlen.inv().shr(7) //vlen & ~0x80
            if (1 + length >= binLen - pos) {
                throw IllegalArgumentException("Invalid sdns hash")
            }
            pos++
            pos += length
            if (vlen and 0x80 != 0x80) {
                break
            }
        }

        //Host name
        length = bin[pos].toInt() and 0xFF
        if (1 + length >= binLen - pos) {
            throw IllegalArgumentException("Invalid sdns host name")
        }
        pos++
        if (address.isEmpty()) {
            address = bin.copyOfRange(pos, pos + length).toString(Charsets.UTF_8)
        }
        pos += length

        //Path
        length = bin[pos].toInt() and 0xFF
        if (length >= binLen - pos) {
            throw IllegalArgumentException("Invalid sdns path")
        }
        pos++
        val path = bin.copyOfRange(pos, pos + length).toString(Charsets.UTF_8)
        pos += length

        if (pos != binLen) {
            throw IllegalArgumentException("Invalid sdns (garbage after end)")
        }

        if (address.isEmpty() && path.contains("/") && path.indexOf("/") > 0) {
            address = path.substring(0, path.indexOf("/"))
        }

        return getAddressWithPort(address)
    }

    private fun getAddressWithPort(address: String): String =
        if (address.isIPv6Address() && !address.matches(Regex(".+:\\d{1,5}$"))
            || address.isNotEmpty() && !address.contains(":")
        ) {
            "$address:443"
        } else {
            address
        }

    private fun String.isIPv6Address() = contains("[") && contains("]")

    enum class ProtoType(val magic: Int) {
        RELAY_DNSCRYPT(0x81),
        RELAY_ODOH(0x85)
    }
}
