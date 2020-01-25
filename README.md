# InviZible Pro

![GitHub release (latest by date)](https://img.shields.io/github/v/release/gedsh/invizible?style=plastic)
![GitHub Releases](https://img.shields.io/github/downloads/gedsh/invizible/latest/total?color=blue&style=plastic)

## [Download the latest release](https://github.com/Gedsh/InviZible/releases/latest)

## Android application for Internet privacy and security

*Keep privacy, protect your device from dangerous sites, block ads, get access to blocked Internet resources*

**InviZible Pro** includes well-known modules such as **DNSCrypt, Tor, Purple I2P**.
They are used to achieve maximum security, privacy and comfortable use of the Internet.

Start three modules of **InviZible Pro**, and you will transform from an ordinary user to the invisible, which is very difficult to track, block, impose on you unnecessary products and services in the form of ubiquitous advertising.

### DNSCrypt
* Encrypts DNS requests
* Protects against DNS spoofing
* Blocks ads *
* Protects against dangerous and malicious sites *
* Can block "adult" sites *
* Hides visited sites from your provider
* Prevents some types of resource blocks
* Open-source

**Depending on the selected dnscrypt server*

### Tor
* Encrypts Internet traffic
* Prevents sites blocking
* Can provide privacy and anonymity
* Provides access to "onion" sites
* Open-source

### Purple I2P
* Encrypts Internet traffic
* Provides access to the hidden anonymous network Invisible Internet and "i2p" sites
* Open-source

To start using **InviZible Pro**, it is enough to have an android phone.
Just run all three modules and enjoy safe and comfortable Internet surfing. However,
if you want to get full control over the application and your Internet connection - no problem!
Provided access to a large number of both simple and professional settings.
You can flexibly configure **InviZible Pro** itself, as well as its modules - **DNSCrypt**,
**Tor** and **Purple I2P**, to satisfy the most non-standard requirements.

**InviZible Pro** is an all-in-one application. After installation, you can remove all of your VPN applications and ad blockers.
 In most cases, **InviZible Pro** works better, more stable, faster than free VPNs.
 It does not contain ads, bloatware code and does not spy upon the users.
 
### Why InviZible Pro is better than other similar applications:
* There are no analogs)))
* The only application that provides handy use of **DNSCrypt** on Android.
* It is often more stable than the Orbot application, which also uses the Tor network.
* Much more handy than the official **Purple I2P** client
* Qualitatively removes most ads, thereby saving Internet traffic.
* You can configure parental controls to protect children from "adult" sites
* Allows you to easily and flexibly configure which sites and applications will open through **Tor**,
 for anonymity or bypassing blocks
* Can transform your phone, or Android TV set-top box into a Wi-Fi access point - safe and without ads,
 which can be used by any phone, without root access
* Optimized interface for set-top boxes
* Replaces several apps - various VPNs, ad blockers, other tools to achieve privacy and anonymity.
* Successfully combines **DNSCrypt**, **Tor**, **Purple I2P** features; to visit sites anonymously and without advertising, for example
* Free and open-source

## Compatibility

**InviZible Pro** can be used both with a rooted and non-rooted device.
Please visit the [wiki](https://github.com/Gedsh/InviZible/wiki) to find out how to use it.

Depending on the rooting method and device specifics, an application may be incompatible with some android phones.


## Support

For questions, feature requests and bug reports, you can use GitHub.

### International:
 
Telegram channel: [InviZiblePro](https://t.me/InviZiblePro)

Telegram group: [InviZiblePro_Group](https://t.me/InviZiblePro_Group)

### For Russian-speaking users:

Telegram channel: [InviZiblePro](https://t.me/InviZibleProRus)

Telegram group: [InviZiblePro_Group](https://t.me/InviZibleProRus_Group)

Site [invizible.net](https://invizible.net/ru) for Russian-speaking users at the moment.

There is support for the latest version of **InviZible Pro** only.

There is no support for things that are not directly related to **InviZible Pro**.

There is no support for a building and developing things by yourself.

## Contributing

#### Building

To build **InviZible Pro** please use **Android Studio**.

If you see something like this:
_Illegal character in opaque part at index 2: C:\KStore\keystore.properties_

Please comment lines of the settings.gradle file in the project root, as shown below:

```bash
include ':tordnscrypt'
//Please comment below if you are not the project owner
//project(':tordnscrypt').buildFileName = 'owner.gradle'
```

It is expected that you can solve build problems yourself, so there is no support for building. 
If you cannot build yourself, there are prebuilt versions of **InviZible Pro** available [here](https://github.com/Gedsh/InviZible/releases/latest).

For application signing, you can use your signature. But without the original signature, you will see a warning about this.
All features will continue to work as with original signature in any case.

#### Translating

If you would like to translate this application into other languages,
please send an email to the developer [invizible.soft@gmail.com](mailto:invizible.soft@gmail.com)

## Attribution

InviZible Pro uses:

* [DNSCrypt](https://github.com/jedisct1/dnscrypt-proxy)
* [Tor](https://www.torproject.org/)
* [Purple I2P](https://github.com/PurpleI2P/i2pd)
* [Chainfire/libsuperuser](https://github.com/Chainfire/libsuperuser)
* [jaredrummler/AndroidShell](https://github.com/jaredrummler/AndroidShell)
* [Angads25/android-filepicker](https://github.com/Angads25/android-filepicker)
* [meefik/busybox](https://github.com/meefik/busybox)

This product is produced independently from the **Tor®**, **DNSCrypt**, **Purple I2P** software 
and carries no guarantee from The Above Projects about quality, suitability or anything else.

## License

[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.txt)

Copyright (c) 2019-2020 Garmatin Oleksandr invizible.soft@gmail.com

All rights reserved

This file is part of **InviZible Pro**.

**InviZible Pro** is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your discretion) any later version.

**InviZible Pro** is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with **InviZible Pro**. If not, see [http://www.gnu.org/licenses/](https://www.gnu.org/licenses/)


