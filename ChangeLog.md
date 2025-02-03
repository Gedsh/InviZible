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

**InviZible Pro beta 1.0.9**
* Updated snowflake.
* Updated German language
* Changed method of defining own uid to fix backup and restore using third party applications.
* Fixed using a proxy with the FixTTL option enabled.
* Other minor fixes.

**InviZible Pro beta 1.1.0**
* Updated Purple I2P to version 2.34.0
* Updated Purple I2P default configuration as ntcp is no longer supported.
* Build Purple I2P from source including all dependencies.
* Updated Tor with new dependencies.

**InviZible Pro beta 1.1.1**
* Implemented a firewall for VPN mode.
* Updated Indonesian language.
* Updated snowflake.

**InviZible Pro beta 1.1.2**
* Fixed app crash when installing new app.
* Fixed display of firewall menu item in Root or Proxy mode.
* Don't show notification when updating system app or without Internet permission.
* Prevent apps without Internet permission from being shown in app lists.

**InviZible Pro beta 1.1.3**
* Don't show firewall notification when updating user apps.
* Fixed inconsistencies in the firewall UI in rare cases.
* NTP can bypass Tor if allowed by firewall rules.
* Fixed Proxy port does not change.

**InviZible Pro beta 1.1.4**
* Optimized Tor bridges handling and selection.
* Updated Tor.

**InviZible Pro beta 1.1.5**
* Improved real-time Internet connections log.
* Do not lock the interface if an empty password is used.
* Minor fixes and improvements.

**InviZible Pro beta 1.1.6**
* Updated Purple I2P to version 2.35.0
* Implemented patches to change the default configuration after updating the application.
* Updated default DNSCrypt configuration to use v3 update sources.
* Enabled openssl enable-ec_nistp_64_gcc_128 for arm64 version to improve speed.
* Added French language.
* Minor fixes and improvements.

**InviZible Pro beta 1.1.7**
* Tor apps selection improvements.
* Updated obfs4proxy (fixed meek_lite bridge).
* Updated snowflake.
* Updated German language.
* Fixed crash on android 4.4.2.
* Fixed rare bugs.

**InviZible Pro beta 1.1.8**
* Implemented option to allow/block the Internet for newly installed applications.
* Implemented sorting and filtering for the Tor apps selection option.
* Changed the way of Tor, DNSCrypt and I2P starting.
* Added tooltips to firewall.
* Improved handling of modules log files.
* Limited size of real-time Internet connections log.
* Updated French language.
* Fixed rare ANR when stopping DNSCrypt.
* Other fixes and improvements.

**InviZible Pro beta 1.1.9**
* Added notification if private DNS or proxy is enabled.
* Implemented automatic Tor geoip update.
* Fixed allowing Internet for newly installed apps by default.
* Updated Tor.
* Updated snowflake.
* Updated Tor geoip.
* Updated French language.

**InviZible Pro beta 1.2.0**
* Updated Tor to version 4.5.3.
* Updated DNSCrypt to version 2.0.45.
* Updated DNSCrypt default configuration.
* Implemented DNS rebinding protection for VPN mode.
* Added dialog to confirm mode change.
* Fixed duplicate DNSCrypt rules when editing.
* Fixed import DNSCrypt rules for android 10, 11.

**InviZible Pro beta 1.2.1**
* Optimized using iptables in Root mode.
* Improved websites handling for the Tor Exclude/Select websites feature.
* Improved backup/restore feature.
* Implemented reset settings feature.
* Improved save logs feature.
* Improved Fix TTL feature.
* Improved import DNSCrypt rules feature.
* Updated default DNSCrypt configuration.
* Added Japanese language.
* Updated French language.
* Minor fixes and optimizations.

**InviZible Pro beta 1.2.2**
* Improved app update feature.
* Updated Purple I2P.
* Updated Purple I2P default configuration.
* Fixed "Clat" selection for use with Tor.
* Explicitly set unmetered connection for Android Q.
* Added Chinese translation.
* Updated German translation.
* Updated French translation.

**InviZible Pro beta 1.2.3**
* Updated Tor to version 4.5.6.
* Updated Purple I2P to version 2.36.0.
* Updated snowflake.
* Workaround to allow updates to be installed from a removable SD card.
* Added Spanish translation.
* Minor fixes and optimizations.
* A lot of internal changes to start using a clean architecture.

**InviZible Pro beta 1.2.4**
* Updated Tor.
* Fixed displaying an error message even if there were no connection problems.
* Bugs fixes and stability improvements.

**InviZible Pro beta 1.2.5**
* Updated Tor to version 4.5.7.
* Updated Purple I2P to version 2.37.0.
* Improved user interface interactivity.
* Provided a universal (armv7a and arm64) build for the f-droid version.
* Minor fixes and optimizations.

**InviZible Pro beta 1.2.6**
* Implemented the "Clean module folder" feature for DNSCrypt.
* Fixed modules(Tor, DNSCrypt, I2P) show starting, even if they are truly running.
* Fixed "Route All through Tor" when using hotspot and only Tor is running
* Minor fixes and optimizations.

**InviZible Pro beta 1.2.7**
* Fixed "Run modules with Root" option (Do not enable it unless you really need to).
* Optimized memory usage.
* Minor fixes and optimizations.

**InviZible Pro beta 1.2.8**
* Fixed bugs related to the user interface and auto-start.

**InviZible Pro beta 1.2.9**
* Improved DNS handling.
* Using better compiler optimizations.
* Updated Tor.
* Updated Purple I2P.
* Updated obfs4proxy.
* Updated snowflake.
* Fixed firewall sliding back to top when restoring an app from recent apps.

**InviZible Pro beta 1.3.0**
* Fixed - START button does not change its state when only Tor or I2P is running.
* Implemented non ASCII DNS handling.
* Updated Purple I2P.
* Minor bug fixes and optimizations.

**InviZible Pro beta 1.3.1**
* Always use a full Tor restart when pressing the new identity button, which previously only happened with Tor bridges.
* Fixed DNS leak when Android private DNS is in automatic mode.
* Updated Tor.
* Updated Snowflake.
* Fixed UPNP for Purple I2P.

**InviZible Pro beta 1.3.2**
* Show apps that are allowed Internet access at the top of the list in the firewall.
* Updated Tor to version 4.6.3.
* Updated Purple I2P to version 2.38.0.
* Switch snowflake front domain and host to fastly CDN.

**InviZible Pro beta 1.3.3**
* Revert back to Tor 4.5.x versions so that v2 onion services can be used.

**InviZible Pro beta 1.3.4**
* Updated Tor to version 4.5.9.
* Updated Tor Snowflake bridge to version 1.0.0.
* Updated Purple I2P.
* Block DNS over TLS and google DNS when DNSCrypt ignore_system_dns is enabled.
* Restart InviZible when Tor fails in the background.
* Attempt to fix wifi access point on android 11.
* Updated firewall view.
* Updated Spanish translation.
* Updated Portuguese-BR translation.
* Minor bug fixes and optimizations.

**InviZible Pro beta 1.3.5**
* Updated DNSCrypt to version 2.1.0.
* Updated Tor to version 4.5.10.
* Updated Tor Snowflake bridge to version 1.1.0.
* Updated Purple I2P.
* Updated firewall view.
* Updated Polish translation.

**InviZible Pro beta 1.3.6**
* Updated Purple I2P to version 2.39.0.
* Changed implementation of saving app settings.
* Added HardwareAccel option to Tor settings.
* Updated Tor default bridges.
* Fixed showing recently installed apps at the top of the firewall apps list.
* Minor bug fixes and optimizations.

**InviZible Pro beta 1.3.7**
* Fixed updating of the main screen toolbar depending on the mode.
* Fixed firewall view.
* Fixed delayed saving of settings in some cases.
* Fixed display of warning about arp spoofing attack if app is launched.
* Minor bug fixes and optimizations.

**InviZible Pro beta 1.3.8**
* Updated DNSCrypt to version 2.1.1.
* Added tiles to Android Quick Settings for starting/stopping modules.
* Preparing the app for Android 12.

**InviZible Pro beta 1.3.9**
* Fixed crash when opening some settings.

**InviZible Pro beta 1.4.0**
* Added current connection status to the notification.
* Optimized application behavior on unstable networks.
* Improved websites handling for the Tor Exclude/Select websites feature.
* Improved Android Quick Settings tiles for starting/stopping modules.
* Improved ARP Spoofing detection.
* Lots of internal code optimizations.

**InviZible Pro beta 1.4.1**
* Improved current connection status in the notification.
* Improved Tor Exclude/Select websites feature.
* Fixed internet sharing in Root mode on android 11.
* Fixes and optimizations.

**InviZible Pro beta 1.4.2**
* Fixed Restore Settings feature.
* Minor bug fixes and optimizations.

**InviZible Pro beta 1.4.3**
* Optimized battery usage when network connection is unavailable.
* Added tile to Android Quick Settings for changing Tor identity.
* Fixed using Bypass LAN Addresses with socks5 proxy.

**InviZible Pro beta 1.4.4**
* Fixed internet sharing in Root mode on android 11.
* Improved firewall.
* Minor fixes.

**InviZible Pro beta 1.4.5**
* Updated Tor to version 4.6.8 (onion v2 services are no longer supported).
* Updated Tor snowflake bridge to version 2.0.1.
* Updated Purple I2P to version 2.40.0.
* Optimized battery usage.
* Improved firewall.
* Fixed using onion websites with DNSCrypt force_tcp enabled.
* Improved the Arp Spoofing attack detector.

**InviZible Pro beta 1.4.6**
* Updated Tor to version 4.6.9.
* Updated Tor Snowflake bridge.
* Updated Tor geoip and default bridges.
* Added the option to firewall settings to display all applications regardless of app internet permission.
* Added the option to select Snowflake communication via AMP or Fastly.
* Improved firewall.
* Improved traffic and speed statistics in notification.
* Fixes and optimizations.

**InviZible Pro beta 1.4.7**
* Pausing InviZible instead of completely shutting down when using another VPN.
* Improved internet connection checking when only DNSCrypt is running and Use socks5 proxy enabled.
* Added Greek translation.
* Updated French translation.
* A lot of fixes and optimizations.

**InviZible Pro beta 1.4.8**
* Optimized modules logs parser.
* Fixed and improved Fix TTL feature.
* Updated German translation.
* Minor fixes.

**InviZible Pro beta 1.4.9**
* Fixed crash when device comes out of idle mode.

**InviZible Pro beta 1.5.0**
* Implemented a kill switch for Root mode.
* Improved internet connection checking when only DNSCrypt is running and Use socks5 proxy enabled.
* Updated Greek translation.

**InviZible Pro beta 1.5.1**
* Internet is disconnected before the device is turned off if the Kill switch is enabled in Root mode.
* Fixes and optimizations.

**InviZible Pro beta 1.5.2**
* Fixed infinite connecting status in notification.
* Fixed display of notifications on Android 4.4.2
* Fixed display of a kill switch notification when an ARP spoofing attack is detected.
* Updated German translation.
* Minor fixes and optimizations.

**InviZible Pro beta 1.5.3**
* Updated Tor to version 4.6.10.
* Fixed infinite connecting status in notification.
* Minor fixes and optimizations.

**InviZible Pro beta 1.5.4**
* Fixed Purple I2P not starting on some devices.
* Fixed internet sharing on some Samsung devices.
* Fixed displaying a firewall notification to control app connection after it is installed.

**InviZible Pro beta 1.5.5**
* Updated Tor.
* Updated Tor obfs4proxy to version 0.0.12.
* Updated Tor snowflake to version 2.1.0.
* Updated Purple I2P to version 2.41.0.
* Preventing frequent updating of iptables rules on unstable networks.
* Fixed crashes on some MIUI phones.
* Updated Persian translation.

**InviZible Pro beta 1.5.6**
* Implemented bridges ping check if Tor is stopped.
* Revert back to Tor obfs4proxy version 0.0.11.
* Minor fixes.

**InviZible Pro beta 1.5.7**
* Updated Tor.
* Implemented Tor bridges sorting and swipe to refresh bridges ping.
* Implemented the use of Tor relays as default vanilla bridges.
* DNSCrypt force_tcp is enabled by default.
* Updated default DNSCrypt servers.
* Disable ntcp2 published option of Purple I2P if notransit is enabled.
* Minor fixes.

**InviZible Pro beta 1.5.8**
* Improved root commands execution.
* Fixed active bridges sorting.
* Added Turkish translation.
* Updated Spanish translation.
* Updated Indonesian translation.
* Fixes and optimizations.

**InviZible Pro beta 1.5.9**
* Implemented firewall for Root mode.
* Optimized memory usage in VPN mode.
* Fixes and optimizations.

**InviZible Pro beta 1.6.0**
* Root firewall fixes and optimizations.
* Fixed using Multi-user support on some devices.
* Tor restarts if an internet connection cannot be established within one minute.

**InviZible Pro beta 1.6.1**
* Updated Tor to version 4.7.6.
* Updated Tor snowflake bridge.
* Updated Indonesian translation.

**InviZible Pro beta 1.6.2**
* Fixed using Root firewall when Refresh rules option is disabled.
* Fixed the ARP spoofing detector false positives on some devices.
* Minor fixes.

**InviZible Pro beta 1.6.3**
* Added option to wait for an xtables lock to prevent concurrent modification of the iptables rules.
* Fixed internet blocking when using root firewall on some roms.
* Improved app restart after a crash or after the app was killed due to low device memory.
* Improved root commands execution.
* Updated Portuguese-BR translation.

**InviZible Pro beta 1.6.4**
* Updated Tor to version 4.7.7.
* Updated Tor snowflake bridge.
* Improved requesting new Tor bridges.
* Improved checking and downloading app updates.
* Fixed compatibility with android 4.4.2.
* Lots of internal optimizations.
* Minor fixes.

**InviZible Pro beta 1.6.5**
* Implemented real-time connection logs for Root mode.
* Improved real-time connection logs for VPN mode.
* Fixed saving changes when using direct editing of module configuration files.
* Lots of internal optimizations.
* Minor fixes.

**InviZible Pro beta 1.6.6**
* Updated Purple I2P to version 2.42.0.
* Added "Internet connectivity check" option to the firewall to allow connection checks for system applications.
* Added a switch to enable/disable real-time connection logs.
* Improved real-time connection logs for Root mode.
* Improved real-time connection logs for VPN mode.

**InviZible Pro beta 1.6.7**
* Updated Tor.
* Updated Tor snowflake bridge to version 2.2.0.
* Updated Tor obfs4proxy to version 0.0.13.
* Improved real-time connection logs for Root mode.
* The "Run modules with Root" option is no longer supported and will be hidden in the Common Settings.
* Minor fixes and optimizations.

**InviZible Pro beta 1.6.8**
* Updated Purple I2P to version 2.42.1.
* Updated Tor snowflake bridge.
* Bridge ping color changes based on ping value.
* Fixed DNSCrypt version detection.
* Minor optimizations.

**InviZible Pro beta 1.6.9**
* DNSCrypt fork is used, which minimizes plaintext DNS queries for bootstrap.
* Check Tor bridges ping through proxy if enabled.
* Fixed crash on anroid 4.4.2 when opening some screens.
* Minor fixes and optimizations.

**InviZible Pro beta 1.7.0**
* Updated Tor to version 4.7.8.
* Updated Tor snowflake bridge.
* Updated DNSCrypt.

**InviZible Pro beta 1.7.1**
* Updated DNSCrypt to version 2.1.2.
* Updated Tor.
* Updated Tor snowflake bridge to version 2.3.0.
* Optimized armv7a build.
* Minor fixes and optimizations.

**InviZible Pro beta 1.7.2**
* Updated DNSCrypt.
* Added http3 option to DNSCrypt settings (DoH3, HTTP over QUIC).
* Updated Tor to version 4.7.10.
* Updated Tor geoip.
* Fixes and optimizations.

**InviZible Pro beta 1.7.3**
* Updated Purple I2P to version 2.43.0.
* Added SSU2 option to Purple I2P settings.
* Tor restarts if the internet connection is lost.

**InviZible Pro beta 1.7.4**
* Fixed requesting Tor bridges via the app interface.
* Removed bridge types that are no longer available on the torproject website.
* Added Italian translation.

**InviZible Pro beta 1.7.5**
* Fixed display of notifications on Android 13.
* Fixed requesting Tor bridges on Android 4.4.2.
* Minor fixes and optimizations.

**InviZible Pro beta 1.7.6**
* Updated Tor.
* Updated Tor snowflake bridge.
* Updated Tor obfs4proxy to version 0.0.14.
* Use default network DNS to test connectivity if only DNSCrypt is running.
* Fixed Tor bridges requesting dialog when font size is too large.

**InviZible Pro beta 1.7.7**
* Updated Tor to version 4.7.11.
* The app will not block port 80 on the LAN if the corresponding option is enabled.
* Fixed compatibility with Android 4.4.2.

**InviZible Pro beta 1.7.8**
* Updated Tor to version 4.7.12.
* Updated Tor snowflake bridge to version 2.4.1.
* Updated Tor geoip.
* Updated Purple I2P to version 2.45.0.
* Removed no longer supported I2P SSU option.
* Do not block related, established connections while updating iptables rules in Root mode.
* Fixes and optimizations.

**InviZible Pro beta 1.7.9**
* Updated Tor to version 4.7.13.
* Updated Tor snowflake bridge to version 2.5.1.
* Updated Purple I2P to version 2.45.1.
* Added display of Tor bridges country.

**InviZible Pro beta 1.8.0**
* Updated DNSCrypt to version 2.1.3.
* Fixed starting app modules using tiles.
* Fixed restoring a backup on Android 13.

**InviZible Pro beta 1.8.1**
* Updated DNSCrypt to version 2.1.4.
* Updated default DNSCrypt servers.
* Updated default Tor bridges.

**InviZible Pro beta 1.8.2**
* Updated Purple I2P to version 2.47.0.
* Fixed using of IPv6 protocol with DNSCrypt.
* Fixes and optimizations.

**InviZible Pro beta 1.8.3**
* Added full ipv6 support for DNSCrypt in VPN mode.
* Added full ipv6 support for Tor in VPN mode.
* Fixed a crash on Android 4.4.2 when installing a new app.

**InviZible Pro beta 1.8.4**
* Fixed requesting for new Tor bridges.
* Allowed requesting for ipv6 bridges in addition to ipv4 for default vanilla bridges.
* Allowed requesting ipv6 bridges from torproject database.
* Added IPv6 support for auxiliary app's features.
* Added Portuguese translation.
* Updated German translation.
* Updated French translation.

**InviZible Pro beta 1.8.5**
* Fixed requesting for new Tor bridges.
* Updated Tor.
* Updated DNSCrypt.

**InviZible Pro beta 1.8.6**
* Updated built-in snowflake and meek_lite Tor bridges.
* IPv6 is enabled by default for app modules.
* Fixes and optimizations.

**InviZible Pro beta 1.8.7**
* Updated Purple I2P to version 2.48.0.
* Added support for the Tor Conjure bridge.
* Fixed using Fastly server with Snowflake on Android versions below 8.
* DNSCrypt shows that it is running if at least one server is ready.
* Fixes and optimizations.

**InviZible Pro beta 1.8.8**
* Updated Tor Snowflake bridge to version 2.6.0.
* Improved Tor Conjure bridge connection stability.
* Fixes and optimizations.

**InviZible Pro beta 1.8.9**
* Updated Tor Conjure bridge.
* Added option to separately activate and configure socks5 proxy.
* Fixed adding Tor bridges without fingerprint.
* Fixed using X bandwidth with Purple I2P.

**InviZible Pro beta 1.9.0**
* Fixed Tor Snowflake bridge failure on Android 11 and above.

**InviZible Pro beta 1.9.1**
* Improved Tor Conjure bridge connection stability.
* Fixed adding meek_lite bridges with a domain that contains a hyphen.

**InviZible Pro beta 1.9.2**
* Improved Tor Conjure bridge censorship resistance.
* Improved meek_lite bridge censorship resistance.
* Fixed modules not installed on initial app startup.
* Updated German translation.
* Fixes and optimizations.

**InviZible Pro beta 1.9.3**
* Fixed Tor not starting after enabling Allow Tor Tethering.
* Fixes and optimizations.

**InviZible Pro beta 1.9.4**
* Updated Tor to version 4.8.3.
* Updated Tor geoip file.
* Updated Tor lyrebird obfuscating proxy.
* Updated Tor Conjure bridge.
* Updated Tor Snowflake bridge.
* Updated DNSCrypt.
* Fixed app can't start modules when ports are busy.
* Fixes and optimizations.

**InviZible Pro beta 1.9.5**
* Updated DNSCrypt to version 2.1.5.
* Added support for the Tor WebTunnel bridge.

**InviZible Pro beta 1.9.6**
* Improved Tor WebTunnel bridge censorship resistance.
* Improved Tor Snowflake bridge connection stability.
* Fixed adding meek_lite bridges with utls option.
* Fixed display of ping and country for some WebTunnel bridges.
* Optimized memory usage.

**InviZible Pro beta 1.9.7**
* Updated Tor to version 4.8.4.
* Added a suggestion to reset the module settings in case of a startup failure.
* Fixes and optimizations.

**InviZible Pro beta 1.9.8**
* Updated Tor to version 4.8.5.
* Implemented customizable SNI for Tor connections.
* Using Chromium's TLS fingerprint instead of Firefox's obsolete TLS fingerprint for Tor connections.
* Updated Tor WebTunnel bridge.
* Fixed ANR on Android 4.4.2.
* Fixed requesting new bridges button not responding in rare cases.
* Added Ukrainian translation.

**InviZible Pro beta 1.9.9**
* Added TrackHostExits option to Tor settings.
* Fixed the Tor WebTunnel bridge failure to connect on Android versions lower than 8.
* Fixes and optimizations.

**InviZible Pro beta 2.0.0**
* Updated Tor to version 4.8.7.
* Updated Purple I2P to version 2.49.0.
* Fixed SnowFlake bridge failed to connect.
* Fixed adding new SnowFlake bridges.
* Fixed Conjure bridge failed to connect.
* Fixes and optimizations.

**InviZible Pro beta 2.0.1**
* Updated Tor to version 4.8.8.
* Updated Tor Snowflake bridge to version 2.7.0.
* Updated Tor Lirebird, WebTunnel, Conjure bridges.
* Updated DNSCrypt.
* Updated default DNSCrypt settings.
* Fixed on/off firewall button is not displayed.
* Fixes and optimizations.

**InviZible Pro beta 2.0.2**
* Updated Tor to version 4.8.10.
* Updated Purple I2P to version 2.50.1.
* Fixed mode switching on some devices.
* Fixes and optimizations.

**InviZible Pro beta 2.0.3**
* Updated Purple I2P to version 2.50.2.
* Improved real-time connection logs.
* Fixes and optimizations.

**InviZible Pro beta 2.0.4**
* Updated DNSCrypt.
* Updated default DNSCrypt servers.
* Updated Tor Snowflake bridge to version 2.8.1.
* Updated Tor Lirebird bridge.
* Updated Polish translation.
* The app icon and color scheme have been changed.
* Improved handling of IPv6-only networks.
* Fixes and optimizations.

**InviZible Pro beta 2.0.5**
* The app icon and color scheme have been changed.
* Added monochrome adaptive icon.
* Fixed the appearance of the DNSCrypt rules import dialog.
* Updated translations.
* Fixes and optimizations.

**InviZible Pro beta 2.0.6**
* Various user interface fixes and improvements.
* Improved switching speed between networks.
* Fixes and optimizations.

**InviZible Pro beta 2.0.7**
* Various fixes to save battery power when network is unavailable.
* Added DormantClientTimeout option to Tor settings.
* Updated Tor Snowflake bridge to version v2.9.0.
* Fixes and optimizations.

**InviZible Pro beta 2.0.8**
* Updated DNSCrypt to save battery power when the network is unavailable.
* Further improvements to save battery power when the network is unavailable.
* Fixes and optimizations.

**InviZible Pro beta 2.0.9**
* Updated Tor Snowflake bridge to version v2.9.1.
* Added CDN77 and Azure Snowflake rendezvous.
* Added exclude UDP from Tor and Bypass app options.
* Updated translations.
* Fixes and optimizations.

**InviZible Pro beta 2.1.0**
* Improved Tor bridges screen.
* Improved firewall screen.
* Improved Tor apps screen.
* Improved real-time connection logs.
* Fixed Tor Snowflake bridge.

**InviZible Pro beta 2.1.1**
* Updated Tor Snowflake bridge.
* Fixed previously updated screens for use with Android TV.
* Fixed and improved script control.
* Updated German and Spanish translations.
* Fixes and optimizations.

**InviZible Pro beta 2.1.2**
* Updated Tor.
* Updated Tor Snowflake bridge to version v2.9.2.
* Attempt to improve Snowflake bridge censorship resistance.
* Added Amazon Snowflake rendezvous.
* Updated Tor Lirebird and WebTunnel bridges.
* Fixes and optimizations.

**InviZible Pro beta 2.1.3**
* Optimized performance and memory usage in VPN mode.
* Fixes and optimizations.

**InviZible Pro beta 2.1.4**
* Fixed adding Tor WebTunnel bridges.
* Updated Polish translation.
* Updated Chinese translation.

**InviZible Pro beta 2.1.5**
* Optimized performance and memory usage in VPN mode.
* Improved handling of IPv6 networks.
* Improved handling of the ICMP protocol.
* Improved handling of resources in the local network.
* Fixes and optimizations.

**InviZible Pro beta 2.1.6**
* Updated Tor to version 4.8.11.
* Updated Purple I2P to version 2.51.0.
* ipv4only.arpa was excluded from the rebinding detection as a legitimate domain.
* Show a warning when blocking the internet for critical system apps in the firewall.
* DNSCrypt server settings have been completely reworked.
* Fixes and optimizations.

**InviZible Pro beta 2.1.7**
* Fixed Telegram messenger always connecting.

**InviZible Pro beta 2.1.8**
* Added DNSCrypt ODoH servers support.
* Updated default Tor bridges.
* App shows IP and country for all types of bridges.
* Improved handling of IPv6-only networks.
* Performance improvements.
* Fixes and optimizations.

**InviZible Pro beta 2.1.9**
* Updated Purple I2P with the latest patch to mitigate the ongoing DoS attack on the I2P network.

**InviZible Pro beta 2.2.0**
* Updated Purple I2P with the latest patch to mitigate the ongoing DoS attack on the I2P network.
* Display the IPv6 network status for Purple I2P.
* Sort tabs depending on running modules.
* Fixed WebTunnel connection via IPv6.
* Fixes and optimizations.

**InviZible Pro beta 2.2.1**
* Revised app settings.
* Updated translations.
* Fixes and optimizations.

**InviZible Pro beta 2.2.2**
* Fascist Firewall automatically turns off when inappropriate bridges are selected.
* Fixed display of the app list on some phones.
* Updated Spanish and Chinese translations.
* Fixes and optimizations.

**InviZible Pro beta 2.2.3**
* Updated Tor.
* Updated Tor lyrebird obfuscating proxy.
* Updated DNSCrypt.
* Fascist Firewall option is blocked if inappropriate bridges are selected.
* Updated Spanish and Turkish translations.
* Fixes and optimizations.

**InviZible Pro beta 2.2.4**
* Downgraded Android material library which causes multiple crashes.

**InviZible Pro beta 2.2.5**
* Updated Purple I2P to version 2.52.0.
* Updated DNSCrypt.
* Updated Tor Snowflake bridge.
* Updated Tor Lyrebird obfuscating proxy.
* Updated Chinese and Persian translations.
* Fixes and optimizations.

**InviZible Pro beta 2.2.6**
* Fixed a crash when using Tor bridges containing only IP and port.

**InviZible Pro beta 2.2.7**
* Updated Tor to version 4.8.12.
* Implemented Tor isolate by app option.
* Added donation addresses to the about screen.
* Fixes and optimizations.

**InviZible Pro beta 2.2.8**
* Improved app update process.
* Updated Polish, German, Spanish, Indonesian and Chinese translations.
* Fixes and optimizations.

**InviZible Pro beta 2.2.9**
* Updated Purple I2P to version 2.53.0.
* Updated Tor geoip files.
* Preparing the app for use on Android 15.
* Fixed detecting internet access permission in apps with a shared id.
* Fixes and optimizations.

**InviZible Pro beta 2.3.0**
* Updated Tor bridges: Conjure, Lyrebird, SnowFlake.
* Updated French and Portuguese (Brazil) translations.
* Added Arabic and Bulgarian translations.
* Fixes and optimizations.

**InviZible Pro beta 2.3.1**
* Updated Purple I2P to version 2.53.1.
* Updated German, Bulgarian, Chinese, Polish, Arabic, Persian and Portuguese (Brazil) translations.
* Fixed using socks5 proxy with authentication.
* A lot of minor fixes and optimizations.

**InviZible Pro beta 2.3.2**
* Updated Tor.
* Fixes and optimizations.

**InviZible Pro beta 2.3.3**
* Updated Purple I2P to version 2.54.0.
* Updated Tor Snowflake bridge.
* Added option for downloading and updating DNSCrypt blacklists.
* Tor apps isolation is enabled by default.
* Fixed requesting Tor bridges via the app.
* Updated German and Spanish translations.

**InviZible Pro beta 2.3.4**
* Implemented the ability to use the firewall when other modules are stopped.
* Added a stop button to the notification.
* Fixes and optimizations.

**InviZible Pro beta 2.3.5**
* Updated Tor.
* Updated Tor Snowflake bridge.
* Updated Tor Lyrebird bridge to version 0.4.0.
* Implemented the ability to insert a list of DNSCrypt rules.
* Fixes and optimizations.

**InviZible Pro beta 2.3.6**
* Added an option for quick access to Always-on VPN settings.
* Improvements and fixes for standalone firewall operation.
* Improvements for the firewall in root mode for Android versions above 5.
* Removed the Fastly rendezvous of the Tor snowflake bridge.
* Minor fixes and optimizations.

**InviZible Pro beta 2.3.7**
* Updated Tor to version 4.8.13.
* Fixed detection of whether a cellular network interface can provide an internet connection.
* Fixed I2PD subscriptions option.
* Updated German, Spanish, Greek, Chinese, Portuguese (Brazil), Persian and Bulgarian translations.

**InviZible Pro stable 6.9.0**
* Updated Tor to version 4.8.13.
* Updated Purple I2P to version 2.54.0.
* Added a stop button to the notification.
* Added an option for quick access to Always-on VPN settings.
* Implemented the ability to use the firewall when other modules are stopped.
* Tor apps isolation is enabled by default.
* Fixed requesting Tor bridges via the app.
* Updated translations.

**InviZible Pro beta 2.3.8**
* Updated default Tor bridges.
* Fixed LAN rules handling for firewall in root mode.
* Attempt to fix obfs4 protocol blocking in some countries.
* Updated Polish and French translations.

**InviZible Pro beta 2.3.9**
* Updated Tor Snowflake bridge to version 2.10.1.
* Fixed downloading DNSCrypt rules when Block connections without VPN is enabled.
* The app switches to VPN mode from Root mode if root becomes unavailable.
* Fixes related to root detection and root commands execution.
* Other minor fixes.

**InviZible Pro stable 6.9.1**
* Updated default Tor bridges.
* Updated Tor Obfs4 obfuscation bridge.
* Updated Tor Snowflake bridge to version 2.10.1.
* Fixed LAN rules handling for firewall.
* Updated translations.

**InviZible Pro beta 2.4.0**
* Improved Tor Obfs4 bridges censorship resistance.
* Updated Tor SnowFlake bridge stun servers.
* Various fixes and optimizations.

**InviZible Pro beta 2.4.1**
* Display the destination port in real-time logs.
* Socks5 proxy fixes and improvements.
* Improved handling of local networks.
* Tor Browser, OnionShare, Orbot, Briar, Cwtch are excluded from Tor by default as they contain their own Tor instance.
* Fixes and optimizations.

**InviZible Pro beta 2.4.2**
* Added IGMP protocol logging in real-time logs.
* Optimized performance and battery usage in VPN mode.
* Various fixes for Root mode.
* Added Dutch translation.
* Updated Polish, Portuguese (Brazil), Persian and Spanish translations.

**InviZible Pro beta 2.4.3**
* Added x86_64 version for ChromeOS and emulators.
* Updated Tor.
* Updated Purple I2P to version 2.55.0.
* Updated default DNSCrypt servers.
* Optimized performance in VPN mode.
* Added Tamil translation.
* Updated Japanese, Chinese and Dutch translations.
* Fixes and optimizations.

**InviZible Pro stable 7.0.0**
* Updated Purple I2P to version 2.55.0.
* Added x86_64 version for ChromeOS and emulators.
* Improved Tor Obfs4 bridges censorship resistance.
* Optimized performance and battery usage in VPN mode.
* Display the destination port in real-time logs.
* Socks5 proxy fixes and improvements.
* Added Dutch and Tamil translations.
* Various fixes and optimizations.

**InviZible Pro beta 2.4.4**
* Updated DNSCrypt to version 2.1.7.
* Updated Tor obfuscators: WebTunnel, Lyrebird, SnowFlake.
* Improved IPv6 handling in VPN mode.
* Fixed round monochrome icon.
* Updated Dutch, Chinese, French and Finnish translations.
* Fixes and optimizations.

**InviZible Pro beta 2.4.5**
* Updated Tor.
* Added the option for preventing DNS leaks.
* Added I2P sharing option for VPN mode.
* Updated cdn77 meek_lite bridge.
* Fixes and optimizations.
