The `:native-tests` Android library module runs the native regressions as standard
Android JUnit instrumentation tests. Run a source-specific test class from
Android Studio, or run the whole module on a connected ARM/ARM64 device or
x86-64 emulator:

```sh
./gradlew :native-tests:connectedDebugAndroidTest --configure-on-demand
```

Use JDK 17+ (including JDK 21), the project's Android SDK/NDK, and API 23+.
`--configure-on-demand` avoids configuring unrelated app flavors and their
owner-specific signing files. Set `ANDROID_SERIAL` to select a device.
Reports are written to `build/native-tests/reports/androidTests/connected/`
and `build/native-tests/outputs/androidTest-results/connected/`.

The 28 tests cover malformed packet and DNS bounds, DNS compression, JNI
credential byte limits, failed TCP setup, socket/file cleanup, event-loop
shutdown, UDP forwarding and redirects, IP options/extensions, TCP data and
shutdown, SOCKS5 negotiation, DNS answers/blocking, ICMP, and DHCP offers.
The C fixtures live in `src/main/cpp`; JUnit entry points live in
`src/androidTest/java`. Fixtures compile separately and follow the production
source they exercise:

| Production source | Native fixture | JUnit class |
| --- | --- | --- |
| `invizible.c` | `invizible_tests.c` | `InvizibleTest` |
| `session.c` | `session_tests.c` | `SessionTest` |
| `ip.c` | `ip_tests.c` | `IpTest` |
| `tcp.c` | `tcp_tests.c` | `TcpTest` |
| `udp.c` | `udp_tests.c` | `UdpTest` |
| `icmp.c` | `icmp_tests.c` | `IcmpTest` |
| `dns.c` | `dns_tests.c` | `DnsTest` |
| `dhcp.c` | `dhcp_tests.c` | `DhcpTest` |

`test_support.c/.h` holds shared spies, state, and packet helpers;
`NativeTestSupport` provides the shared Java bridge and serializes native calls.
The comparison across protocols remains in `compatibility_tests.c` and
`NativeBaselineComparisonTest`. The former combined resource case is split into
individual socket, file, DHCP, queued-data, and shutdown tests, so JUnit reports
each failure against its source. Existing assertions and scenarios are retained.

For example, run just the TCP tests with:

```sh
./gradlew :native-tests:connectedDebugAndroidTest --configure-on-demand \
  -Pandroid.testInstrumentationRunnerArguments.class=pan.alexander.tordnscrypt.nativetests.TcpTest
```

Native assertions report their file, line, and condition
as JUnit failures. Restart instrumentation after a native assertion failure:
that interrupted case may still own resources.

To run the same tests with AddressSanitizer and UndefinedBehaviorSanitizer:

```sh
./gradlew :native-tests:connectedDebugAndroidTest --configure-on-demand \
  -PnativeSanitizers=true
```

This mode requires API 27+ and packages the NDK's ASan runtime and `wrap.sh` into
the test APK. The JUnit setup also verifies native sanitizer instrumentation is
enabled. LeakSanitizer is disabled on Android; allocation, socket, file, and JNI
string balances are checked explicitly by the regression cases. The sanitizer
runtime staging currently supports the Linux x86-64 SDK host.

To compare valid-input behavior with a committed Git revision:

```sh
./gradlew :native-tests:connectedDebugAndroidTest --configure-on-demand \
  -PnativeBaseline=HEAD -PnativeSanitizers=true
```

`NativeBaselineComparisonTest` exports the selected revision's native sources
under the ignored build directory and builds them into a separate test library.
It compares deterministic packet fingerprints, DNS callbacks, counters, and
session transitions across 17 scenarios. The baseline must have a compatible
native API. Repaired DHCP construction, IP options/extensions, malformed inputs,
and over-limit JNI strings are excluded from this comparison because their
behavior intentionally changed; the current-code tests cover them separately.
Without `nativeBaseline`, this one comparison test is skipped.

The tests use real local TCP streams and UDP loopback datagrams. The initial UDP
probe retries for up to two minutes to allow firewall approval. VPN output is
captured through pipes, and JNI policy callbacks use test implementations. ARM
runs production SIMD checksums; x86-64 uses the fixture's scalar checksum to
avoid the bundled NEON emulation's different alignment requirements. These tests
do not establish a VPN interface or exercise the app's Java/Kotlin lifecycle.
Only the dedicated test APK contains the fixtures and test dependencies.
