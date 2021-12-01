# InviZible Pro

![GitHub release (latest by date)](https://img.shields.io/github/v/release/gedsh/invizible?style=plastic)
![GitHub Releases](https://img.shields.io/github/downloads/gedsh/invizible/latest/total?color=blue&style=plastic)
[![Translation status](https://hosted.weblate.org/widgets/invizible/-/invizible/svg-badge.svg)](https://hosted.weblate.org/engage/invizible/?utm_source=widget)

### [IzzyOnDroid F-Droid beta version](https://apt.izzysoft.de/fdroid/index/apk/pan.alexander.tordnscrypt)

### [F-Droid stable version](https://f-droid.org/packages/pan.alexander.tordnscrypt.stable/)

### [Google Play stable version](https://play.google.com/store/apps/details?id=pan.alexander.tordnscrypt.gp)

### [Download the latest version from GitHub](https://github.com/Gedsh/InviZible/releases/latest)

## Internet privacy and security on Android

*Keeps privacy, protects your device from dangerous sites, prevents tracking, provides access to blocked online resources*

**InviZible Pro** includes well-known modules such as **DNSCrypt**, **Tor** and **Purple I2P**.
They are used to achieve maximum security, privacy and comfortable use of the Internet.

### DNSCrypt
* Encrypts DNS requests
* Protects against DNS spoofing
* Can block ads *
* Can protect against dangerous and malicious sites *
* Can block "adult" sites *
* Hides visited sites from your provider
* Prevents some types of resource blocks
* Libre software

**Depending on the selected dnscrypt server*
**(Not available in the Google Play version!)**

### Tor
* Encrypts Internet traffic
* Prevents website blocking
* Can provide privacy and anonymity
* Provides access to .onion websites
* Libre software

### Purple I2P
* Encrypts Internet traffic
* Provides access to the hidden anonymous network Invisible Internet and .i2p websites
* Libre software

To start using **InviZible Pro**, all you need is an Android device.
Just run all three modules and enjoy safe and comfortable Internet surfing.
However, if you want to get full control over the app and your Internet connection — no problem!
A large number of simple and professional settings are accessible.
You can flexibly set up **InviZible Pro** itself, as well as its modules — **DNSCrypt**,
**Tor** and **Purple I2P**, to satisfy even the most non-standard requirements.

**InviZible Pro** is an all-in-one app.
 After installation, you can remove all your VPN apps and ad blockers.
 In most cases, **InviZible Pro** works better, is more stable, and faster than free VPNs.
 It does not contain any ads, bloatware and does not spy upon its users.
 
### Why InviZible Pro is better than other similar apps:
* There are no analogs))).
* The only app that provides handy use of **DNSCrypt** on Android.
* It is often more stable than the Orbot app, which also allows use of the Tor network.
* Much more handy than the official **Purple I2P** client.
* Allows easy and flexible setup, which websites and apps can open through **Tor**,
 to bypass blocking.
* Can transform your device, or Android TV set-top box into a secure Wi-Fi access point,
 which can be used by any device, without root access.
* Optimized interface for set-top boxes.
* Replaces various VPNs and other tools to achieve privacy and anonymity.
* Combines **DNSCrypt**, **Tor** and **Purple I2P** features.
* Gratis and libre software.

## Compatibility

**InviZible Pro** can be used both with a rooted or non-rooted device.

Please do visit the [wiki](https://github.com/Gedsh/InviZible/wiki) to find out how to use it.

Depending on the rooting method and device specifics, the app may be incompatible with some Android devices.

## Support

For questions, feature requests and bug-reports, you can use GitHub.

**Official site: [invizible.net](https://invizible.net)**

### International:
 
Telegram channel: [InviZiblePro](https://t.me/InviZiblePro)

Telegram group: [InviZiblePro_Group](https://t.me/InviZiblePro_Group)

### For Russian-speaking users:

Telegram channel: [InviZiblePro](https://t.me/InviZibleProRus)

Telegram group: [InviZiblePro_Group](https://t.me/InviZibleProRus_Group)

Only the latest version of **InviZible Pro** is supported.

There is no support for things that are not directly related to **InviZible Pro**.

There is no support for building and developing things by yourself.

## Contributing

#### Building
To clone a project, use the command:
```bash
git clone --recursive https://github.com/Gedsh/InviZible
```

To build **InviZible Pro**, please use **Android Studio**.

If you see something like this:
_Illegal character in opaque part at index 2: C:\KStore\keystore.properties_

Please comment out this line in the settings.gradle file of the project root, as shown below:

```bash
include ':tordnscrypt', ':filepicker'
project(':filepicker').projectDir = new File('android-filepicker/filepicker')
//Please comment out the line below if you are not the project owner
//project(':tordnscrypt').buildFileName = 'owner.gradle'
```

There is no support offered for building the app yourself, as this is an advanced option.
If you cannot build the app yourself, there are pre-built versions of **InviZible Pro** available [here](https://github.com/Gedsh/InviZible/releases/latest).

#### Translating

[Translate InviZible on Hosted Weblate](https://hosted.weblate.org/engage/invizible/).

[![Translation status](https://hosted.weblate.org/widgets/invizible/-/multi-auto.svg)](https://hosted.weblate.org/engage/invizible/?utm_source=widget)

You can always add other lanuages.

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
and carries no guarantee from the above projects about quality, suitability or anything else.

## Donations

**Bitcoin**: 1GfJwiHG6xKCQCpHeW6fELzFfgsvcSxVUR

**Bitcoin Cache**: qzl4w4ahh7na2z23056qawwdyuclkgty5gc4q8tw88

**USD PAX**: 0xdA1Dd53FE6501140E3Dcd5134323dfccF20aD536

**Ether**: 0xdA1Dd53FE6501140E3Dcd5134323dfccF20aD536

**Stellar**: GBID6I3VYR4NIFLZWI3MEQH3M2H72COC3HQDI5WMYYQGAC3TE55TSKAX

## License

[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.txt)

Copyright © 2019–2021 Garmatin Oleksandr and contributors
invizible.soft@gmail.com

This file is part of **InviZible Pro**.

**InviZible Pro** is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your discretion) any later version.

**InviZible Pro** is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with **InviZible Pro**. If not, see [http://www.gnu.org/licenses/](https://www.gnu.org/licenses/)
