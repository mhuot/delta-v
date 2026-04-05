# Delta-V Monitor, Collector & Detector Inventory

> **Purpose:** Decide which monitors, collectors, and detectors to carry forward into Delta-V vs. drop with legacy Horizon. This inventory informs the follow-up PR that replaces ServiceLoader + reflection with Spring Boot-native `@Bean` registration.
>
> **Voting:** Check the box next to your name for each item you want to **keep**. Leave unchecked to drop. Comment on any item you want to discuss.

---

## Context

PR #112 (merged 2026-04-04) added ServiceLoader + reflection fallback registration for 26 detector factories. The current approach carries forward Karaf-era patterns (ServiceLoader, `EXPLICIT_*` reflection arrays, manual DI via SmartLifecycle). Before we clean this up, we need to decide **what to keep** so we only Spring-Boot-ify what survives.

The same question applies to monitors and collectors, which use the same registration pattern.

---

## Monitors

### Tier 1: Currently Registered in Delta-V (EXPLICIT_MONITORS)

These 9 monitors are explicitly wired into `LocalServiceMonitorRegistry` and are used by Delta-V E2E tests today.

#### IcmpMonitor — ICMP ping — Rec: **Keep**
> Fundamental availability check
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SnmpMonitor — SNMP polling — Rec: **Keep**
> Core network monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### TcpMonitor — TCP connect — Rec: **Keep**
> Generic port availability
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpMonitor — HTTP — Rec: **Keep**
> Web service monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpsMonitor — HTTPS — Rec: **Keep**
> Web service monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DnsMonitor — DNS resolution — Rec: **Keep**
> Infrastructure monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SshMonitor — SSH — Rec: **Keep**
> Server availability
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SSLCertMonitor — TLS certificate expiry — Rec: **Keep**
> Essential ops — cert rotation alerts
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PageSequenceMonitor — Multi-step HTTP transactions — Rec: **Keep**
> Widely used for web app monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

### Tier 2: Available via ServiceLoader (poller-monitors-core)

These are loaded from `features/poller/monitors/core` via ServiceLoader. Most users never configure them.

#### AvailabilityMonitor — Always UP — Rec: **Keep**
> Used for passive/manual status
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### ActiveMQMonitor — ActiveMQ broker — Rec: **Evaluate**
> JMS monitoring — still relevant?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### BgpSessionMonitor — BGP via SNMP — Rec: **Keep**
> Network infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### CiscoIpSlaMonitor — Cisco IP SLA via SNMP — Rec: **Evaluate**
> Cisco-specific but SNMP-based
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### CiscoPingMibMonitor — Cisco Ping MIB — Rec: **Evaluate**
> Cisco-specific but SNMP-based
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DiskUsageMonitor — Disk via SNMP (hrStorage) — Rec: **Keep**
> Common server monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DNSResolutionMonitor — Forward DNS lookup — Rec: **Keep**
> Complements DnsMonitor
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DskTableMonitor — Net-SNMP dskTable — Rec: **Keep**
> Linux server monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### FtpMonitor — FTP — Rec: **Evaluate**
> Declining protocol
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HostResourceSwRunMonitor — Process via SNMP (hrSWRun) — Rec: **Keep**
> Common server monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpPostMonitor — HTTP POST — Rec: **Keep**
> API monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### ImapMonitor — IMAP — Rec: **Evaluate**
> Email infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### ImapsMonitor — IMAPS — Rec: **Evaluate**
> Email infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JDBCMonitor — JDBC connect — Rec: **Keep**
> Database monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JDBCQueryMonitor — JDBC query result — Rec: **Keep**
> Database monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JDBCStoredProcedureMonitor — JDBC stored proc — Rec: **Evaluate**
> Niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JolokiaBeanMonitor — JMX via Jolokia HTTP — Rec: **Keep**
> Modern JMX monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### Jsr160Monitor — JMX via JSR-160 RMI — Rec: **Evaluate**
> Legacy JMX — Jolokia preferred
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LaTableMonitor — Net-SNMP laTable (load avg) — Rec: **Keep**
> Linux server monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LdapMonitor — LDAP — Rec: **Evaluate**
> Directory services
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LdapsMonitor — LDAPS — Rec: **Evaluate**
> Directory services
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LogMatchTableMonitor — Net-SNMP logMatch — Rec: **Evaluate**
> Niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### MailTransportMonitor — SMTP+POP3 round-trip — Rec: **Evaluate**
> Email infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### MemcachedMonitor — Memcached — Rec: **Evaluate**
> Cache infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### MinaSshMonitor — SSH (Apache MINA impl) — Rec: **Evaluate**
> Duplicate of SshMonitor?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NrpeMonitor — Nagios NRPE — Rec: **Evaluate**
> Nagios compatibility
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NtpMonitor — NTP — Rec: **Keep**
> Time infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### Pop3Monitor — POP3 — Rec: **Evaluate**
> Email infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PrTableMonitor — Net-SNMP prTable (process) — Rec: **Keep**
> Linux server monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PtpMonitor — IEEE 1588 PTP via SNMP — Rec: **Evaluate**
> Precision time protocol
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SmtpMonitor — SMTP — Rec: **Evaluate**
> Email infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### StrafePingMonitor — ICMP jitter/loss — Rec: **Keep**
> Network quality monitoring
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### TrivialTimeMonitor — RFC 868 time — Rec: **Drop**
> Obsolete protocol
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### WebMonitor — HTTP (simple) — Rec: **Evaluate**
> Overlaps HttpMonitor
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

### Tier 3: Drop Candidates (dead tech, ultra-niche, or security concerns)

#### BSFMonitor — Bean Scripting Framework — Rec: **Drop**
> Dead tech (Rhino/BeanShell)
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### CitrixMonitor — Citrix ICA — Rec: **Drop**
> Dead tech
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DominoIIOPMonitor — Lotus Domino IIOP — Rec: **Drop**
> Dead tech (IBM Notes EOL)
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LoopMonitor — Loopback (always passes) — Rec: **Drop**
> Testing only
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NetScalerGroupHealthMonitor — Citrix NetScaler SNMP — Rec: **Drop**
> Ultra-niche vendor
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### OmsaStorageMonitor — Dell OMSA storage SNMP — Rec: **Drop**
> Legacy hardware mgmt
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### OpenManageChassisMonitor — Dell OMSA chassis SNMP — Rec: **Drop**
> Legacy hardware mgmt
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PercMonitor — Dell PERC RAID SNMP — Rec: **Drop**
> Legacy hardware mgmt
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SmbMonitor — SMB/CIFS — Rec: **Drop**
> Niche, security concerns
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SystemExecuteMonitor — Shell command execution — Rec: **Drop**
> Security risk
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### Win32ServiceMonitor — Windows WMI service — Rec: **Drop**
> Requires local WMI agent
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

### Tier 4: Separate ServiceLoader modules (not in poller-monitors-core)

#### WsManMonitor — features/wsman — Rec: **Drop**
> WS-Management — niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### WmiMonitor — opennms-wmi — Rec: **Drop**
> Windows WMI — niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JCifsMonitor — protocols/cifs — Rec: **Drop**
> CIFS/SMB file shares
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### RadiusAuthMonitor — protocols/radius — Rec: **Evaluate**
> RADIUS auth testing
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SeleniumMonitor — protocols/selenium — Rec: **Drop**
> Headless browser — heavyweight
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PassiveServiceMonitor — features/poller/api — Rec: **Keep**
> Passive status (used by E2E)
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

## Collectors

#### SnmpCollector — features/collection/snmp-collector — Rec: **Keep**
> Core SNMP data collection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SnmpCollectorNG — features/collection/snmp-collector — Rec: **Keep**
> Next-gen SNMP collector
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpCollector — features/collection/collectors — Rec: **Keep**
> HTTP metric scraping
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### PrometheusCollector — features/prometheus-collector — Rec: **Keep**
> Modern metric scraping
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### JdbcCollector — features/jdbc-collector — Rec: **Keep**
> SQL-based collection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### Jsr160Collector — features/collection/collectors — Rec: **Evaluate**
> JMX collection — Jolokia preferred?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### WsManCollector — features/wsman — Rec: **Drop**
> WS-Management — niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### WmiCollector — opennms-wmi — Rec: **Drop**
> Windows WMI — niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### XmlCollector — protocols/xml — Rec: **Evaluate**
> XML data sources — some users depend on it
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### TcaCollector — features/juniper-tca-collector — Rec: **Drop**
> Juniper TCA — very niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

## Detectors

### Keep

#### IcmpDetector — IcmpDetectorFactory — opennms-detector-simple — Rec: **Keep**
> Fundamental
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SnmpDetector — SnmpDetectorFactory — opennms-detector-simple — Rec: **Keep**
> Fundamental — requires SnmpAgentConfigFactory injection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### TcpDetector — TcpDetectorFactory — opennms-detector-lineoriented — Rec: **Keep**
> Generic port detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpDetector — HttpDetectorFactory — opennms-detector-lineoriented — Rec: **Keep**
> Web service detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### HttpsDetector — HttpsDetectorFactory — opennms-detector-lineoriented — Rec: **Keep**
> Web service detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### FtpDetector — FtpDetectorFactory — opennms-detector-lineoriented — Rec: **Keep**
> FTP detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DnsDetector — DnsDetectorFactory — opennms-detector-datagram — Rec: **Keep**
> DNS infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NtpDetector — NtpDetectorFactory — opennms-detector-datagram — Rec: **Keep**
> Time infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SshDetector — SshDetectorFactory — opennms-detector-ssh — Rec: **Keep**
> Server detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### WebDetector — WebDetectorFactory — opennms-detector-web — Rec: **Keep**
> HTTP detection (alt impl)
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LoopDetector — LoopDetectorFactory — opennms-detector-simple — Rec: **Keep**
> Used for cloud service detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

### Evaluate

#### Pop3Detector — Pop3DetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Email — still relevant?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### SmtpDetector — SmtpDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Email — still relevant?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### ImapDetector — ImapDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Email — still relevant?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### ImapsDetector — ImapsDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Email — still relevant?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LdapDetector — LdapDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Directory services
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### LdapsDetector — LdapsDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Directory services
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NrpeDetector — NrpeDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Nagios compatibility
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### MemcachedDetector — MemcachedDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> Cache infrastructure
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### TrivialTimeDetector — TrivialTimeDetectorFactory — opennms-detector-lineoriented — Rec: **Evaluate**
> RFC 868 — obsolete?
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### Jsr160Detector — Jsr160DetectorFactory — opennms-detector-jmx — Rec: **Evaluate**
> JMX detection
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

### Drop

#### SmbDetector — SmbDetectorFactory — opennms-detector-simple — Rec: **Drop**
> SMB/CIFS — niche
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### MSExchangeDetector — MSExchangeDetectorFactory — opennms-detector-lineoriented — Rec: **Drop**
> Dead tech
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### CitrixDetector — CitrixDetectorFactory — opennms-detector-lineoriented — Rec: **Drop**
> Dead tech
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### DominoIIOPDetector — DominoIIOPDetectorFactory — opennms-detector-lineoriented — Rec: **Drop**
> Lotus Notes — dead tech
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

#### NotesHttpDetector — NotesHttpDetectorFactory — opennms-detector-lineoriented — Rec: **Drop**
> Lotus Notes — dead tech
- [ ] indigo423
- [ ] mhuot
- [ ] pbrane

---

## Voting Instructions

1. **Check the box** next to your name for each item you want to **keep** in Delta-V
2. **Leave unchecked** for items you're okay **dropping**
3. **Comment** on this issue if you want to discuss a specific item or change the recommendation
4. Items with 2+ votes to keep will be carried forward; items with 0-1 votes will be dropped

## What Happens Next

Once voting is complete, the follow-up spec will:
1. Replace ServiceLoader + reflection with Spring `@Bean` factory methods for kept items
2. Remove dropped modules from daemon-boot POMs
3. Eliminate the SmartLifecycle workaround for SNMP detector factories
4. Consolidate duplicated POM exclusions
