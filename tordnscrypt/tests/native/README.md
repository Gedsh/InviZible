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
UDP and ICMP handler cases reject truncated headers and offsets past the packet
end for both IP versions without creating sessions or opening sockets.
ICMP receive cases drop one-to-seven-byte datagrams before accessing echo fields,
then forward valid replies with restored identifiers. Both temporary allocations
and reused scratch buffers are covered, along with the existing zero-length stop
behavior. An independent byte-oriented checksum reference checks IPv4/IPv6 replies
at minimum, odd/even and larger lengths, and IPv6 forwarding with and without a
Hop-by-Hop header. ICMPv6 pseudo-headers use the ICMP message length in network byte
order and protocol 58, excluding any IPv6 extension headers.
Exact TCP, UDP and ICMP flow lookups use a fixed 1,024-bucket index alongside the
cleanup list, with one additional link per record and no index allocations.
Tests cover both address families, ports and echo IDs, IPv4 union padding,
unaligned query addresses, newest-duplicate ordering, stopped ICMP filtering,
closed/blocked flow lookup, policy removal, expiry, blocked-record reuse,
eviction, collisions, 2,560-record churn, teardown and context reuse. Exact UID
restoration uses the same index; oldest-blocked-flow selection still uses the cleanup list.
TCP queues enforce a 256 KiB payload budget and a 1024-segment metadata limit per
session. Tests cover receive-window boundaries, sequence wraparound, and shorter
or longer retransmissions after partial sends, including forwarding through a real socket.
Each segment and its payload share one allocation. A tail pointer allows sorted
appends without walking the queue; out-of-order insertion still follows sequence
order. Growing a retransmission replaces its whole allocation, preserving accepted
bytes, partial-send offsets, PSH and links. Both allocations and the temporary
node are charged until replacement completes. Tests count one allocation per
segment and linear total sequence comparisons for 1,024 appends across wraparound,
and check tail updates after growth, gap overlap/eviction, drain, cleanup and reuse.
Segment metadata shrinks from 20 to 16 bytes on ARMv7 and from 32 to 24 bytes on
ARM64, in addition to avoiding a second allocator header. The tail pointer fits
existing ARMv7 session padding and adds 8 bytes per session record on ARM64.
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
Legacy UID lookup uses the same cached address formatter. With INFO diagnostics
disabled, successful proc lookups and cache hits skip both address conversions;
other diagnostics format addresses only when their level is enabled. INFO cache-hit
logging still receives the original addresses, including IPv4's IPv6-table probe.
An IPv4/IPv6 TCP/UDP regression counts zero conversions for normal and root owners,
cache hits, missing/malformed rows and IPv4 fallback after an IPv6 proc-open error,
while checking resolved owners, cache reuse and resource balance.
The same focused regression can run with INFO logging enabled to check unchanged
address text and exactly two conversions per lookup, including cache hits. Restore
the default ERROR level before running the full suite; disabled-log regressions
expect that default.
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
finishing, closed and blocked flows. DNS shares its actual flow lookup with
forwarding and resolves ownership/checks policy on every request in both modes.
An existing DNS flow must not bypass policy checks. The lookup is never
stored beyond the packet or reused across event-loop iterations.
TCP backpressure uses socket writability for queued, contiguous app data and TUN
readiness for sequence gaps. Nonblocking sends retain partial writes and retry on
EAGAIN without a periodic buffer-space poll. Closed app windows schedule probes
after 1 second, doubling to a 60-second cap; app ACKs can reopen reads immediately.
The event loop reuses unchanged TCP monitoring decisions until a packet/socket
event, a relevant memory release or a probe deadline requires reevaluation.
Buffered DNS output continues to drain on each event batch. Probe timestamps
already stored in the flow supply deadlines; two flags fit existing ARM session
padding, adding no per-session allocation or timer. The count and cleanup list
traversals remain. A 64-flow regression counts one monitor call per changed flow
after the initial pass, and deadline tests check that unrelated loops do not
restart or postpone probe backoff.
TCP cases exercise both families with full socket buffers, partial writes, probe
timing and ACK-driven recovery. A session case checks the earliest probe deadline
across flows and stop-pipe readiness using a controlled monotonic clock.
IPv4/IPv6 loopback TCP cases transfer a full send buffer, empty the native
queue and model zero send capacity independently of kernel ACK timing. Recovery
uses real socket capacity after draining the peer. They verify that writability remains
monitored until a window-update ACK is sent, further app data resumes, and writable
monitoring stops once the window reopens or app input has shut down.
Legacy UID proc-table cases cover native-word address decoding for IPv4/IPv6,
mixed-case hex, malformed and truncated fields, wildcard/duplicate match order,
cache growth failures, expiry and bounded replacement. Scans continue past malformed
rows and retain valid owner matches before or after them, including UID zero.
Malformed scans without a resolved match still report an error. Overlong physical
rows are discarded whole so continuations and partial UID fields cannot become
socket records. Cases cover both families and TCP/UDP, duplicate ordering, final
rows without a newline, overlong headers, disabled caching and cache allocation failure.
Cache timestamps use
64-bit `CLOCK_BOOTTIME` milliseconds for the cache's 30-second lifetime,
including device sleep. Clock-read failures bypass cache reads and updates while
retaining proc lookup. Tests check the real device clock, cross signed/unsigned
32-bit millisecond boundaries, verify exact expiry and clock-failure recovery,
and compare linear and indexed replacement using timestamps above 32 bits. The dedicated row parser
avoids `sscanf` and address byte-swapping. Cache allocation grows geometrically
up to 64 entries and then in blocks of 64, retaining at most 63 unused slots.
After 64 valid proc rows, caches with at least 64 entries use a temporary
1,024-bucket exact-key index, an oldest-entry heap ordered by timestamp and slot,
and a forward cursor for expired slots. This replaces repeated full-cache update
searches with expected constant-time key lookup and logarithmic heap updates.
The 26,632-byte workspace is allocated once per larger scan and freed at scan
completion, including scans containing malformed rows. Failed workspace allocation uses the linear fallback
for that scan. Small caches, short scans and cache-hit lookups allocate no workspace.
Differential tests compare IPv4/IPv6 updates against the prior linear policy,
including duplicate keys, expiry, timestamp ties, backward time, mixed protocols,
full-cache churn, growth failures and workspace cleanup. Wildcard lookup order
and proc-scan last-match behavior remain unchanged; cache matching remains linear.
The full-cache case checks that population needs at most 80 allocations for
4,096 entries rather than one allocation per entry.
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
Three timeout-range cases check ICMP, UDP and TCP sessions with future timestamps:
polling stays capped at one hour, including values that would wrap when narrowed
to a 32-bit int on devices with 64-bit time_t. ARMv7 exercises representable
timestamps near the 32-bit time_t limit.
Three descriptor-limit cases inject ordinary, tiny, very large and unlimited
`RLIMIT_NOFILE` values and a failed `getrlimit()` call. They verify that the event
loop passes the expected positive, capped session limit to timeout scaling
without changing the device's real resource limits.
Four monotonic-clock cases inject failures before the first sample, during TCP
probe monitoring, throughout event-loop maintenance and just before polling.
`get_ms()` returns -1 on failure. Callers retain probe timestamps/backoff, retry
within 100 ms and keep wall-clock session cleanup running; recovery resumes the
original deadlines and monitor caching. A utility case also checks wide integer
millisecond conversion, a valid zero timestamp and the real device clock.
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
Eight additional live proc tests open local TCP, UDP and ICMP ping sockets, then
read the real `/proc/net/{tcp,tcp6,udp,udp6,icmp,icmp6}` tables under the test app's
UID. They check socket ownership, cache reuse without reopening the table, the
public IPv6-first UID lookup and IPv4 fallback, and IPv4-mapped TCP/UDP sockets.
If an older kernel has no `icmp6` table, that case verifies a clean lookup miss
without opening a file or allocating cache storage instead of binding a socket.
ICMP sockets use connect/autobind without sending packets, supporting older app
policies that reject explicit ping-socket bind calls.
They send no packets and require no firewall exceptions. These cases run on
Android 6-9; Android 10+ skips them because app access to `/proc/net` is restricted.
Six `MemoryProfileTest` cases exercise the production `PROFILE_MEMORY` tracker in
a separate native library, with raw allocator and mutex fault injection. They
cover first-record and table-growth failures, payload allocation and resize
failures, release before first use, errno preservation, lock failure/retry, and
concurrent first use and churn. Tracker storage failure frees the record table
and disables tracking until the library is unloaded; application allocations
continue normally. An empty tracker releases its record table. The test library
exports only its test entry point to avoid replacing the ordinary suite's JNI.
UID restoration also checks unaligned IPv4 addresses with their high bits set
for TCP, UDP and ICMP, including source and destination mismatches.
IPv4/IPv6 TCP and UDP cases require exact source/destination ports and addresses,
reject closed/disconnected flows and unknown owners, and do not fall back past
the newest exact record. TCP SYN restoration also requires the original sequence
number, preventing UID reuse across successive connections with the same tuple.
ICMP restoration uses the newest eligible indexed record rather than skipping an
unknown owner for an older UID. TCP/UDP/ICMP handler cases check that a resolved
UID, including UID zero, fills only an unknown live owner and that failed lookups
or conflicting UIDs do not replace a known owner. TCP updates require the same SYN
sequence number; stale/disconnected records do not acquire a new owner.
When a retransmitted SYN resolves an unknown TCP owner, the existing connection
also applies the packet's policy decision and verifies its route and Tor isolation.
Denied owners, changed routes/endpoints, isolation changes and unavailable peer
information reset the old socket and release queued data. Matching allowed routes
keep the socket without repeating the UID lookup or policy callback. IPv4/IPv6
integration cases cover LISTEN, SYN_RECV and ESTABLISHED, JNI/proc lookups, UID zero,
direct/Tor/proxy/redirect routes, stale events, handshake completion and data delivery.
Malformed SYN combinations, changed initial sequences, and disconnected/closed
sessions cannot fill an unknown owner.
At or above session capacity, valid SYN retransmissions for an exact live socket
with the original sequence still resolve ownership and enforce policy without
creating a session or socket. The route/isolation matrix runs below, at and above
the limit for both families and JNI/proc lookups, including reset cleanup and
continued data delivery. Capacity boundary cases preserve known/root owners and
allow unknown owners when policy permits; new flows, changed tuples/sequences,
closed/socketless records and malformed SYN flags/header lengths remain dropped
before lookup and policy callbacks.
Successful lookups also fill unknown live ICMP and UDP DNS owners before policy
handling, even when the current packet is denied. Retired DNS records retain the
newly learned owner for their earlier traffic. ICMP cases cover both families and
lookup paths, UID zero, known-owner preservation, failed subsequent lookups, and
stopped, disconnected or mismatched sessions. Synthetic ICMPv6 proc tables do not
depend on the device kernel exposing `/proc/net/icmp6`; live proc tests still use
the real device's table availability.
At session capacity, exact live ICMP flows still resolve ownership, check policy,
and forward without creating a session or socket. IPv4/IPv6 cases exercise both
lookup paths at and above the limit, UID zero, known/unknown owners and denial.
New, stopped, disconnected, and mismatched flows remain blocked by the limit.
Outgoing sends are captured; replies traverse real local sockets with restored IDs,
and allocation/socket balances are checked after each case.
Logging preserves the caller's `errno` even when the log backend changes it or
returns an error. The utility regression also checks disabled logging and direct
logger calls, with unchanged formatted output.
A packet timestamp regression injects `gettimeofday()` failure, checks the
whole-second `time()` fallback and zero timestamp if both reads fail, and verifies
that packet fields, JNI reference cleanup and subsequent recovery remain intact.
Run with `-PnativeJniProfiling=true` to also compile the optional `PROFILE_JNI`
paths and inject failures in profiling's start/end samples. Invalid timing
samples are skipped without changing packet creation. This option can be combined
with `nativeSanitizers` and `nativeBaseline`.
TCP connect-error logs omit the repeated target for direct connections and retain
the actual target for other routes. An IPv4/IPv6 regression checks direct and
same-family/cross-family redirect errors, preserved error details, socket cleanup,
and skipped target-address formatting for direct failures.
Legacy IPv4 UID lookup falls back after IPv6 proc open or parse errors, while
successful mapped IPv6 lookups (including UID zero) retain priority. TCP and UDP
regressions cover these cases, an empty IPv6 table, and failure of both tables.
JNI UID lookup exceptions discard the callback result and return an unknown UID.
Regression cases cover apparent UID zero, a positive UID, and an unknown UID,
checking reference cleanup and a successful retry after each exception.
Active non-DNS UDP flows with unknown owners retry UID lookup on outgoing packets,
at most three times, with one-, two-, and four-second delays. Idle flows schedule
no wake-ups; known, inactive, and disconnected flows do not retry. Clock failures
disable further retries for that flow. Each lookup retry triggers policy checking,
including a failed lookup that leaves the owner unknown.
Unchanged direct or redirected routes keep their socket and source port; denied
flows and changed routes retire the old socket, using a new socket when allowed.
Unknown-owner replacements and active/blocked transitions retain their retry count
and absolute deadline, so socket replacement cannot restart the three-attempt budget.
Retired records stay
alive for normal cleanup, and queued socket events for them are ignored.
IPv4/IPv6 regressions exercise proc and JNI lookup paths, retry spacing and budget,
UID zero, allow/deny policy, same-family and cross-family redirects, stale events,
clock failures, and allocation/socket balance.
Non-DNS sessions retain their binary forwarding endpoint separately from the
original flow key. Second and third packets keep using it without more UID or
policy lookups, even after the temporary policy result changes. DNS retains its
per-request routing behavior.
DNS lookup failures can restore the UID from an exact live session before policy
and forwarding. IPv4/IPv6 UDP and TCP regressions cover proc and JNI lookup paths,
UID zero, fresh lookup precedence, denied policy, missing/mismatched/unknown or
disconnected/closed records, and TCP SYN sequence reuse. DNS policy and session
limits remain covered with forwarding enabled and disabled.
Unknown-owner blocked non-DNS UDP records use the same bounded, packet-driven
UID retries as active flows. Each retry triggers policy reevaluation; denied
owners remain cached as blocked, while allowed owners can create a socket using
the current route. A full active-session table defers recovery without consuming
the retry budget. Known-owner blocks never use this recovery path.
Regressions cover both address families and lookup paths, UID zero, retry and
clock limits, policy and routing decisions, capacity, and socket/allocation failure
cleanup followed by successful retry.
TCP flows with unknown owners retry lookup on valid in-order ACK/data and FIN/ACK packets in
ESTABLISHED, CLOSE_WAIT, FIN_WAIT1 and FIN_WAIT2, with the same three-attempt backoff
and no idle wake-ups. ACKs may range from the last accepted acknowledgement through the latest
transmitted sequence, using wrap-safe comparisons. Recovery rechecks
policy and retains an allowed socket only when its route and redirect endpoint
match. Denied flows, changed routes, and Tor UID isolation reset the connection,
release queued data, and ignore stale socket events. IPv4/IPv6 cases cover JNI and
legacy proc lookup, UID zero, retry initialization and clock failure, finite
budgets, packet validation, route changes, and data forwarding after recovery.
Additional cases cover partial ACKs at the range endpoints and interior,
wraparound, half-closed data forwarding, policy denial and route changes, bounded
failed retries, stale/future ACKs, invalid states, and data beyond an app FIN.
App-first half-close cases reach CLOSE_WAIT through an actual FIN and local socket
write shutdown, receive upstream data, and recover ownership on a pure partial ACK.
They check both IP families, JNI/proc lookups, UID zero, continued upstream delivery,
policy denial, route changes, stale events, and the finite failed-lookup budget.
FIN/ACK recovery occurs before queuing the final payload or processing shutdown,
and passes the FIN flag to policy. Cases cover pure FIN and FIN with final data in
ESTABLISHED and both FIN_WAIT states, sequence wraparound, known/root owners,
failed lookups, the retry deadline/budget, policy denial, route changes and Tor
isolation. Allowed flows deliver the final bytes and half-close or finish normally;
rejected routes release queued data and ignore stale events. A FIN without ACK,
stale sequence, conflicting FIN position, or an already acknowledged app FIN
cannot trigger recovery. Tests use real local sockets and verify resource balance.
UDP DNS checks ownership and policy on every request, independently of forwarding.
DNS lookups continue to use the legacy proc cache; failed lookups can restore an
exact live owner. Denying an active flow retires its socket registration and ignores
queued events, preventing pending replies from bypassing policy. Allowed blocked
flows can reopen only when an active session slot is available. IPv4/IPv6 cases
exercise JNI/proc lookup, root and unknown owners, immediate repeated requests,
allow/deny policy, direct/redirected destinations, full tables, and blocked recovery.
TCP DNS SYNs also check policy for root-owned flows when forwarding is disabled.
Blocked DNS flows follow policy for unknown owners when lookup fails: an allowed
flow can reopen with an unknown UID when capacity is available. Continued denial
preserves the stored owner of an exact blocked tuple; eviction for a different
tuple does not inherit that owner. Recovery cases cover repeated failed lookups,
allow/deny decisions for unknown owners, capacity limits, UID zero, and subsequent
resolved-owner denial/recovery in both modes and lookup paths.
When an allowed blocked DNS flow cannot reopen because the active-session table
is full, a successful lookup fills its unknown stored owner before returning.
Known metadata is preserved while reopening waits. Capacity regressions cover
UID zero and ordinary owners, both IP families, lookup paths and forwarding modes,
and full/overfull tables without opening a socket. Subsequent failed lookups without
recent destination evidence still
reach policy as unknown, preserve metadata on denial, and can reopen an unknown
active flow when policy permits it and capacity becomes available.
Unknown-owner policy regressions cover TCP, UDP, DNS and ICMP in both IP families,
including failed JNI lookups, empty proc tables and proc access errors. UDP owner
retries reevaluate policy even when lookup fails; an allowed blocked flow can
reopen with an unknown owner, while continued denial preserves its retry budget.
Policy may explicitly allow direct DNS even when forwarding rules are configured;
the native UDP handler honors that result instead of applying a second UID-based
restriction. Malformed packets, unsupported protocols and resource limits retain
their independent checks.
UDP route-retry cases cover unchanged binary endpoints with different IPv6 text,
IPv6 loopback normalization, port/family changes, active/blocked transitions, and
no further owner/policy/clock queries after the shared retry budget is exhausted.
Same-destination UID fallback uses one per-context array of 256 confirmed
observations (about 12 KiB plus small metadata). It matches IP family,
source IP, destination IP and destination port across TCP and UDP; source ports may differ. Only
successful Android/proc lookups add or refresh observations, before policy and
socket creation, including later authoritative TCP/UDP recovery. Connection
completion does not remove observations. DNS hostname inference is not included.
Each observation expires five seconds after its own confirmation, including device
sleep, using `CLOCK_BOOTTIME`. Overflow deadlines use the same clock. Different UIDs
for the same destination remain separate records; inference succeeds only when
all recent matches agree on an app UID. Root, system and the VPN process participate
in conflict detection but cannot themselves be inferred. Guessed and restored
session owners never add observations, and inference never refreshes expiry.
Cross-protocol matches include conflicting and excluded owners. Records retain
their protocol and independent expiry, so refreshing UDP evidence cannot extend
an older TCP observation. Regression cases cover sharing in both
directions, four-second-old evidence, exact five-second expiry, cross-protocol
conflicts and excluded owners, and unchanged source/address/port restrictions.
Packet/policy cases exercise both transports as the confirmed source with IPv4/IPv6,
JNI/proc lookups, DNS forwarding modes and allowed/denied policy outcomes.
Inference compares candidates with the process UID saved during native initialization,
without another `getuid()` call; a regression verifies exclusion uses that saved UID.
The fixed array uses linear scans of populated entries, without hash buckets,
allocations, timers, sleeps, extra platform lookups or an empty-cache clock read.
One per-context slot index remembers the last authoritative write. Repeated
observations for that same key and UID refresh it directly; misses retain the
ordinary scan. Other UIDs remain separate observations for conflict detection.
The remembered slot also has the latest expiry. Once it expires, reads or writes
reset the populated count and overflow pause without clearing array storage.
This avoids scanning stale entries after idle periods; stale storage is outside
the populated range and cannot supply evidence. Clock failures and cleanup still
clear the complete cache.
Expired entries are reused first. If capacity requires eviction of a recent
record, inference pauses until that record's original expiry; additional evictions
retain the latest discarded expiry. Refreshing a retained record does not extend
that pause. Unknown traffic continues through the configured policy.
Clock failures/backwards reads invalidate the array; cleanup clears it and other
VPN contexts cannot use its contents. This is heuristic attribution, not proof of
ownership: unobserved apps can share a destination with the confirmed app.
A packet-local UID/provenance value passes by value to TCP/UDP handlers. Session
provenance prevents a guess from becoming exact confirmed restoration, and keeps
the existing bounded traffic-driven recovery enabled. Authoritative recovery
clears provenance and reevaluates policy, routing and Tor isolation before further
forwarding. A guess cannot downgrade a confirmed blocked UDP record's owner.
Failed UDP recovery retains the flow's provisional owner for policy and routing;
observation expiry limits new inference without interrupting that flow. IPv4/IPv6
regressions using JNI and legacy proc failures exercise every retry deadline,
including after expiry, with unknown owners denied. They verify policy UID,
forwarding calls, socket continuity, bounded retries and resource cleanup.
Thirteen additional regressions cover exact matching and non-refreshing inference,
independent conflict expiry, excluded owners, original-expiry overflow, clocks,
context isolation/cleanup, both families and owner APIs, denied observations,
DNS forwarding modes, inferred session creation/reuse and TCP/UDP authoritative
recovery including policy, route changes, isolation and resource balance.
Suspend coverage advances boot time while holding monotonic time still, checking
exact observation expiry, original overflow deadlines and fresh evidence after
waking for both address families and TCP/UDP. Retry timers retain their existing clock.
Optimization cases count endpoint comparisons in a full cache: 64 repeated writes
to the last slot require 128 comparisons, rather than 32,768. The test APK disables
builtin `memcmp` expansion to count these calls; production build flags are unchanged.
Cases also cover a refresh found by the normal scan becoming the newest write,
independent conflict expiry, overflow deadlines and count resets on idle reads
and writes while retaining the unused array contents.
Exact-restoration and blocked-metadata fixtures explicitly isolate independent
scenarios from recent destination observations; the fallback has separate tests.
Failed-restoration errors are emitted after the destination fallback, avoiding
an error for a packet whose owner was successfully inferred.
Domain UID fallback runs only when there is no usable direct-IP observation;
direct-IP conflicts or excluded owners do not fall through to it. A separate
per-context array stores up to 128 exact hostname/address mappings and 64 exact
discarded-evidence records (about 56 KiB together),
with no allocations, timers, DNS transaction tracking or Java/JNI additions.
Only complete, successful, unfiltered standard A/AAAA responses supply mappings.
Names are normalized to ASCII lowercase without suffix matching; binary labels
cannot masquerade as hostname prefixes. The DNS parser validates record bounds across
the whole response, including remaining sections, before publishing mappings. CNAME chains
up to eight hops are followed regardless of record order, with address lifetime bounded
by every alias TTL. Each hop uses a bounded scan of the existing response; the final
scan collects all matching addresses together. No additional DNS queries are sent.
Unrelated answer owners are ignored. Mapping lifetimes use boot time and are capped
at 60 seconds; zero TTL revokes the matching association. Oversized answer sets
(more than 32 matching addresses) publish no mappings; a validated rescan retains
discarded evidence for every matching address, including those beyond the batch.
Evicting a live mapping retains its exact hostname, address and original expiry.
Only inference for that hostname or a shared destination address is suspended;
unrelated cached domains continue working. There are no hash-only guards or
global DNS inference pause. If the discarded-evidence array is also full, live
guards and mappings are preserved, affected resident keys retain local deadlines,
and admission of new mapping keys stops until the untracked evidence expires.
Existing keys can still refresh without clearing their local deadlines. Direct-IP
inference continues in every case. Both fixed arrays are cleared on teardown and
clock failure; the fix adds no allocations, timers or network requests.
For a uniquely mapped destination hostname, inference compares all recent confirmed
owners of other addresses mapped to that exact hostname, across TCP and UDP.
Destination port must match. Same-family evidence must also match source IP; cross-family
evidence is scoped to the VPN context. Shared destination addresses with multiple
live hostnames are ambiguous and remain unknown. Root, system and own-UID evidence
participate in conflicts but are not inferred. Parsing a DNS response never uses its
socket owner as application ownership evidence. Inferred traffic never seeds ownership.
The shared owner window is five seconds for both exact-IP and domain matching,
and DNS mappings cannot refresh ownership evidence. Clock failures/backwards reads and teardown clear
both caches. DNS that is not visible to the VPN cannot supply these mappings.
Four IP cases cover real packet/policy dispatch across both families and owner APIs,
cross-family and cross-protocol inference in both directions, provisional provenance,
independent cross-protocol conflict expiry, port/source restrictions, excluded owners,
ambiguity, exact-IP priority,
selective original-expiry capacity guards, unrelated-domain continuity, shared-IP
losses, saturation of both arrays, deferred admission and recovery, clocks,
cleanup and context isolation. Four DNS
cases cover TCP/UDP response parsing, normalization, aliases, TTLs, malformed tails,
filtered/truncated/failed responses and resource balance. Reordered one- and two-hop
alias responses verify IPv4/IPv6 inference and conflicting-owner rejection even
when an address precedes its alias. Ownership still passes
through the same policy and TCP/UDP recovery paths used by direct-IP inference.
The full run contains 302 tests plus the optional comparison test.
Inference failures use the existing `Packet uid can not be restored` diagnostic.
Temporary branch diagnostics and their expired-mapping-only scan have been removed;
inference decisions and cache lifetimes are unchanged.
Destination-unreachable errors now contain the original tunnel packet prefix,
family-specific type/code and a checksum over the full ICMP message (including
the IPv6 pseudo-header). Immediate UDP blocks/retries and blocked TCP requests
quote the current packet, bounded so the complete error fits in 576 bytes for
IPv4 or 1280 bytes for IPv6. Async UDP errors retain the latest successfully sent
packet's first 112 bytes inside existing session storage: no per-flow allocation
or increase to `ng_session` size on ARMv7/ARM64. Redirected flows quote the original
application endpoints. If extension headers push the UDP header past that bound,
the async quote is invalidated and no unidentifiable error is emitted. SO_ERROR
does not identify which datagram failed, so the quote is the latest sent datagram
of that flow; detailed error-queue handling remains outside this change.
Async TCP failures retain their TCP reset without a redundant unquoted ICMP packet.
Three ICMP regressions cover blocked-flow bytes/checksums, injected UDP socket
errors, input-buffer reuse, direct/redirected endpoints, IPv4 options, IPv6
extensions, quote limits, malformed short packets, multicast suppression and
allocation failure. A TCP case verifies socket failures produce a reset with no
additional unquoted ICMP. Resource balance is checked across these paths.
Outgoing ping requests require IPv4/ICMP type 8 or IPv6/ICMPv6 type 128,
with code zero. Other types, nonzero codes and mismatched family/protocol pairs
are rejected after the minimum-length check, before checksum work, owner lookup,
session lookup or policy. Direct ICMP handling and UID restoration share the same
type/code validator; valid echo requests retain checksum, policy and capacity checks.
Two additional ICMP/IP cases cover opposite-family echo types, nonzero codes,
echo replies, router solicitations, unreachable messages and unknown types. They
assert no JNI/proc owner lookups, policy callbacks, forwarding or resource creation
for rejected traffic, including at session capacity. Existing-owner restoration
also rejects malformed requests and mismatched protocols.
ICMP session creation closes its socket and frees the unregistered session when
epoll registration fails, before changing the packet or sending it upstream.
An IPv4/IPv6 regression uses real failing epoll calls repeatedly, verifies empty
session indexes, unchanged packets, no sends and balanced resources, then checks
successful registration and echo reply delivery through real epoll/local sockets.
Successful ICMP fixtures use a pollable local UDP socket as the ping-socket
surrogate; outgoing traffic remains captured, without external network access or
a dependency on kernel ping-socket permissions.
TCP packet fingerprint fixtures pin the initial sequence before the handshake;
other process-wide random-generator users cannot change comparison results.

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
