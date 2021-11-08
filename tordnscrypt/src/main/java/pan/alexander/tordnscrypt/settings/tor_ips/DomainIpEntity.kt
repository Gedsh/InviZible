package pan.alexander.tordnscrypt.settings.tor_ips

sealed class DomainIpEntity(
    open var isActive: Boolean
)

data class DomainEntity(
    val domain: String,
    val ips: Set<String>,
    override var isActive: Boolean
): DomainIpEntity(isActive)

data class IpEntity(
    val ip: String,
    val domain: String,
    override var isActive: Boolean
): DomainIpEntity(isActive)
