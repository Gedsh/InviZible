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
