package pan.alexander.tordnscrypt.dnscrypt_fragment

class DNSQueryLogRecord( val qName: String = "",
                         val aName:String = "",
                         var cName:String = "",
                         val hInfo:String = "",
                         val rCode: Int = 0,
                         var ip:String = "",
                         var uid:Int = -1000) {
    var blocked = false
    var blockedByIpv6 = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DNSQueryLogRecord

        if (qName != other.qName) return false
        if (aName != other.aName) return false
        if (cName != other.cName) return false
        if (hInfo != other.hInfo) return false
        if (rCode != other.rCode) return false
        if (ip != other.ip) return false
        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qName.hashCode()
        result = 31 * result + aName.hashCode()
        result = 31 * result + cName.hashCode()
        result = 31 * result + hInfo.hashCode()
        result = 31 * result + rCode
        result = 31 * result + ip.hashCode()
        result = 31 * result + uid
        return result
    }

}