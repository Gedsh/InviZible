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
External SOCKS5 UDP forwarding uses UDP ASSOCIATE with one protected TCP control
socket and one protected UDP relay socket per selected flow. Tor-selected UDP
remains blocked by the existing policy (including its explicit direct-UDP
exceptions); external-proxy bypasses, firewall rules, IPv6 restrictions and local
DNS forwarding retain their precedence. Policy explicitly marks external UDP
routes, so negotiation failures never fall back to direct traffic. ICMP proxying
is still unsupported. DNS replies received through the relay use the existing
filtering and mapping-cache parser without additional DNS lookups.

External SOCKS5 proxy endpoints may use a different address family from the app
destination. TCP selects the proxy transport family from its numeric address,
while SOCKS requests retain the original destination family. CONNECT replies
are validated using their own IPv4/IPv6 bound-address type, independently of the
destination. Existing mapped
loopback behavior for `127.0.0.1` is preserved. Invalid proxy addresses fail
without attempting a connection or falling back to direct traffic. UID recovery
checks the actual proxy endpoint family and port; matching routes remain open,
while changed endpoints require reconnecting. UDP independently selects the
control and negotiated relay families. A remote proxy address no longer blocks
IPv6 app traffic by itself; explicit IPv6-blocking settings still apply.
Reaching an IPv6 destination over an IPv4 proxy transport requires the proxy to
provide IPv6 egress; UDP also requires a reachable relay and UDP ASSOCIATE support.
These changes add no timers, owner lookups or per-packet allocations.
The TCP regression uses real local IPv4/IPv6 listeners for all destination/proxy
family combinations, verifies destination encoding, differing CONNECT reply
families, stream data and invalid-address cleanup, and extends UID recovery
coverage with matching/mismatching remote proxy ports. App policy cases
cover both transports/families, remote/local proxies, explicit IPv6 blocks,
DNSCrypt forwarding and bypassed apps. These fixtures do not establish remote
proxy connectivity or IPv6 egress on an external network.

The VPN interface MTU is always 1500, including with Tor or an external SOCKS5
proxy enabled. The builder and native TCP MSS calculations use the same constant;
proxy toggles do not change the MTU or require interface replacement for that reason.
Tunnel reads retain the 1500-byte maximum.
The TCP regression checks generated IPv4/IPv6 SYN-ACK MSS bytes in network order:
1460/1440 with or without the external proxy. It toggles proxy
mode in both directions, uses temporary and reused output buffers, and checks
packet checksums independently and allocation/socket balance. The regression
fails against the previous host-order MSS serialization. SYN-ACK fingerprints
intentionally differ from revisions preceding these MTU/MSS corrections; strict
baseline comparisons require a baseline containing the corrections.
The IP regression checks the constant MTU, IPv4/IPv6 MSS, enable/disable transitions,
Tor-only configuration, JNI queries against differing native state, full-size
tunnel reads and allocation cleanup. App `VpnMtuTest` checks the Java/JNI binding
and interface-builder equality without establishing a VPN. Remote SOCKS5 UDP
framing can exceed the relay path MTU for large datagrams; packet-size error
handling remains necessary, and IP/SOCKS fragmentation remains unsupported.

Association state is allocated only on demand, with at most 16 live associations.
UDP proxy admission validates the numeric proxy endpoint, port range 1 through
65535 and credential lengths before allocating association state, reclaiming
capacity or opening sockets. Both credentials may be empty for no authentication;
otherwise both must contain 1 through 127 encoded bytes. Invalid settings fail
locally without evicting an existing flow or changing capacity-scan deadlines.
The parsed endpoint is reused for connection setup; handshake checks remain as
defensive validation. No resolver, timer or work on ordinary direct traffic is added.
Only when admission reaches that limit, one session-list scan closes expired proxy
associations using their unscaled ordinary timeout (20 seconds during negotiation).
If still full, it closes the oldest ready association idle for at least 60 seconds.
An unsuccessful scan starts a 100 ms monotonic cooldown: further full-capacity
admissions are rejected without rescanning until that deadline. Rejections do not
extend it. Successful reclamation starts no cooldown; closing any live association
clears it, and spare slots remain immediately usable. The check runs only during
proxy admission and adds no timer or wake-up. Failed clock reads or readings before
the last scan bypass throttling; unrepresentable deadlines are omitted.
Recent traffic and unexpired handshakes remain protected; otherwise the new flow is
rejected with the existing bounded feedback. Reclaimed records leave the lookup
index so later traffic can reopen immediately, but remain until normal cleanup so
queued epoll events stay safe. Usage accounting follows normal UDP cleanup.
Reclamation sends no errors for old traffic. It adds no timers, periodic
work or allocations. Failed/backward clock reads cannot establish idle eligibility.
Pressure reclamation loses the relay's UDP mapping: later traffic must establish
another association and applications depending on that mapping may be interrupted.
During negotiation or temporary relay send congestion, up to four datagrams are
retained in arrival order, with a 64 KiB payload limit per association. New datagrams exceeding either limit are
dropped without replacing queued packets; empty datagrams count toward the
four-packet limit. After negotiation, EAGAIN/EINTR retains the unsent head and
later traffic appends behind it. Relay EPOLLOUT is enabled only while data remains
queued and removed after draining; each event attempts at most four sends.
EMSGSIZE drops only the affected datagram when the association remains usable.
Queued copies are freed after sending/dropping, failure, timeout or cleanup.
Successful uncongested sends allocate no payload copy. There are no retransmission
timers. Negotiation has a fixed 20-second timeout, then
uses the ordinary UDP idle timeout. All I/O is nonblocking in the existing epoll
loop. Idle control sockets watch for input/closure without EPOLLOUT, polling
timers or keepalives. Scatter/gather sends add the SOCKS header without allocating
a payload copy. Ordinary direct UDP keeps its socket path and session-record size;
disabled-proxy policy callbacks do not read an additional JNI field.

The UDP proxy handshake advances immediately through available request/reply
steps, including the prefix and remaining address of a UDP ASSOCIATE reply.
Normal processing is bounded to eight handshake read/write attempts per callback; short reads and
writes consume that budget, and EAGAIN/EINTR yields immediately. Epoll watches
change only when the required direction changes. The existing terminal-error
drain remains bounded by the handshake buffer so buffered rejection replies keep
their diagnostic precedence. This adds no timers, allocations or direct-traffic work.
Two IPv4/IPv6 regressions cover immediate next-request transmission, combined and
split replies, authentication, maximum-length credentials, interrupted reads,
blocked sends, one-byte writes across the work limit, idle watch masks, forwarded
payloads and complete allocation/socket cleanup. Existing handshake fixtures
expect the next request without an extra writable event.

The relay supports IPv4/IPv6 endpoints and destinations, including a different
relay address family from the TCP proxy. A wildcard relay address uses the TCP
peer's IP of the same family. No authentication and configured username/password
authentication are supported. Numeric relay addresses and unfragmented SOCKS5
datagrams are required: domain-name relay replies and SOCKS fragmentation are
rejected without blocking name resolution or direct fallback. Credentials must
have nonempty username and password when authentication is configured. SOCKS
encapsulation reduces the maximum UDP payload by its 10-byte IPv4 or 22-byte IPv6
destination header within the relay transport's UDP limit; oversized datagrams
are dropped. Connected UDP accepts packets only from the negotiated relay, and
received frames must match the original flow's destination address/port. Short,
truncated, fragmented, reserved-byte and mismatched frames are discarded.

`UdpProxyTest` uses real local TCP control and UDP relay sockets to exercise both
families, cross-family relays, authentication, split replies, datagram framing,
empty payloads, real MSG_TRUNC and recovery, malformed handshakes, refusal,
control EOF, epoll failures, stale events, handshake queue ordering and overflow,
empty queued datagrams, exact byte/count limits, allocation failures with a
partially filled queue, fatal sends while flushing the queue, bounded memory,
timeouts, owner-recovery
switches between proxy/direct/blocked routes, and cleanup.
Two congestion regressions inject temporary send failures over real IPv4/IPv6
relay sockets. They cover handshake retention, EAGAIN/EINTR, empty datagrams,
partial draining, FIFO ordering with later arrivals, overflow, MTU recovery,
allocation/epoll failures, stale events, resource balance and removal of writable
wake-ups after draining. Congestion itself is injected for deterministic coverage.
Three admission regressions cover expired handshakes before idle replacement,
the oldest eligible ready flow, the exact 59/60-second boundary, spare capacity,
recent traffic and pending handshakes, failed/backward clocks, failed allocation
after reclamation, stale relay/control events, surviving relay traffic and complete
socket/allocation cleanup.
Two additional capacity regressions cover burst rejection, the 99/100 ms boundary,
successive successful reclamations, immediate use of freed slots, invalidation
after closure, monotonic zero/failure/backward readings and deadline overflow.
They retain the real socket and allocation-balance checks; the cooldown regression
fails against the earlier unthrottled admission path.
Three configuration regressions cover malformed/empty numeric addresses, invalid
ports, incomplete credentials and missing credential terminators. They verify
rejection before association allocation or connection, preservation of a full
association table and recovery after correcting settings. Real IPv4/IPv6 control
and relay sockets validate no-auth and 127-byte username/password negotiation,
datagram forwarding and complete socket/allocation cleanup. The early-rejection
regression fails against the previous allocation-before-validation path.
JNI fault injection checks the explicit route flag, pending exceptions and
disabled-proxy reset. App `UdpProxyPolicyTest` exercises Tor precedence, external
UDP selection, proxy bypass, explicit Tor UDP exceptions and local DNS forwarding.
These local fixtures do not establish a VPN or validate a third-party proxy server.
Relay input handles at most eight receive attempts per readiness event, sharing
one input buffer across the batch. Invalid and truncated frames consume that
budget too. Remaining datagrams stay readable for the next level-triggered epoll
event. EAGAIN, EINTR and handled MTU errors end the batch; fatal receive errors,
DNS completion and output failures retain the existing close behavior and stop
further reads. No timers, worker threads or additional packet queues are added.
Two real-socket IPv4/IPv6 regressions cover ten-datagram bursts, ordering, empty
payloads, independent output checksums, the eight-attempt limit for valid and
malformed frames, truncation followed by a valid reply, transient errors mid-batch,
fatal receive/output failures and stale events. They exercise both buffer-reuse
modes and verify socket/allocation balance. The burst regression fails against
the previous single-datagram receive path.

Proxy failures identify the failed operation and retain numeric socket errors.
Failure diagnostics share one one-second monotonic budget per native context,
across flows, owners and causes. The first failure is logged immediately; further
failures in that window only increment a saturating counter. The next allowed
failure log contains its current cause and the number of intervening failures
suppressed (reported as a lower bound if the counter saturates). Intervening causes
are not retained. The count is reported only when another failure occurs, with no
timer, background work or allocation. Suppressed failures still send the existing
bounded ICMP feedback and perform normal cleanup. Capacity-limit messages retain
their separate budget. Invalid clock readings bypass throttling; overflow cannot
create an indefinite window. Disabled ERROR logging skips the limiter as well.
Two IPv4/IPv6 regressions cover repeated admission failures, suppressed real
handshake rejection with unchanged feedback/cleanup, the 999/1000 ms boundary,
sharing between owners and causes, suppression counts, clock recovery and counter
saturation. They check errno preservation and socket/allocation balance. Existing
diagnostic-specific fixtures advance a deterministic clock between independent
cases so each cause remains observable. The burst regression fails against the
previous unbounded logger.
Method selection, username/password rejection and UDP ASSOCIATE replies include
their protocol status codes; unsupported UDP ASSOCIATE is reported explicitly.
A rejection followed immediately by control closure is read before handling EOF.
When reset/error readiness accompanies a buffered handshake reply, complete
rejections retain their SOCKS status. Incomplete replies retain the socket error;
successful replies cannot advance negotiation or send queued datagrams after a
known reset. This uses bounded nonblocking reads only on the failure path.
Each failed association reports once, including failure while draining its queue.
Opening failures also return ICMP feedback for the original tunnel packet, using
the existing shared rate limit (burst 20, then one per 100 ms). Association-limit
warnings are limited to once per second per context, without polling or timers.
Known permission, refusal and network failures produce the corresponding ICMP
unreachable code; failures without a suitable ICMP equivalent use host/no-route
unreachable.

Relay MTU errors keep both sockets and the association usable. UDP error queues
are enabled only on the selected relay socket and drained at most eight entries
per event. MTU feedback subtracts the relay's outer IP/UDP and SOCKS headers, then
adds the original IP header (including options/extensions) and UDP header. Mapped
IPv4 relay addresses use an IPv4 socket so framing and MTU accounting agree with
the actual wire protocol. Only a matching SOCKS frame and bounded original-payload
prefix can produce feedback for an asynchronous error. Synchronous EMSGSIZE can
also use a known connected-socket route MTU when queued metadata is unavailable.
The existing independent ICMP MTU rate budget applies.

Missing, malformed, uncorrelated or unusable MTUs are consumed without guessing a
size or closing the association. Translated IPv6 limits below 1280 are omitted;
this does not implement fragmentation or a new path-MTU probing mechanism. These
changes introduce no timers, background work or additional proxy allocations.
Successful sends make no MTU queries; the direct UDP/ICMP error paths are unchanged.

Regressions check all defined nonzero UDP ASSOCIATE reply codes and an unassigned
code, rejection followed by closure, authentication rejection, synchronous and
asynchronous control failures, relay socket/receive errors, single-report cleanup,
and bounded admission feedback and logging. Error replies quote the original
IPv4/IPv6 packet, and socket/allocation balances are checked after failures.
Combined readable/reset events cover complete and split rejections, truncated
replies, method/authentication rejection, and successful replies with a pending
error, including a success prefix read before reset.
MTU regressions cover send, receive and epoll errors for both IP families and
cross-family relays, mapped relay addresses, IP options/extensions, missing and
malformed metadata, quote mismatch, queue-drain bounds, independent checksums,
and successful forwarding after errors. A real IPv6 socket size limit with
fragmentation disabled produces a kernel EMSGSIZE; feedback is checked if the
kernel exposes a usable MTU, and smaller datagrams must still pass. This local
test does not establish end-to-end PMTU discovery through an external proxy.

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
Same-destination UID fallback uses per-context arrays of 256 confirmed
observations and 64 exact discarded-endpoint guards (about 15 KiB plus small
metadata). It matches IP family,
source IP, destination IP and destination port across TCP and UDP; source ports may differ.
Successful Android/proc lookups add or refresh observations before policy and
socket creation, including later authoritative TCP/UDP recovery. Validated app
traffic on a live session with a confirmed owner also refreshes evidence, at most
once per second per session. A per-session slot hint makes an existing-owner
refresh constant work even when other connections interleave updates. Before use,
the hint must be within the live cache and match UID, protocol, address family,
source/destination IP and destination port; expired or replaced entries fall back
to the normal scan, which supplies a new hint. Hits update the newest-write marker
and retain every conflicting owner and loss guard. Session creation/reuse clears
the hint; clock failures invalidate it. The 16-bit hint fits existing structure
padding on ARMv7, ARM64 and x86_64, adding no session allocation size or timers.
Within each TUN batch (at most ten packets), activity refreshes share a lazily
sampled boot-clock timestamp only to skip already throttled updates. Every actual
evidence write and inference still reads the current clock. A slow callback or
suspend can defer a refresh until the next batch but cannot extend stale evidence.
The batch timestamp lives on the event loop's stack; one borrowed context pointer
is cleared before leaving the batch, including read-error and stop paths. No
allocation, timer or idle wake-up is added. The IP regression checks one clock
read for ten repeated refreshes, fresh inference/writes after a long pause,
batch boundaries, standalone calls, conflicts, and clock failure/backwards time
for both families/transports. Event-loop buffer-cleanup tests also check that the
borrowed pointer is cleared on normal and failed reads.
The IP regression checks a full interleaved cache with two endpoint comparisons
per hit, every key mismatch, unset/out-of-range hints, expiry, cache reset and slot
reuse, clock failures/backwards time, conflict retention and resource balance for
both families/transports. TCP requires a current sequence and an ACK within
the acknowledged/sent range, with valid data/FIN bounds; rejected packets and
queue failures do not refresh evidence. Activity-only validation is skipped for
unknown and inferred TCP owners, which cannot seed evidence. The TCP regression
checks that their ACK handling and ownership stay unchanged without those extra
sequence comparisons. Confirmed TCP traffic also skips activity-only validation
when the existing batch timestamp already throttles its refresh. The same inline
predicate is shared with the refresh function, without added clock reads or state.
TCP cases check unchanged ACK handling with no activity sequence comparisons or
clock reads while throttled, normal validation at the one-second boundary, and
missing/invalid/backward samples and first use. A future ACK is still rejected by
normal TCP processing while activity is throttled. UID recovery still performs its own packet validation; an
owner confirmed by recovery can refresh activity in the same packet. UDP refreshes after successful direct
sends or acceptance into the bounded SOCKS queue, excluding overflow and failed
sends. Connection
completion does not remove observations. DNS hostname inference is not included.
Each observation expires five seconds after its latest confirmation or eligible activity, including device
sleep, using `CLOCK_BOOTTIME`. Overflow deadlines use the same clock. Different UIDs
for the same destination remain separate records; inference succeeds only when
all recent matches agree on an app UID. Root, system and the VPN process participate
in conflict detection but cannot themselves be inferred. Guessed session owners
never add observations, and inference never refreshes expiry. Inactive sessions
cannot keep evidence alive. Activity adds at most one boot-clock read per eligible
packet, with throttled checks sharing reads within a TUN batch, and a timestamp
per session, without new owner lookups, allocations, timers or
wake-ups. Existing sessions keep their UID after inference evidence expires.
TCP/UDP activity regressions cover both families, throttling, idle expiration,
cross-protocol inference, conflicting confirmed owners, inferred/unknown owners,
root evidence, invalid packets, send/queue failures, half-closed streams, and
clock failure/backwards recovery; SOCKS round trips check queue admission versus
overflow. Resource accounting remains balanced.
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
reset the populated count, discarded guards and overflow fallback without clearing array storage.
This avoids scanning stale entries after idle periods; stale storage is outside
the populated range and cannot supply evidence. Clock failures and cleanup still
clear the complete cache.
Expired entries are reused first. Evicting a recent observation retains its exact
endpoint and original expiry in the bounded guard array. Only that endpoint's
inference pauses; unrelated retained endpoints remain usable. TCP/UDP share these
guards, and domain inference also rejects discarded endpoints mapped to the same
hostname, including cross-family relationships and DNS learned after eviction.
Guards are matched by full addresses, source and destination port, without hashes.
Refreshing a retained observation does not extend a discarded observation's lifetime.
If all 64 guard slots remain live and another distinct endpoint must be discarded,
a conservative global fallback lasts until the untracked observation's original
expiry. Existing guards are never overwritten to admit another live guard.
Unknown traffic continues through the configured policy.
Overflow regressions cover unrelated exact-IP and domain continuity, conflicting
owners, cross-family/cross-protocol relationships, late DNS mappings, independent
expiry, guard saturation and expired-slot reuse. Reload preserves guard deadlines;
teardown and idle expiry clear them. No allocations, polling or extra owner queries
are added to packet processing.
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
Domain UID fallback runs only when exact-IP inference has no matching owner;
exact-IP conflicts, excluded owners and applicable owner-loss guards do not fall
through to it. It uses passive DNS history and recent confirmed ownership, not
the DNS socket's UID. Inferred traffic never seeds ownership evidence, and an
inferred owner still passes through normal policy and TCP/UDP recovery handling.

Each VPN context holds up to 512 exact hostname/address mappings and 256 DNS
loss guards, alongside 256 ownership observations and 64 owner-loss guards.
Ownership evidence lasts five seconds and is shared across TCP/UDP. Positive DNS
answers retain historical hostname/address associations for 15 minutes using
`CLOCK_BOOTTIME`, independently of their wire TTL. Both the known and unknown
destination mappings must remain within that history window. Only another
observed positive DNS answer refreshes DNS history; confirmed session activity
or an authoritative UID lookup refreshes ownership. Zero TTL at an address or
CNAME revokes its matching history immediately. DNS answer freshness is not
stored or used to resolve names.

Only complete, successful, unfiltered standard A/AAAA responses supply mappings.
The parser validates the whole response before publishing evidence, normalizes
ASCII hostname case, rejects binary labels as inference keys, and follows up to
eight CNAME hops regardless of record order. It ignores unrelated answer owners
and sends no additional DNS queries. Duplicate family/IP pairs share a slot in
the 128-answer buffer, with the shortest address/CNAME TTL taking precedence,
including zero. Parser-normalized batches skip the cache writer's duplicate
check; the general writer still honors zero-TTL duplicates in either order.

More than 128 distinct matching pairs publish no mappings. After validating the
response, the parser rescans its answers and retains loss evidence for every
matching address, including those beyond the buffer. The same buffer supplies
batches of at most 128 discarded answers, with one boot-clock read per batch.
Zero-TTL answers still revoke resident history. Response bytes remain unchanged.

Evicting a retained DNS mapping records its exact hostname, address and original
history deadline. Applicable hostname or destination guards reject domain
inference; unrelated cached domains remain usable. When all 256 DNS guard slots
are live, existing guards and mappings are preserved, affected resident keys
receive local incomplete-evidence deadlines, and admission of new mapping keys
pauses until untracked evidence expires. Existing keys may refresh without
erasing guards. Each loss retains its 15-minute deadline; further losses can
prolong restrictions. DNS pressure does not disable exact-IP inference. The
separate owner-guard overflow can suppress all inference until its untracked
five-second evidence expires.

For each live hostname mapped to the unknown destination, domain inference
requires recent confirmed ownership evidence for the same app UID. Destination
ports must match; same-family evidence must also match source IP, while
cross-family evidence is scoped to the VPN context. All matching hostnames must
agree. A hostname without usable ownership evidence, conflicting or excluded
owners, or an applicable DNS/owner loss guard leaves the UID unknown. Root,
system and the VPN process participate in conflict detection but cannot be
inferred. Expired/revoked mappings supply no ownership association, although
their live incomplete-evidence guards still apply.

The implementation uses bounded arrays without packet-time allocations, timers,
extra wake-ups, DNS transactions or Java/JNI additions. Mapping updates validate
the last matched slot and its successor before falling back to a full scan.
Exact refreshes retain their keys and guard deadlines. Revocation and paused
admission skip insertion bookkeeping; admission is checked per answer because
earlier losses in the batch may pause it. Writers stop tracking the oldest entry
after finding a reusable slot but continue looking for an exact match.

Domain inference scans owners only for the candidate hostname's live mappings.
Destination loss guards are checked once, after finding a usable mapping and
before scanning its owners. Hostname guards share the mapping traversal, and no
UID is returned before all applicable evidence is checked. Worst-case work is
bounded by mappings times owner records, plus per-hostname mapping/guard scans.
Confirmed flows and successful exact-IP inference avoid these domain scans.
Reload retains both caches and their original deadlines; clock failure,
backward reads and teardown clear them. DNS hidden from the VPN cannot supply
mappings. Attribution remains heuristic: unobserved apps may share destinations,
and longer history can increase ambiguity or misattribution risk.

The IP, DNS, TCP/UDP activity and session regressions cover:

- IPv4/IPv6, cross-family and cross-protocol evidence, Android/legacy owner APIs,
  allowed/denied policy, provisional provenance and authoritative recovery.
- Shared hostnames with agreeing, missing, expired, conflicting or excluded
  owners; source/port restrictions, exact-IP priority and context isolation.
- Historical DNS reuse, the exact 15-minute and five-second boundaries, partial
  refresh, zero-TTL revocation, reload retention and failed/backward clocks.
- Duplicate addresses below/at the 128-pair limit, mixed families, reordered
  aliases, malformed/truncated/filtered/failed replies, and oversized responses
  spanning multiple discarded-answer batches without publishing partial mappings.
- Capacity eviction, original guard deadlines, selective losses, saturation,
  admission pauses, exact refresh/revocation while paused and boundary recovery.
- Full-key slot-hint validation, invalid/reused hints, wraparound, reset, earlier
  free slots followed by exact matches, and complete cache-state equivalence
  against ordinary scans.
- Bounded work with full caches and unrelated mappings/losses, including 256
  hostnames sharing one destination. That saturated-cache regression allows at
  most 655,360 address comparisons; ordered eight-address refreshes use at most
  sixteen comparisons, and 514 discarded answer records use at most five clock reads.
- Unchanged packet bytes, policy and resource balance, and absence of temporary
  UID/capacity diagnostic output.

The full run contains 368 tests plus the optional comparison test. Inference
failures retain the existing `Packet uid can not be restored` message.

Locally generated ICMP errors share a per-context token bucket across IPv4/IPv6
and flows: a burst of 20, replenished by one token per 100 monotonic milliseconds.
MTU errors have an independent bucket with the same limits so unreachable floods
cannot starve path-MTU feedback. Echo replies bypass the limiter. Invalid packets
do not consume tokens; permitted attempts consume a token even if output fails.
Suppression happens before message construction, checksum work or output allocation,
with no timers or extra wake-ups. Clock-read failures suppress errors without
refilling, and context cleanup resets both budgets. The ICMP regression covers
both families, shared burst limits, fractional refill, idle refill caps, backward
and failed clocks, cleanup/reuse, independent MTU feedback, echo forwarding,
error-queue draining and allocation/socket balance.
Validation on 2026-11-20 passed all 23 ICMP cases and the full suite on Android 10
ARMv7 with ASan/UBSan and HEAD comparison: 303 passed, eight expected proc skips.
The non-sanitized API 23 build also succeeded for ARMv7, ARM64 and x86_64;
the rate-limit change has not yet been run on Android 6 hardware.

Destination-unreachable errors now contain the original tunnel packet prefix,
family-specific type/code and a checksum over the full ICMP message (including
the IPv6 pseudo-header). Immediate UDP blocks/retries and blocked TCP requests
quote the current packet, bounded so the complete error fits in 576 bytes for
IPv4 or 1280 bytes for IPv6. Async UDP errors retain the latest successfully sent
packet's first 112 bytes inside existing session storage: no per-flow allocation
or increase to `ng_session` size on ARMv7/ARM64. Redirected flows quote the original
application endpoints. If extension headers push the UDP header past that bound,
the async quote is invalidated and no unidentifiable error is emitted. SO_ERROR
does not identify which datagram failed, so the fallback quotes the latest sent
datagram of that flow.
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
UDP and ping sockets now request IP_RECVERR/IPV6_RECVERR. Queued upstream errors
produce port, host, network or administratively prohibited unreachables, or TTL
exceeded replies. EMSGSIZE produces IPv4 fragmentation-needed/IPv6 Packet Too Big
with the kernel's MTU and keeps the session/socket open for smaller retries.
The reported MTU includes any original IP options/extension headers removed by
socket forwarding. Ping retains the original request prefix before rewriting its
identifier, also inside existing session storage. Error queues are drained only
on error events, receive errors or synchronous EMSGSIZE, at most eight entries per invocation;
remaining entries keep EPOLLERR ready. No extra timers or per-flow allocations
are introduced. TCP retains its existing reset handling.
Four additional ICMP cases cover both families and protocols, MTU/TTL/unreachable
bytes and checksums, smaller retries on the same socket, IP options/extensions,
malformed/truncated ancillary data, mismatched payloads, zero/missing MTUs,
bounded draining and resource balance. Real IPv4/IPv6 loopback UDP sockets verify
kernel error-queue delivery. MTU errors use controlled kernel-metadata injection;
these tests do not establish end-to-end PMTU discovery over an external network.
When available, queued payload prefixes (and ping sequence numbers) are checked
against the retained request. Empty/identical payloads cannot uniquely identify
older datagrams; only the latest bounded quote is retained. Long extension headers
can still prevent an asynchronous reply. Cross-family redirects omit MTU replies
because copying the upstream MTU would describe a different packet format.
Unsupported RECVERR options retain SO_ERROR fallback; that fallback cannot supply
a reliable MTU or distinguish TTL expiry. Such EMSGSIZE errors keep the flow alive
without inventing an MTU. No additional DNS or ICMP traffic is sent upstream.
An additional IPv4/IPv6 UDP/ping regression injects receive errors after an EPOLLIN
snapshot while a real local reply remains readable. It verifies MTU bytes/checksums,
the unchanged session/socket and subsequent reply delivery, including when MTU
metadata is missing. Fatal queued errors still close the flow; ordinary network
errors without queued metadata use the retained quote. Transient EAGAIN does not
drain errors, logging cannot change the saved errno decision, and resources balance
after every case. Receive-error buffers are released through the normal cleanup path.
Routine ping forwarding, normal echo replies, session creation and idle cleanup
log at DEBUG. Kernel-rewritten echo IDs do not turn normal replies into warnings.
Unexpected reply types/codes are dropped with WARN diagnostics; generated ICMP
errors and socket/packet failures retain WARN/ERROR diagnostics. The existing lazy
logging gate avoids address formatting when the selected log level suppresses
these messages. Logging priorities do not alter packet bytes.
Ping replies use recvmsg so MSG_TRUNC is detected before any ID or checksum rewrite.
Only complete IPv4 echo replies (type 0) and IPv6 echo replies (type 129), with
code zero and at least eight bytes, are forwarded. The socket limits include the
ICMP header: 65,515 bytes for IPv4 and 65,535 bytes for IPv6 without jumbograms.
Truncated, short and unsupported replies are dropped without stopping the session.
Two additional ICMP regressions cover wrong-family replies, echo requests received
as replies, error/control/unknown types, nonzero codes, maximum and adjacent lengths,
and valid replies after rejection. Real local datagram sockets exercise truncation
and recovery. Maximum-size receives use controlled callbacks to avoid large
contiguous socket-buffer allocations on old kernels; complete output packets are
checked byte-for-byte with independent checksums through a local stream socket.
Both buffer-reuse modes verify allocation/socket balance. Existing receive-error
regressions also exercise recvmsg, preserving MTU recovery and fatal-error handling.
Two real ping-socket regressions send echo requests to IPv4 127.0.0.1 and IPv6 ::1
through handle_icmp, wait with real epoll, peek at the kernel reply, then forward
it through check_icmp_socket to a local tunnel surrogate. They verify the bare
ICMP reply format, the kernel-assigned identifier and restoration of app ID zero,
sequence/payload preservation, reversed IP addresses and independent checksums.
Each family covers both buffer modes, repeated requests on the same socket,
8/9/40/1400-byte messages and allocation/socket balance after cleanup; the IPv6
40-byte case includes a Hop-by-Hop header in the tunnel request. Ping socket
creation, sendto and recvmsg are real; VPN protect remains a test stub, so this
checks local kernel interoperability rather than VPN routing or external reachability.
Loopback permission/kernel failures fail visibly rather than silently skipping.
On 2026-11-20, both cases and the full suite passed on Android 10 ARMv7 with
ASan/UBSan and HEAD comparison: 305 passed, eight expected proc skips. The API 23
build succeeded for ARMv7, ARM64 and x86_64; these new round-trip cases have not
yet been run on Android 6 hardware.

Android 6/API 23 compatibility uses the existing non-sanitized test configuration
(`-PnativeSanitizers=false`); sanitizer builds require API 27. The receive path uses
recvmsg, msghdr and MSG_TRUNC already provided by Android 6, with no newer platform
API requirement. Compatibility validation on 2026-11-20 built API 23 libraries for
ARMv7, ARM64 and x86_64 (confirmed in their ELF Android notes), then ran the full
suite on the connected Android 10 ARMv7 device: 302 passed and eight expected proc
skips. These device runs do not establish coverage of every vendor kernel.
The connected Meizu M5 Note subsequently passed the full 310-test suite on
Android 7.0 ARM64, with no skips, using the non-sanitized API 23 build and HEAD
baseline. All 22 ICMP cases passed, including real IPv4/IPv6 UDP error-queue
delivery, reply type/code rejection, maximum-size/truncation handling, buffer
reuse, receive-side MTU recovery and epoll-registration failure cleanup.
Initial loopback UDP sends returned EPERM on this phone; both families passed
on retry before the full run. The loopback fixtures report the IP family and
send errno where needed to distinguish environment restrictions from
packet-handling failures. A Mi 4 running Android 6.0.1 ARMv7 also passed the full
310-test suite with no skips, using the same non-sanitized API 23 build and HEAD
baseline. All 22 ICMP cases passed, including real IPv4/IPv6 UDP error-queue
delivery. Initial loopback sends on this phone also returned EPERM; both real
UDP cases passed on retry before the full run.
TCP packet fingerprint fixtures pin the initial sequence before the handshake;
other process-wide random-generator users cannot change comparison results.

A START or RELOAD that times out waiting for the previous worker registers one
retry. It waits for that worker to exit and native cleanup to finish, then posts
the original command back to the VPN handler. A newer command, service destruction,
or VPN permission revocation cancels the pending retry. Ordinary configuration
and startup errors do not trigger this retry.

RELOAD keeps the same context's bounded UID observations and DNS mappings while
closing all session sockets and releasing session/queue storage. Cleanup retains
the caches in place without copies, allocations, refreshed timestamps or extended
expiry. Conflicting UID observations and DNS/observation overflow guards survive
alongside positive evidence. Normal STOP, service replacement/destruction and
failed native startup discard the caches. Startup's redundant cleanup and delayed
same-owner reload cleanup preserve them; a timed-out full STOP remembers that its
eventual cleanup must discard them even if another START arrives.
Session regressions call the JNI cleanup entry point with both preservation modes
for IPv4/IPv6, checking socket/allocation balance, direct/domain inference, original
expiry, conflicts and overflow metadata. NativeTunnel tests cover normal reload,
full stop/destruction and both reload/full-stop timeout paths. The handler timeout
regression also verifies that caches survive the retried production RELOAD path.
An app instrumentation case initializes a real native context, invokes both
boolean JNI cleanup modes through the VM, then destroys it without opening a VPN.

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
notification. VPN command completion leaves garbage-collection scheduling to
Android; descriptor and native worker cleanup remain explicit. Run them with
the app's matching debug variant, for example:

```sh
./gradlew :tordnscrypt:connectedFdroidArmv7aDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=pan.alexander.tordnscrypt.vpn.service.ServiceVPNHandlerTest
```

Both app build files register `tests/handler/src/androidTest/java` as an
`androidTest` source directory; the checkout ignores the default `src/androidTest`.
These app tests are separate from the `:native-tests` suite above. Select the
appropriate app build/signing configuration for your checkout when running them.

`pan.alexander.tordnscrypt.vpn.RuleInventoryTest` covers inventory reuse across
VPN reloads. Each handler caches only UID/package/display-name identities, without
icons or a Context, and creates fresh rules from current Tor/proxy preferences.
Firewall allowlists are still selected from current preferences on every reload.
Package events, including updates that do not reload the VPN, and receiver
registration invalidate the inventory. On API 24 and later, profile
availability/unlock events also invalidate without restarting the VPN; those
actions are neither registered nor handled on API 23. Profile membership, multi-user support,
root availability, cross-profile permission and locale are checked on each read.
An asynchronous cross-profile permission grant therefore refreshes the next read.
Complete local-only inventories are cached even with multiple profiles and a
granted cross-user permission. A profile contributes to the caching decision only
when discovery actually returns application UIDs from it. This also allows reuse
in Google Play builds without the permission. Synthetic kernel/root/native-system
identities do not count as cross-profile applications. Inventories containing
actual foreign application UIDs keep fresh enumeration because this profile's
package broadcasts cannot invalidate the others reliably. If no foreign apps are
visible, newly accessible apps are discovered on the next normal invalidation,
profile/permission change, or handler recreation; no polling is added.
Partial discovery results remain usable for the current reload but are not cached.
Only the declared `INTERACT_ACROSS_USERS` permission is checked; no new permission
is requested. Invalidation during a scan
leaves that scan's version stale for the next read; failures and empty manager
results are retried. The cache lives with the handler and adds no packet work,
timer or periodic scan. Cases cover preference changes, mutable result isolation,
cache lifetime, lazy invalidation, scan races/failures, scope changes, local-only
multi-profile reuse (including a work-profile context), cross-profile fallback
and recovery, incomplete snapshots, package/profile
dispatch and scope-dependent reuse of a real installed-app enumeration. Device package-manager
broadcast delivery and actual profile creation/unlock are not simulated by these tests.
The real-inventory case reports visible profiles, cross-user permission, scan
completeness and application counts per user under the `InventoryDeviceTest` tag.
It expects incomplete snapshots to be scanned again even if no foreign UID was
returned. Optional runner arguments `inventoryProbeUser` and
`inventoryProbePackage` probe UID-to-package visibility for a package known in
the current profile; results are diagnostic because private-profile access can
be denied by the device. These probes do not change permissions or profile state.
Inventory fixtures and per-user counters use API 23-compatible map lookups,
without Java collection APIs that require a newer Android runtime.

`pan.alexander.tordnscrypt.utils.apps.InstalledAppsTest` exercises
the production package-query and profile-mapping helpers with Android framework
package-manager mocks. Authorized discovery explicitly requests `MATCH_ANY_USER`
(a system flag passed through the public query API), instead of relying on older
Android's implicit expansion of `MATCH_UNINSTALLED_PACKAGES`. A rejected query
falls back to local candidates and marks the scan incomplete; authorized owner
lookup exceptions also prevent caching. UID mapping uses the target user and the
source UID's app ID, skips the current profile and includes the parent profile
when called from a work profile. Cases cover work-only candidates, denied-query
recovery, system/secondary-profile UIDs, overflow, null/empty owners and partial
lookup failures. Framework mock stubs are compile-only test inputs from the SDK;
the test APK requests the optional framework mock library. Newer-Android query
behavior is modeled on older devices; modern-device validation remains necessary.

Discovery retains installed apps (including disabled and force-stopped apps) and,
on API 35+, packages explicitly marked archived. Retained uninstall data is not
treated as an archive merely because `FLAG_STOPPED` is set. Broad package queries
remain useful for identifying other-profile candidates, but excluded records do
not contribute local names, UI rows or VPN rules. Disabled and archived states
reach both app-list adapters through string resources. A shared UID is archived
only when all included members are archived; any enabled member prevents a
disabled label. Synthetic system identities remain available.
Other-profile metadata uses the profile-specific `LauncherApps` query on API 26+
when accessible. Its result is filtered by installation/archive state. If metadata
is unavailable, UID ownership must confirm the exact package before inclusion;
local archive/disabled flags are not copied to that profile. Such fallback rows
have no state label because the state is unknown. Optional metadata denial does
not invalidate a successful ownership fallback, including confirmed absence;
ownership-query failures and earlier enumeration failures still leave the scan
incomplete. Older archive stubs without a
public archive marker cannot reliably receive an Archived label.
Each other profile is also queried once per scan with
`LauncherApps.getActivityList(null, profile)`. This supplements the candidates
with launchable work-only apps even without cross-user permission or a matching
main-profile installation. The returned application UID is used directly.
Multiple activities for one package are deduplicated, while different packages
sharing a UID still merge their names and state. Own-profile entries and records
with a UID belonging to a different profile are excluded. Launcher access denial
keeps the existing discovery results usable; unexpected service failures leave
the scan incomplete. No polling, additional permission or retained inventory is
introduced. Launcher enumeration does not guarantee coverage of disabled,
non-launchable or hidden apps, so the existing candidate/ownership path remains.
`InstalledAppsTest` covers real discovery with controlled package-manager inputs:
uninstalled stopped records, disabled and force-stopped installed apps, shared-UID
merging in either order, profile ownership/state isolation, and API 35+ explicit
archive state. Metadata-denial regressions cover successful/absent ownership,
ownership-query denial and preservation of an earlier incomplete scan; these
cases require API 26+. Archive-specific cases skip on older devices. Refresh preserves
user selections while replacing rows whose archive/disabled state changed.
Direct-profile cases cover work-only packages with no cross-user grant, actual
UID/selection preservation, activity deduplication, shared UIDs, profile and
installation filtering, independent profile failures and earlier incomplete
scans. A real current-profile launcher query checks the public API path on the
connected device; controlled profile sources do not establish visibility into
an actual work profile or Secure Folder.
Name sorting in discovery and both app lists puts selected apps first regardless
of state, followed by unselected regular apps, disabled apps and archived apps.
Selected apps retain their existing flag priorities, followed by alphabetical
order without state subgroups. Unselected groups are alphabetical.
Discovery prioritizes active apps;
Tor/proxy lists prioritize VPN bypass (outside root mode), Tor selection and
direct UDP; the firewall prioritizes bypass outside root mode, newly installed apps and allowed
traffic. Firewall UID sorting uses the same selection and unselected-state
priorities, then orders by UID. Tor/proxy UID sorting retains its existing behavior.
`AppSortingTest` covers these groups, selection priorities, case-insensitive names
with equal selections, archived-state precedence, firewall UID state grouping
and unchanged Tor/proxy UID sorting. Both firewall comparators ignore bypass
flags in root mode, including for state grouping; VPN allowance counts as
selected only in root mode. Mode-specific tests cover both sorting methods.

Root permission requests target the app's own user. One request may be pending
per process; dispatch failure, queue eviction and shutdown release it for retry.
A private completion receiver checks the actual permission instead of trusting
command output. A verified grant invalidates the inventory, requests a rules
refresh if modules are running, and refreshes open app lists while preserving
unsaved selections. Receivers use the application context or unregister with
their UI owner. No polling or additional permission is introduced. A scan that
runs before a requested grant completes is not cached when other profiles exist.

`CrossUserPermissionTest` covers concurrent requests, rejected/exceptional
dispatch, completion without a grant, retry, stale/duplicate callbacks and refresh
failure. `RootCommandsDispatchTest` checks dispatch feedback and completion
receiver parcel delivery without starting a service. `RootExecutorQueueTest`
checks bounded queue overflow and shutdown release, including sends after closure,
without opening a root shell. `FirewallPermissionRefreshTest` exercises a real
inventory refresh and preservation of unsaved selections, followed by ViewModel
cleanup. `AppsRefreshTest` checks that Tor/proxy list refresh preserves edits
held in a filtered list's backing collection. Refresh keeps the current list
editable and saveable until a complete replacement is published on the main
thread; failed, empty or incomplete refresh scans keep the previous snapshot.
Both app-list regressions interrupt real discovery at its scan lock to check
failure recovery. Firewall retains its save prompt during discovery, and Tor's
`onStop()` saves edited routing/direct-UDP/bypass selections during the blocked
scan and preserves them after failure. Firewall baseline loading, progress
callbacks and list publication run on the main thread; only discovery runs on
the worker. Refresh does not reread or mutate the saved permission baseline.
Tests check baseline/progress thread ownership and preservation of that baseline
while save checks overlap a blocked refresh. These controlled tests do not establish
that a particular device's su manager will authorize the automatic grant; that
requires a rooted-device check.

`CrossUserPermissionDeviceTest` is an opt-in integration check for a rooted phone
with a running, accessible secondary profile. Use an isolated app whose application
ID ends in `.profilescopetest`, begin with `INTERACT_ACROSS_USERS` denied, and pass
`-Pandroid.testInstrumentationRunnerArguments.crossUserGrantProbe=true` together
with its class filter. Approve the isolated app's su request if prompted. The test
uses the production root service to grant only the declared permission, verifies
inventory invalidation and exactly one refresh notification, checks foreign UIDs
in the refreshed UI and VPN rules, and preserves an unsaved selection. It does
not start a VPN or the modules service. Older Android versions may already expose
foreign apps before the grant, so an increased app count is not required. The test
restores preferences and closes its root service; uninstall the isolated app after
the run to remove its permission. Without the opt-in argument, this case is skipped.

`pan.alexander.tordnscrypt.modules.ModulesReceiverPackageTest` exercises package
change handling and records the real VPN helper's service requests. In VPN mode,
`PACKAGE_REMOVED` and `PACKAGE_ADDED` events with `EXTRA_REPLACING=true` do not
reload the VPN. Ordinary installs and uninstalls retain their immediate reload
requests. Six cases cover both replacement
events, replacement addition without a preceding removal, ordinary installs and
uninstalls, absent replacement extras and overlapping updates mixed with other
package changes. The tests do not broadcast package changes or start a VPN. No timer,
pending-package collection or additional work on packet processing is introduced.

`ModulesReceiverRootPackageTest` covers root-mode package events with paused
dispatchers: replacements invalidate the installed-app inventory and retain name
refresh, but do not schedule an iptables update. Actual installs and uninstalls,
including events without the replacement extra, still force an update when the
optional refresh setting is disabled. Replacement events do not suppress a
subsequent real uninstall. These tests run no root commands or discovery tasks;
they verify scheduling, not application of rules to the device's firewall.

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
cleanup retries. Omit the class filter above to run the complete app
instrumentation suite.
