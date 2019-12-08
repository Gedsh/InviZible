package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

import java.util.Objects;

class DNSRelay {
    private String name;
    private String description;
    private boolean checked;

    DNSRelay(String name, String description) {
        this.name = name;
        this.description = description;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSRelay dnsRelay = (DNSRelay) o;
        return checked == dnsRelay.checked &&
                name.equals(dnsRelay.name) &&
                description.equals(dnsRelay.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, checked);
    }
}
