# InviZible Pro

![GitHub release (latest by date)](https://img.shields.io/github/v/release/gedsh/invizible?style=plastic)
![GitHub Releases](https://img.shields.io/github/downloads/gedsh/invizible/latest/total?color=blue&style=plastic)
[![Translation status](https://hosted.weblate.org/widgets/invizible/-/invizible/svg-badge.svg)](https://hosted.weblate.org/engage/invizible/?utm_source=widget)

### [Google Play stable version](https://play.google.com/store/apps/details?id=pan.alexander.tordnscrypt.gp)

### [Download the latest version from Github](https://github.com/Gedsh/InviZible/releases/latest)

### [IzzyOnDroid F-Droid beta version](https://apt.izzysoft.de/fdroid/index/apk/pan.alexander.tordnscrypt)

### [F-Droid stable version](https://f-droid.org/packages/pan.alexander.tordnscrypt.stable/)

## Android application for online privacy and security

*Preserves privacy, prevents tracking, and provides access to restricted and hidden online content*

**InviZible Pro** combines the strengths of **Tor**, **DNSCrypt**, and **Purple I2P** to provide a comprehensive solution for online privacy, security, and anonymity.

### Tor
* Hides user's identity and location
* Defends against traffic analysis and censorship
* Protects online activities from surveillance
* Routes traffic through multiple servers
* Provides access to "onion" websites
* Open-source

### DNSCrypt
* Secures DNS traffic with encryption
* Verifies DNS server legitimacy using cryptographic keys
* Shields against DNS-based attacks like spoofing
* Guards against eavesdropping and DNS query logging
* Can block ads *
* Can protect against dangerous and malicious sites *
* Can block "adult" sites *
* Open-source

**Depending on the selected dnscrypt server*
**(Not available in Google Play version!)**

### Purple I2P
* Provides anonymous communication network
* Conceals users' identities and activities
* Defends against surveillance
* Ensures secure data transmission
* Distributed and self-organizing network
* Provides access to "i2p" websites (eepsites)
* Open-source

To start using **InviZible Pro**, all you need is an Android phone.
Just run all three modules and enjoy safe and comfortable internet surfing. However,
if you want to get full control over the application and your internet connection - no problem!
There is access to a large number of both simple and professional settings.
You can flexibly configure **InviZible Pro** itself, as well as its modules - **DNSCrypt**,
**Tor** and **Purple I2P**, to satisfy the most non-standard requirements.

**InviZible Pro** is an all-in-one application. After installation, you can remove all of your VPN applications and ad blockers.
 In most cases, **InviZible Pro** works better, is more stable, and faster than free VPNs.
 It does not contain any ads, bloatware and does not spy upon its users.
 
### Why InviZible Pro is better than other similar applications:
* Privacy Protection: Guards your online activities.
* Anonymous Browsing: Conceals your identity.
* Secure DNS Encryption: Protects your DNS queries.
* Anonymity Network Integration: Utilizes Tor, DNSCrypt, and Purple I2P.
* Firewall: Safeguards against unauthorized access.
* Access to Restricted Content: Unblocks blocked websites.
* Anti-Tracking Measures: Prevents tracking of your online behavior.
* Hidden Network Access: Connects to "onion" and "i2p" websites.
* Open-Source: Transparent and community-driven.
* User-Friendly Design: Simple and intuitive interface.

## Compatibility

**InviZible Pro** can be used both with a rooted or non-rooted device.

Please visit the [wiki](https://github.com/Gedsh/InviZible/wiki) to find out how to use it.

Depending on the rooting method and device specifics, an application may be incompatible with some android phones.


## Support

For questions, feature requests and bug reports, you can use GitHub.

**Official site: [invizible.net](https://invizible.net)**

### International:
 
Telegram channel: [InviZiblePro](https://t.me/InviZiblePro)

Telegram group: [InviZiblePro_Group](https://t.me/InviZiblePro_Group)

Matrix group: [Matrix](https://matrix.to/#/#invizible-pro-en:matrix.org)

### For Russian-speaking users:

Telegram channel: [InviZiblePro](https://t.me/InviZibleProRus)

Telegram group: [InviZiblePro_Group](https://t.me/InviZibleProRus_Group)

Matrix group: [Matrix](https://matrix.to/#/#invizible-pro-ru:matrix.org)

There is support for the latest version of **InviZible Pro** only.

There is no support for things that are not directly related to **InviZible Pro**.

There is no support for building and developing things by yourself.

## Contributing

#### Building
To clone a project, use the command:
```bash
git clone --recursive https://github.com/Gedsh/InviZible
```

To build **InviZible Pro** please use **Android Studio**.

If you see something like this:
_Illegal character in opaque part at index 2: C:\KStore\keystore.properties_

Please comment lines of the settings.gradle file in the project root, as shown below:

```bash
include ':tordnscrypt', ':filepicker'
project(':filepicker').projectDir = new File('android-filepicker/filepicker')
//Please comment line below if you are not the project owner
//project(':tordnscrypt').buildFileName = 'owner.gradle'
```

It is expected that you can solve build problems yourself, so there is no support for building. 
If you cannot build yourself, there are prebuilt versions of **InviZible Pro** available [here](https://github.com/Gedsh/InviZible/releases/latest).

#### Translating

[Translate InviZible on POEditor](https://poeditor.com/join/project/h6ulNL9gEd).

[Translate InviZible on Hosted Weblate](https://hosted.weblate.org/engage/invizible/).

[![Translation status](https://hosted.weblate.org/widgets/invizible/-/multi-auto.svg)](https://hosted.weblate.org/engage/invizible/?utm_source=widget)

## Attribution

InviZible Pro uses:

* [DNSCrypt](https://github.com/jedisct1/dnscrypt-proxy)
* [Tor](https://www.torproject.org/)
* [Purple I2P](https://github.com/PurpleI2P/i2pd)
* [Chainfire/libsuperuser](https://github.com/Chainfire/libsuperuser)
* [jaredrummler/AndroidShell](https://github.com/jaredrummler/AndroidShell)
* [NetGuard](https://github.com/M66B/NetGuard)
* [Angads25/android-filepicker](https://github.com/Angads25/android-filepicker)
* [meefik/busybox](https://github.com/meefik/busybox)

This product is produced independently from the **Tor®**, **DNSCrypt**, **Purple I2P** software 
and carries no guarantee from The Above Projects about quality, suitability or anything else.

## Donations
**PayPal**: Send to invizible.soft@gmail.com

**Patreon**: https://www.patreon.com/inviziblepro

**BTC**: 1GfJwiHG6xKCQCpHeW6fELzFfgsvcSxVUR

**LTC**: MUSAXkcAvnN1Ytauzeo9bwjVjarUdDHGgk

**BCH**: qzl4w4ahh7na2z23056qawwdyuclkgty5gc4q8tw88

**USDT**: 0xdA1Dd53FE6501140E3Dcd5134323dfccF20aD536

**XLM**: GBID6I3VYR4NIFLZWI3MEQH3M2H72COC3HQDI5WMYYQGAC3TE55TSKAX

**XMR** 82WFzofvGUdY52w9zCfrZWaHVqEDcJH7y1FujzvXdGPeU9UpuFNeCvtCKhtpC6pZmMYuCNgFjcw5mHAgEJQ4RTwV9XRhobX

Please note that the XMR address has changed. The old address is no longer valid.

## License

[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.txt)

Copyright (c) 2019-2024 Garmatin Oleksandr invizible.soft@gmail.com

All rights reserved

This file is part of **InviZible Pro**.

**InviZible Pro** is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your discretion) any later version.

**InviZible Pro** is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with **InviZible Pro**. If not, see [http://www.gnu.org/licenses/](https://www.gnu.org/licenses/)


