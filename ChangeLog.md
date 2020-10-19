**InviZible Pro 2.0.1 (beta 0.2.9)**

* Change Root read/write file operations with no root
* Add correct processing when no bridges available on tor bridges site
* Add correct processing when unable to start module
* Add Polish language

**InviZible Pro beta 0.3.0**
* Material design
* Update day/night theme
* UI/UX improvements
* Fix hotspot on non-standard tether interface name devices
* Update Purple I2P tunnels.conf to add Telegram proxy support
* Add continuous modules status checking when app running in no root mode
* Add wakelock option for additional modules service protection
* No more read/write permission required on first app startup
* Change root installation method with no root
* Change root backup/restore method with no root and file format to zip
* Change save logs method and file format to zip
* Total code refactoring and optimization

**InviZible Pro beta 0.3.1**
* Fix modules update result message
* Fix modules restart after modules updates
* Fix dialog fragments illegal state

**InviZible Pro beta 0.3.2**
* Implemented new full no root mode to use InviZible modules as proxy
* UX improvements
* Bug fixes

**InviZible Pro beta 0.3.3**
* Bug fixes

**InviZible Pro beta 0.3.4**
* The possibilities of the USB modem using were implemented
* Tor and I2P socks proxies tethering were implemented
* Bug fixes and stability improvements

**InviZible Pro beta 0.3.5**
* Improve wifi and usb hotspot;
* Improve modules stopping method

**InviZible Pro beta 0.3.6**
* Bug fixes and stability improvements

**InviZible Pro beta 0.3.7**
* Attempt to rectify stop modules issue on some devices

**InviZible Pro beta 0.3.8**
* Attempt to rectify stop modules issue on some devices
* Add force stop feature on long back button press

**InviZible Pro beta 0.3.9**
* Remove unnecessary options without root.
* Fix modules update feature when they are used with root

**InviZible Pro beta 0.4.0**
* Implement DNSCrypt servers anonymized relays support (clean install required to use)
* Attempt to fix using AfWall with InviZible on android 9, 10.
* Prepare InviZible for Android TV
* Migrate to androidx support library.

**InviZible Pro beta 0.5.0**
* Implement local VPN mode based on NetGuard source code
* Replace internal stericson busybox with meefik.
* Add DNSCrypt options block_unqualified and block_undelegated
* Fix ModulesService stop in root mode.
* Fix DNSCrypt white list option.

**InviZible Pro beta 0.5.1**
* Downgrade target sdk version for compatibility with android Q.
* Remove alpha in status bar icon.
* Fixes.

**InviZible Pro beta 0.5.2**
* Disable VPN mode for android versions below LOLLIPOP.
* Add select apps feature to use or bypass InviZible in VPN mode.
* Add block http feature in VPN mode.

**InviZible Pro beta 0.5.3**
* Implement DNS responses live log for VPN mode.
* Fix use of VPN mode with hotspot.
* Other fixes.

**InviZible Pro beta 0.5.5**
* Implement a new interface for portrait orientation.
* Implement the iptables rules update when the device state changes in root mode.
* Bug fixes and stability improvements.

**InviZible Pro beta 0.5.6**
* Bug fixes and stability improvements.

**InviZible Pro beta 0.5.7**
* Changed the location of modules binaries to the application libs folder to meet android 10 requirements.
* InviZible cannot do OTA modules update anymore, modules will update together with InviZible.
* Assigned different version codes for armv7 and arm64.
* Fixed too frequent iptables rules update
* Updated modules: obfs4proxy, tor v4.2.5
* Clean install recommended. InviZible will ask the modules update with overwriting your modules settings.

**InviZible Pro beta 0.5.8**
* Fixed a bug in which the modules did not start with the "Run modules with root" option activated.
* Device restart required after installation

**InviZible Pro beta 0.5.9**
* Update DNSCrypt to v2.0.38
* Force the app to stop when one of the modules starts with an error

**InviZible Pro beta 0.6.0**
* Implemented the option to add a custom DNSCrypt server.
* Update DNSCrypt to v2.0.39.
* Performance improvements

**InviZible Pro beta 0.6.1**
* Fixed saving logs and backing up to internal storage for android 10.

**InviZible Pro beta 0.6.2**
* Implemented "Refresh rules" option to control iptables rules update on every connectivity change.
* Implemented clear module folder option for Tor and I2P.
* Changed Tor configuration to stay active longer in the background.
* Changes related to the preparation of Google Play and F-Droid versions.

**InviZible Pro beta 0.6.3**
* Implemented Fix TTL option based on the local VPN (root is still required).
* Fixes.

**InviZible Pro beta 0.6.4**
* Bug fixes and stability improvements.

**InviZible Pro beta 0.6.5**
* Fixed modules stop in the background.

**InviZible Pro beta 0.6.6**
* Bug fixes and performance improvements.

**InviZible Pro beta 0.6.7**
* Bug fixes.

**InviZible Pro beta 0.6.8**
* Fixed rare bugs related to tor bridges, hotspot, tor nodes selection.

**InviZible Pro beta 0.6.9**
* Updated Purple I2P to 2.30.0.
* Removed dnswarden servers from the default settings.
* Fixed modules autostart on some devices.
* Added request to force close the application.
* Fixed DNS leak on some devices.
* Other fixes.

**InviZible Pro beta 0.7.0**
* Added snowflake bridges support.
* Replaced the obfs4 bridge binary with a self-build file.
* Minor fixes.

**InviZible Pro beta 0.7.1**
* Fixed Tor bridges for android 4.4.2.
* Added German and Persian languages.
* Added I2P outproxy options.
* Fixed backup when "Run modules with Root" option activated.

**InviZible Pro beta 0.7.2**
* Added Indonesian language.
* Updated languages.
* Added Edit configuration files options.
* Fixed modules ports changing.
* Fixed use of system DNS when "ignore_system_dns" option is disabled.
* Other fixes.

**InviZible Pro beta 0.7.3**
* Updated DNSCrypt version to 2.0.40.
* Updated DNSCrypt configuration file.
* Updated Tor configuration file.
* Fixed Tor countries selection.
* Added notification if private DNS is switched on.
* Fixed app update when the update server is unavailable.

**InviZible Pro beta 0.7.4**
* Updated DNSCrypt version to 2.0.41.
* Fixed real-time logs when log files do not exist.
* Implemented DNSCrypt servers search.

**InviZible Pro beta 0.7.5**
* Updated DNSCrypt to version 2.0.42.
* Updated DNSCrypt configuration file.
* Updated snowflake binary.
* Fixed checking InviZible update manually.
* Implemented snowflake bridge log.
* Implemented option for using different STUN servers with the snowflake bridge.
* Implemented the use of Tor http proxy to request new bridges.

**InviZible Pro beta 0.7.6**
* Implemented IsolateDestAddr IsolateDestPort Tor options.
* Fixed app update and Tor bridges request for android 4.4.2.

**InviZible Pro beta 0.7.7**
* Improved power wakelock and implemented wifi wakelock for Prevent device sleep option and app update.
* Fixed use of system DNS when ignore_system_dns is disabled.
* Fixed use of system DNS with snowflake bridges.
* Fixed restore code of PRO version from backup.
* Improved Internet speed with Fix TTL enabled.

**InviZible Pro beta 0.7.8**
* Implemented iptables execution control.
* Built in ip6tables.
* Updated German language.
* Important fixes.

**InviZible Pro beta 0.7.9**
* Updated Purple I2P to version 2.31.0.
* Improved DNSCrypt real-time log.
* Improved ipv6 handling in VPN mode.
* Implemented local import of DNSCrypt filter files.
* Fixed bug for AfWall to work with 5354 port of DNSCrypt.
* Improved adding Tor bridges.

**InviZible Pro beta 0.8.0**
* Changed DNSCrypt default servers.
* Improved DNSCrypt real-time log.
* Implemented apps list to use with Tor for VPN mode. Now all applications use DNSCrypt if it is running as in Root mode.
* Fixes.

**InviZible Pro beta 0.8.1**
* Updated Tor version to 4.2.7.
* Fixed the use of Tor hidden services and I2P for VPN mode when "Rote All traffic through Tor" option is disabled.
* Ensure compatibility with VPN mode and VPN Hotspot app.

**InviZible Pro beta 0.8.2**
* Added DNS queries real-time log for the tethered device in Root mode with the Fix TTL option enabled.
* Implemented iptables rules update when installing or uninstalling an application.
* Improved request for new Tor bridges.
* Added experimental IPv6 support.
* Updated default DNSCrypt servers.
* Changed default Tor VirtualAddrNetwork to 10.192.0.0/10.
* Renamed None Tor bridges type to Vanilla.
* Fixed the use of IPv6Traffic and PreferIPv6 for the Tor.
* Fixes.

**InviZible Pro beta 0.8.3**
* Fixed application update when the hotspot is turned on.
* Fixed the use of bridges after the application update.
* Fixed bug with incorrect bridge selection.

**InviZible Pro beta 0.8.4**
* Fixed switching between default and custom bridges.
* Improved the use of bridges after the application update.
* Fixed DNS leak when restarting VPN service.
* Fixed using usb modem with a remote hotspot.
* Fixes.

**InviZible Pro beta 0.8.5**
* Fixed "Do Not Use Bridges" option selection.
* Improved real-time logs auto scroll feature.
* Performance improvements.

**InviZible Pro beta 0.8.6**
* Reduced power consumption.
* Fixed Fix TTL feature.

**InviZible Pro beta 0.8.7**
* Fixed Add DNSCrypt server feature.
* Implemented LAN Tethering for devices with a LAN connection.
* Fixed use of DNSCrypt DNS after device reboot or app update with Fix TTL enabled.
* Improved real-time Internet connections log.
* Performance improvements.
* Other fixes.

**InviZible Pro beta 0.8.8**
* Updated Purple I2P to version 2.32.0.
* Updated Purple I2P default configuration.
* Fixed modules logs when Run modules with root option is enabled.
* Prevented too frequent iptables updates.
* Attempt to fix Always on VPN.

**InviZible Pro beta 0.8.9**
* Real-time Internet connections logs fixes and performance improvements

**InviZible Pro beta 0.9.0**
* Attempt to fix Always on VPN.
* Fixed rare bugs with services.

**InviZible Pro beta 0.9.1**
* Real-time Internet connections logs improvements.
* Performance improvements.
* Attempt to fix RemoteServiceException.

**InviZible Pro beta 0.9.2**
* Attempt to fix RemoteServiceException.
* Fixed "Please wait" message when starting modules.
* Fixed service icon does not disappear.
* Real-time Internet connections logs improvements.

**InviZible Pro beta 0.9.3**
* Updated Purple I2P to version 2.32.1.
* Attempting to fix autorun on Android version 9-10

**InviZible Pro beta 0.9.4**
* Updated DNSCrypt to version 2.0.43.
* Attempting to fix autorun on Android version 8-10

**InviZible Pro beta 0.9.5**
* Updated DNSCrypt to version 2.0.44.
* Implemented measurement of Internet speed and traffic in the notification.
* Updated notifications view.
* Fixed language selection.
* Improved application security.
* Implemented compatibility mode for the custom ROMs.
* Add shell script control.

**InviZible Pro beta 0.9.6**
* Implemented allowed and excluded sites for Tor in VPN mode.
* Selected DNSCrypt servers and Tor applications are displayed at the top of the list.
* Added chronometer to notification.
* Fixes related to Always-On VPN.
* Improved compatibility mode.
* Performance improvements.
* Minor bug fixes.

**InviZible Pro beta 0.9.7**
* Added New Tor identity button.
* Added extended crash handling.
* Request to send logs if file cannot be saved.
* Updated logic for Root mode when only Tor is running.
* Disabled stopping modules when updating.

**InviZible Pro beta 0.9.8**
* Implemented real-time logs scaling.
* Fixed app update on Android Q.

**InviZible Pro beta 0.9.9**
* Fixed import of DNSCrypt filter files on Android Q.
* Fixes and UI updates for Backup and Logs on Android Q.

**InviZible Pro beta 1.0.0**
* Attempt to fix Always On VPN when Block connections without vpn is enabled.

**InviZible Pro beta 1.0.1**
* Updated Tor to version 4.4.4-rc.
* Updated obfs4proxy.
* Updated snowflake.
* Updated Tor geoip.

**InviZible Pro beta 1.0.2**
* Updated Purple I2P to version 2.33.0.
* Optimized memory usage.
* Added Finnish language.

**InviZible Pro beta 1.0.3**
* Implemented system-wide socks5 proxy.
* Implemented and fixed modules proxy settings.
* Fixed saving files with direct configuration editor.
* Performs a full Tor restart in case of using bridges.

**InviZible Pro beta 1.0.4**
* Implemented multi-user support.
* Implemented xtables lock for iptables.
* Implemented default Tor bridges update.
* Implemented system-wide socks5 proxy for Root mode with Fix TTL enabled.
* Improved applications selection.
* Improved real-time Internet connections log.
* Improved iptables rules for Root mode.
* Updated default Tor bridges.
* Updated snowflake Tor bridge.
* Fixed selection of DNSCrypt anonimized relays after device rotation.
* Fixed import of DNSCrypt rules when site name contains _
* Fixed changing I2P settings after rotating device.
* Fixed language selection for android Q.
* Removed Quad9 servers from anonimized relays broken implementation.
* Optimized the speed of Internet responses in VPN mode.

**InviZible Pro beta 1.0.5**
* Added script for building Tor for Android from source using Gitlab CI / CD.
* Updated Tor to version 4.4.5.
* Changed default I2P outproxy address.
* Updated German language.
* Fixed real-time connections log when default DNS servers are unavailable.
* Fixed bridges selection after activity recreate.
* Fixed activity not found exception.

**InviZible Pro beta 1.0.6**
* Implemented ARP spoofing and rogue DHCP attacks detection.
* Bug fixes.

**InviZible Pro beta 1.0.7**
* Improved ARP spoofing and rogue DHCP attacks detection when always-on VPN is active.
* Updated Persian language.
* Minor fixes.

**InviZible Pro beta 1.0.8**
* Added bypass Tor option for LAN and IANA addresses.
* Added Purple I2P build script.
* Updated Purple I2P.
* Updated Tor.
* Updated snowflake.
* Updated Persian language.
* Removed unnecessary lines from the manifest.
* Fixed usb modem and wifi hotspot detection.
* Implemented auxiliary detection of enabling hotspot.
* Fixed application usage when uid0 is blocked by firewall.
* Fixed reading files content when using Run modules with Root.
* Other fixes.
