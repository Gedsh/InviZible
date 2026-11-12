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

The native regression tests cover malformed packet and DNS bounds, DNS compression, JNI
credential byte limits, failed TCP setup, socket/file cleanup, event-loop
shutdown, UDP forwarding and redirects, IP options/extensions, TCP data and
shutdown, SOCKS5 negotiation, DNS answers/blocking, ICMP, and DHCP offers.
They also inject allocation, mutex, pipe, and descriptor-configuration failures;
check initialization retry and partial-session cleanup; preserve TCP queues on
allocation failure; and exercise bounded logging and JNI exception-reporting failures.
Exact TCP, UDP and ICMP flow lookups use a fixed 1,024-bucket index alongside the
cleanup list, with one additional link per record and no index allocations.
Tests cover both address families, ports and echo IDs, IPv4 union padding,
unaligned query addresses, newest-duplicate ordering, stopped ICMP filtering,
closed/blocked flow lookup, policy removal, expiry, blocked-record reuse,
eviction, collisions, 2,560-record churn, teardown and context reuse. Broader UID
restoration matching and oldest-blocked-flow selection still use the cleanup list.
TCP queues enforce a 256 KiB payload budget and a 1024-segment metadata limit per
session. Tests cover receive-window boundaries, sequence wraparound, and shorter
or longer retransmissions after partial sends, including forwarding through a real socket.
Missing-head packets can displace the furthest unacknowledged data at either queue
limit, including with a zero window. Regressions verify allocation-failure recovery,
overlapping bytes, bounded queues, and delivery of evicted tails after retransmission.
The context also bounds requested retained TCP allocations to 16 MiB: ordinary
queue payloads and segment structs share 14 MiB, gap recovery and DNS requests
may use a further 1 MiB, and DNS response frames have a separate 1 MiB pool.
Queue nodes are limited to 16,384 plus 512 for progress. These aggregate limits
include temporary replacement allocations and the actual capacity of filtered
DNS frames. Allocator overhead, session records, scratch buffers and kernel socket
buffers are outside this limit. Existing per-session caps still apply.
Pressure tests saturate the byte and node pools across connections, check rollback
on allocation failure, pause DNS without bypassing filtering, and verify that
memory releases reopen idle flows. Queue and DNS-response release generations
are independent. Only flows blocked by shared queue memory retry their receive
capacity on a queue release; socket-blocked flows recover through epoll without
extra capacity queries. Event-loop regressions check a single rescan after cleanup
for waiters in the released pool, no memory-driven rescan without waiters or for
the other pool, and no writable or hangup spin during a DNS memory wait.
Recovery adds one 8-byte generation counter per context; the wait flag fits
existing session padding on ARMv7 and ARM64. It adds no allocations or timers.
IPv4/IPv6 recovery tests cover window scales 0, 7 and 14, including a partial
memory release too small to advertise a nonzero window and a later release that
resumes delivery. Shared capacity below one scaled window unit stays blocked.
Combined-pressure cases control the socket-capacity samples while forwarding real
local stream data. A positive socket allowance smaller than one scaled unit can
advertise a whole unit when retained storage can hold it. The final window is
rounded down after the per-flow and aggregate memory limits, keeping blocked
windows and recovery decisions consistent with the wire without polling timers.
Ten TCP shutdown cases use real local socket pairs to verify IPv4/IPv6 data
after upstream EOF, app-first and simultaneous close, FIN behind a sequence gap,
repeated FIN, data queued before EOF, timeout/reset cleanup, failed write shutdown,
and stored UID logging.
Lazy diagnostic cases count address conversions on IPv4/IPv6 TCP and UDP paths:
disabled logs skip their arguments and normal packet/session diagnostics, enabled
errors retain addresses and the original TCP state, and usage accounting still
receives its destination. Address formatting is cached only within a call and
preserves errno; no persistent session cache or allocation is added.
The native event loop owns two lazy scratch buffers: one shared by sequential
TUN/socket reads and one for packet construction. Capacity grows only when needed
(at least the MTU), and both buffers are freed on stop or fatal polling failure.
TCP queues and buffered DNS frames retain independent storage. Standalone helpers
without a buffer scope retain their allocate/free behavior.
Buffer-reuse cases cover failed allocation/growth, retries and EOF, IPv4/IPv6 TCP,
UDP and ICMP replies, queued TCP data surviving input reuse, DNS filtering and
partial frames, maximum UDP packet construction, and cleanup after loop exit.
Large UDP construction uses a local sink because some device kernels cannot
allocate a maximum-size Unix datagram; smaller replies exercise real socket pairs.
UDP lookup cases count exact flow searches, UID lookups and policy callbacks.
Non-DNS packets reuse one packet-local result for capacity, UID, policy and
forwarding checks, including a completed lookup that found no session. IPv4/IPv6
cases distinguish protocol, family, ports and addresses, and preserve active,
finishing, closed and blocked flows. DNS keeps its forwarding-policy shortcut
separate from the handler's actual flow lookup; an existing DNS flow must not
bypass UID/policy checks when DNS forwarding is enabled. The lookup is never
stored beyond the packet or reused across event-loop iterations.
TCP backpressure uses socket writability for queued, contiguous app data and TUN
readiness for sequence gaps. Nonblocking sends retain partial writes and retry on
EAGAIN without a periodic buffer-space poll. Closed app windows schedule probes
after 1 second, doubling to a 60-second cap; app ACKs can reopen reads immediately.
TCP cases exercise both families with full socket buffers, partial writes, probe
timing and ACK-driven recovery. A session case checks the earliest probe deadline
across flows and stop-pipe readiness using a controlled monotonic clock.
IPv4/IPv6 loopback TCP cases fill the upstream buffer exactly, empty the native
queue and advertise a zero receive window. They verify that writability remains
monitored until a window-update ACK is sent, further app data resumes, and writable
monitoring stops once the window reopens or app input has shut down.
Legacy UID proc-table cases cover native-word address decoding for IPv4/IPv6,
mixed-case hex, malformed and truncated fields, wildcard/duplicate match order,
cache growth failures, expiry and bounded replacement. The dedicated row parser
avoids `sscanf` and address byte-swapping. Cache allocation grows geometrically
up to 64 entries and then in blocks of 64, retaining at most 63 unused slots.
The full-cache case checks that population needs at most 80 allocations for
4,096 entries rather than one allocation per entry. Cache matching remains linear.
Incoming IPv4 header checksums are always validated, including options and padding.
TCP, UDP, ICMPv4, and ICMPv6 checksums are validated before policy callbacks or session
creation. IPv4 UDP may omit its checksum; IPv6 UDP may not. IPv6 has no header checksum.
Ten IP cases use an independent byte-oriented checksum reference and cover odd/even
payload lengths, scalar/SIMD boundaries, corrupted payloads and pseudo-header addresses,
IPv6 extension lengths, source/segment routing destinations, Mobile IPv6 home addresses,
malformed options, and UDP's all-ones encoding for a calculated zero checksum.
Active IPv6 routing formats whose final destination is unknown are rejected explicitly.
The valid-input UDP/IPv4-options/IPv6-extension fixtures now carry complete checksums.
Eight DNS question-count cases cover RFC 9619 handling for standard QUERY messages:
UDP requests with more than one question receive a header-only FORMERR before any
upstream socket is opened; UDP replies with multiple questions are dropped, and TCP
replies reset the stream. Zero-question messages and other opcodes retain their existing
forwarding behavior. TCP responses retain at most one length-prefixed DNS frame per
session (65,537 bytes maximum, allocated to its declared size). Every complete frame is
inspected before forwarding, including coalesced messages and split headers/bodies.
Rewriting updates the TCP DNS length prefix. Output respects the app window and MSS;
pending output resumes during event-loop monitoring when the app window reopens,
independently of upstream socket writability. Each monitor pass sends at most TCP_YIELD
packets, with a short poll deadline if writable-to-app output remains. Twelve TCP cases
cover domain/HTTPS filtering and callbacks across coalesced and byte-split replies,
maximum-size frames, window pressure, allocation/output failures, partial EOF, timeout,
and tunnel teardown. The backpressure case fills the upstream send buffer, reopens the
app window through an actual TCP ACK, and verifies progress across monitor passes while
epoll reports no upstream readiness. All release the retained frame through normal TCP cleanup.
TCP queue accounting is checked against an independent list traversal after gap
recovery, overlap eviction, allocation failures, partial sends, retransmission
growth and cleanup. Capacity sampling cases count `SO_SNDBUF` and `SIOCOUTQ`
queries: a read batch shares one sample, a successful write refreshes it, and a
new event observes changed socket capacity. Samples live on the event-loop stack;
queue totals add three integers per session without allocating extra buffers.
These cases use real `fcntl(F_GETFD)` checks to verify that the retained socket
stays open after EOF and is actually closed after completion or error cleanup.
EOF disables upstream reads while app data continues to drain. App FIN shuts
down the write direction only after preceding bytes are forwarded; both closed
directions release the socket, with existing close timeouts covering incomplete shutdown.
JNI callback tests inject each class, object, string, UTF, and field-lookup failure,
verify reference cleanup and no calls with pending exceptions, and retry successfully.
Library-load tests fail each class lookup and global-reference allocation, check
local/global reference cleanup, preserve the pending exception, and retry successfully.
TCP, UDP, and ICMP destination tests inspect complete IPv4/IPv6 socket addresses,
including redirects. The test build poisons uninitialized automatic variables so
missing initialization is detected reliably.
Six TCP connection-failure cases verify IPv4/IPv6 resets for immediate network-unreachable,
host-unreachable, and connection-refused errors, including SYN sequence wraparound.
They check reset flags, addresses, ports, acknowledgment numbers, descriptor closure,
and release of SYN payloads and session records. Temporary connect errors and local
setup failures remain retryable without a reset, including when errno is stale.
The test build disables function inlining and preserves symbol interposition so allocation failure injection also
intercepts calls within the source that defines the allocation helpers.
The C fixtures live in `src/main/cpp`; JUnit entry points live in
`src/androidTest/java`. Fixtures compile separately and follow the production
source they exercise:

| Production source | Native fixture | JUnit class |
| --- | --- | --- |
| `invizible.c` | `invizible_tests.c`, `invizible_jni_tests.c` | `InvizibleTest` |
| `util.c` | `util_tests.c` | `UtilTest` |
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
The IP cases also inject initial allocation and resize failures into the UID cache,
check deduplication and refresh, verify its 4,096-entry cap, and preserve IPv4/IPv6
and TCP/UDP lookup behavior. A TCP case checks that closing frees queued payloads
immediately while retaining the metadata needed for late packets and usage accounting.
Six event-loop cases intercept polling and advance a test clock to verify idle
UDP/TCP cleanup deadlines, closing-state transitions, earliest-deadline selection,
and eventual release of session records without waiting minutes on the phone.
Three churn cases verify the shared 1,024-record closed TCP/UDP cache limit across
repeated bursts, preserving active and blocked flows, closing sockets, freeing
queued TCP data, and accounting usage before eviction. Cleanup keeps the most
recently created closed records; normal TCP/UDP expiration deadlines still apply.
Three UDP cases verify the independent 1,024-record blocked-flow cap, IPv4/IPv6
eviction without disturbing active sessions, and reuse of repeated blocked DNS flows.

Three additional Android JUnit classes exercise JNI callers:

- `NativeTunnelTest` runs twelve lifecycle tests against the production Java owner
  with controllable workers. These cover initialization/configuration order,
  shutdown timeouts, interruption, service replacement, delayed cleanup,
  configuration rejection, and restart after a worker exits. The interleaving
  cases also cover retired prepared commands, atomic interface creation/start,
  bounded replacement attempts during cleanup, and delayed cleanup after takeover.
- `NativeTunnelRetryTest` runs eight cleanup-triggered retry tests. These cover
  replacement and reload after a shutdown timeout, cancellation by queued commands
  or service retirement, superseded requests, and protection of a newer VPN owner.
  `NativeTunnelTestBackend` supplies the controlled workers shared by these classes.
- `Socks5SettingsTest` runs eight tests comparing the production JNI preflight
  check with the setters through the device's real JNI implementation. It covers
  byte limits for ASCII, multibyte characters, supplementary characters, embedded
  NUL, addresses, null fields, and disabled settings.

Gradle stages the production `NativeTunnel.java` as a generated test source;
there is no separately maintained copy of the owner. Five real-JNI exception and
exit-reason tests supplement the lifecycle and settings tests. The allocation-failure
case runs on a Java worker with the production Exception catch, checks that no
Error escapes, and verifies that the native exit callback receives the failure reason.
The full run contains 190 tests plus the optional comparison test.

A START or RELOAD that times out waiting for the previous worker registers one
retry. It waits for that worker to exit and native cleanup to finish, then posts
the original command back to the VPN handler. A newer command, service destruction,
or VPN permission revocation cancels the pending retry. Ordinary configuration
and startup errors do not trigger this retry.

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
do not establish a VPN interface or launch the Android service lifecycle. The
ownership tests exercise lifecycle coordination through controlled Java workers.
Only the dedicated test APK contains the fixtures and test dependencies.

The app also has five handler integration regressions in
`tests/handler/src/androidTest/java/pan/alexander/tordnscrypt/vpn/service/ServiceVPNHandlerTest.java`
(relative to `tordnscrypt/`). They compile the actual `ServiceVPNHandler` and use
an Android `HandlerThread` to exercise its command queue, timeout catch, retry
dispatch, and reload ordering. Controlled native workers and overrides for VPN
and root operations avoid establishing a VPN or changing iptables. The cases
cover START after previous-owner cleanup, RELOAD after timeout with exactly one
tethering block/unblock, cancellation as soon as a delayed STOP is queued, and
a newer START superseding a waiting RELOAD, and terminal startup failure
notification. Run them with the app's matching
debug variant, for example:

```sh
./gradlew :tordnscrypt:connectedFdroidArmv7aDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=pan.alexander.tordnscrypt.vpn.service.ServiceVPNHandlerTest
```

Both app build files register `tests/handler/src/androidTest/java` as an
`androidTest` source directory; the checkout ignores the default `src/androidTest`.
These app tests are separate from the `:native-tests` suite above. Select the
appropriate app build/signing configuration for your checkout when running them.

The same app instrumentation source directory also contains fifteen boot-recovery
regressions. `BootCompleteManagerTest` records service requests without launching
them and verifies that VPN/TTL recovery targets the manifest-declared `ServiceVPN`,
while other modes only target `ModulesService`. `ServiceVPNRecoveryTest` runs the
production `onStartCommand` on the main thread, substituting native command delivery
and framework shutdown. It checks bootstrap cleanup, overlapping package-replaced
and Always-on recovery after START, cleanup after RELOAD, and shutdown after explicit
or generated STOP commands. Late recovery cleanup must preserve an accepted start
and its foreground state. Real commandless system Always-on intents and null restart
intents must also preserve an accepted START or RELOAD, while bootstrap and recovery
after STOP remain available. Terminal startup failures release the recovery guard
when no worker remains; a running tunnel or a newer command keeps its protection.
The handler tests also check terminal failure notification and distinguish it from
cleanup retries. The complete app instrumentation suite has twenty tests;
omit the class filter above to run all of them.
