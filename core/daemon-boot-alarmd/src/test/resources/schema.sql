-- ============================================================================
-- DDL for Alarmd integration tests
--
-- Derived from Jakarta entity annotations in:
--   core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/
--
-- Tables cover only the entities mapped with jakarta.persistence annotations
-- that Hibernate 7 will discover via @EntityScan.
-- ============================================================================

-- -------------------------------------------------------
-- Sequences
-- -------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS alarmsNxtId    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS catNxtId       START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS memoNxtId      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS serviceNxtId   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS nodeNxtId      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS opennmsNxtId   START WITH 1 INCREMENT BY 1;

-- -------------------------------------------------------
-- monitoringLocations (referenced by node.location FK)
-- Normally managed by the legacy model, but needed as a
-- target for the foreign key from the node table.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS monitoringLocations (
    id              VARCHAR(255) NOT NULL,
    monitoringArea  VARCHAR(255) NOT NULL,
    geolocation     VARCHAR(255),
    longitude       FLOAT,
    latitude        FLOAT,
    priority        BIGINT,
    CONSTRAINT pk_monitoringLocations PRIMARY KEY (id)
);

-- -------------------------------------------------------
-- monitoringSystems  (OnmsMonitoringSystem / OnmsDistPoller)
-- Single-table inheritance: discriminator column = "type"
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS monitoringSystems (
    id              VARCHAR(255) NOT NULL,
    type            VARCHAR(31)  NOT NULL,
    label           VARCHAR(255),
    location        VARCHAR(255) NOT NULL,
    last_updated    TIMESTAMP,
    last_checked_in TIMESTAMP,
    CONSTRAINT pk_monitoringSystems PRIMARY KEY (id)
);

-- Element collection for MonitoringSystem properties
CREATE TABLE IF NOT EXISTS monitoringSystemsProperties (
    monitoringSystemId VARCHAR(255) NOT NULL,
    property           VARCHAR(255) NOT NULL,
    propertyValue      VARCHAR(255),
    CONSTRAINT fk_msp_system FOREIGN KEY (monitoringSystemId)
        REFERENCES monitoringSystems(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- node  (OnmsNode)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS node (
    nodeId            INTEGER      NOT NULL DEFAULT nextval('nodeNxtId'),
    nodeCreateTime    TIMESTAMP    NOT NULL,
    nodeParentID      INTEGER,
    nodeType          VARCHAR(1),
    nodeSysOID        VARCHAR(256),
    nodeSysName       VARCHAR(256),
    nodeSysDescription VARCHAR(256),
    nodeSysLocation   VARCHAR(256),
    nodeSysContact    VARCHAR(256),
    nodeLabel         VARCHAR(256) NOT NULL,
    nodeLabelSource   VARCHAR(1),
    nodeNetBIOSName   VARCHAR(16),
    nodeDomainName    VARCHAR(16),
    operatingSystem   VARCHAR(64),
    lastCapsdPoll     TIMESTAMP,
    foreignId         VARCHAR(255),
    foreignSource     VARCHAR(255),
    location          VARCHAR(255) NOT NULL,
    last_ingress_flow TIMESTAMP,
    last_egress_flow  TIMESTAMP,
    CONSTRAINT pk_node PRIMARY KEY (nodeId),
    CONSTRAINT fk_node_parent FOREIGN KEY (nodeParentID)
        REFERENCES node(nodeId) ON DELETE SET NULL,
    CONSTRAINT fk_node_location FOREIGN KEY (location)
        REFERENCES monitoringLocations(id)
);

-- pathOutage  (@SecondaryTable for OnmsNode)
CREATE TABLE IF NOT EXISTS pathOutage (
    nodeId                  INTEGER NOT NULL,
    criticalPathIp          VARCHAR(255),
    criticalPathServiceName VARCHAR(255),
    CONSTRAINT pk_pathOutage PRIMARY KEY (nodeId),
    CONSTRAINT fk_pathOutage_node FOREIGN KEY (nodeId)
        REFERENCES node(nodeId) ON DELETE CASCADE
);

-- node_metadata (ElementCollection for OnmsNode)
CREATE TABLE IF NOT EXISTS node_metadata (
    id      INTEGER      NOT NULL,
    context VARCHAR(256) NOT NULL,
    key     VARCHAR(256) NOT NULL,
    value   TEXT,
    CONSTRAINT fk_node_metadata FOREIGN KEY (id)
        REFERENCES node(nodeId) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- categories  (OnmsCategory)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    categoryid          INTEGER      NOT NULL DEFAULT nextval('catNxtId'),
    categoryName        VARCHAR(255) NOT NULL UNIQUE,
    categoryDescription VARCHAR(255),
    CONSTRAINT pk_categories PRIMARY KEY (categoryid)
);

-- category_node  (ManyToMany join table: Node <-> Category)
CREATE TABLE IF NOT EXISTS category_node (
    nodeId      INTEGER NOT NULL,
    categoryId  INTEGER NOT NULL,
    CONSTRAINT pk_category_node PRIMARY KEY (nodeId, categoryId),
    CONSTRAINT fk_cn_node FOREIGN KEY (nodeId)
        REFERENCES node(nodeId) ON DELETE CASCADE,
    CONSTRAINT fk_cn_category FOREIGN KEY (categoryId)
        REFERENCES categories(categoryid) ON DELETE CASCADE
);

-- category_group  (ElementCollection for OnmsCategory.authorizedGroups)
CREATE TABLE IF NOT EXISTS category_group (
    categoryId INTEGER      NOT NULL,
    groupId    VARCHAR(64)  NOT NULL,
    CONSTRAINT fk_cg_category FOREIGN KEY (categoryId)
        REFERENCES categories(categoryid) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- service  (OnmsServiceType)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS service (
    serviceId   INTEGER      NOT NULL DEFAULT nextval('serviceNxtId'),
    serviceName VARCHAR(255) NOT NULL UNIQUE,
    CONSTRAINT pk_service PRIMARY KEY (serviceId)
);

-- -------------------------------------------------------
-- snmpInterface  (OnmsSnmpInterface)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS snmpInterface (
    id                INTEGER NOT NULL DEFAULT nextval('opennmsNxtId'),
    nodeId            INTEGER NOT NULL,
    snmpPhysAddr      VARCHAR(32),
    snmpIfIndex       INTEGER,
    snmpIfDescr       VARCHAR(256),
    snmpIfType        INTEGER,
    snmpIfName        VARCHAR(32),
    snmpIfSpeed       BIGINT,
    snmpIfAdminStatus INTEGER,
    snmpIfOperStatus  INTEGER,
    snmpIfAlias       VARCHAR(256),
    snmpLastCapsdPoll TIMESTAMP,
    snmpCollect       VARCHAR(255),
    snmpPoll          VARCHAR(255),
    snmpLastSnmpPoll  TIMESTAMP,
    last_ingress_flow TIMESTAMP,
    last_egress_flow  TIMESTAMP,
    CONSTRAINT pk_snmpInterface PRIMARY KEY (id),
    CONSTRAINT fk_snmpIf_node FOREIGN KEY (nodeId)
        REFERENCES node(nodeId) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- ipInterface  (OnmsIpInterface)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ipInterface (
    id               INTEGER     NOT NULL DEFAULT nextval('opennmsNxtId'),
    nodeId           INTEGER     NOT NULL,
    ipAddr           VARCHAR(255),
    netmask          VARCHAR(255),
    ipHostName       VARCHAR(256),
    isManaged        VARCHAR(1),
    isSnmpPrimary    VARCHAR(1),
    ipLastCapsdPoll  TIMESTAMP,
    snmpInterfaceId  INTEGER,
    CONSTRAINT pk_ipInterface PRIMARY KEY (id),
    CONSTRAINT fk_ipIf_node FOREIGN KEY (nodeId)
        REFERENCES node(nodeId) ON DELETE CASCADE,
    CONSTRAINT fk_ipIf_snmpIf FOREIGN KEY (snmpInterfaceId)
        REFERENCES snmpInterface(id) ON DELETE SET NULL
);

-- ipInterface_metadata (ElementCollection for OnmsIpInterface)
CREATE TABLE IF NOT EXISTS ipInterface_metadata (
    id      INTEGER      NOT NULL,
    context VARCHAR(256) NOT NULL,
    key     VARCHAR(256) NOT NULL,
    value   TEXT,
    CONSTRAINT fk_ipIf_metadata FOREIGN KEY (id)
        REFERENCES ipInterface(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- ifServices  (OnmsMonitoredService)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS ifServices (
    id              INTEGER NOT NULL DEFAULT nextval('opennmsNxtId'),
    ipInterfaceId   INTEGER NOT NULL,
    serviceId       INTEGER NOT NULL,
    lastGood        TIMESTAMP,
    lastFail        TIMESTAMP,
    qualifier       VARCHAR(16),
    status          VARCHAR(1),
    source          VARCHAR(1),
    notify          VARCHAR(1),
    CONSTRAINT pk_ifServices PRIMARY KEY (id),
    CONSTRAINT fk_ifSvc_ipIf FOREIGN KEY (ipInterfaceId)
        REFERENCES ipInterface(id) ON DELETE CASCADE,
    CONSTRAINT fk_ifSvc_service FOREIGN KEY (serviceId)
        REFERENCES service(serviceId)
);

-- ifServices_metadata (ElementCollection for OnmsMonitoredService)
CREATE TABLE IF NOT EXISTS ifServices_metadata (
    id      INTEGER      NOT NULL,
    context VARCHAR(256) NOT NULL,
    key     VARCHAR(256) NOT NULL,
    value   TEXT,
    CONSTRAINT fk_ifSvc_metadata FOREIGN KEY (id)
        REFERENCES ifServices(id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- memos  (OnmsMemo / OnmsReductionKeyMemo — single-table inheritance)
-- Discriminator column: "type"
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS memos (
    id           INTEGER     NOT NULL DEFAULT nextval('memoNxtId'),
    type         VARCHAR(31) NOT NULL,
    body         TEXT,
    author       VARCHAR(255),
    updated      TIMESTAMP,
    created      TIMESTAMP,
    reductionkey VARCHAR(255),
    CONSTRAINT pk_memos PRIMARY KEY (id)
);

-- -------------------------------------------------------
-- alarms  (OnmsAlarm)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarms (
    alarmId             INTEGER      NOT NULL DEFAULT nextval('alarmsNxtId'),
    eventUEI            VARCHAR(256) NOT NULL,
    systemId            VARCHAR(255) NOT NULL,
    nodeId              INTEGER,
    ipAddr              VARCHAR(255),
    serviceid           INTEGER,
    reductionKey        VARCHAR(255) UNIQUE,
    alarmType           INTEGER,
    ifIndex             INTEGER,
    counter             INTEGER      NOT NULL,
    severity            INTEGER      NOT NULL,
    firstEventTime      TIMESTAMP,
    lastEventTime       TIMESTAMP,
    firstAutomationTime TIMESTAMP,
    lastAutomationTime  TIMESTAMP,
    description         VARCHAR(4000),
    logmsg              VARCHAR(1024),
    operinstruct        TEXT,
    tticketId           VARCHAR(128),
    tticketState        INTEGER,
    mouseOverText       VARCHAR(64),
    suppressedUntil     TIMESTAMP,
    suppressedUser      VARCHAR(256),
    suppressedTime      TIMESTAMP,
    alarmAckUser        VARCHAR(256),
    alarmAckTime        TIMESTAMP,
    clearKey            VARCHAR(255),
    stickymemo          INTEGER,
    managedObjectInstance VARCHAR(512),
    managedObjectType   VARCHAR(512),
    applicationDN       VARCHAR(512),
    ossPrimaryKey       VARCHAR(512),
    x733AlarmType       VARCHAR(31),
    x733ProbableCause   INTEGER      NOT NULL DEFAULT 0,
    qosAlarmState       VARCHAR(31),
    event_tsid          BIGINT,
    event_uei           VARCHAR(256),
    event_source        VARCHAR(256),
    event_severity      INTEGER,
    event_timestamp     TIMESTAMP,
    event_node_id       BIGINT,
    event_log_msg       TEXT,
    last_event_data     TEXT,
    CONSTRAINT pk_alarms PRIMARY KEY (alarmId),
    CONSTRAINT fk_alarm_system FOREIGN KEY (systemId)
        REFERENCES monitoringSystems(id),
    CONSTRAINT fk_alarm_node FOREIGN KEY (nodeId)
        REFERENCES node(nodeId) ON DELETE SET NULL,
    CONSTRAINT fk_alarm_service FOREIGN KEY (serviceid)
        REFERENCES service(serviceId),
    CONSTRAINT fk_alarm_stickymemo FOREIGN KEY (stickymemo)
        REFERENCES memos(id) ON DELETE SET NULL
);

-- alarm_attributes  (ElementCollection for OnmsAlarm.details)
CREATE TABLE IF NOT EXISTS alarm_attributes (
    alarmId        INTEGER      NOT NULL,
    attributename  VARCHAR(255) NOT NULL,
    attributeValue VARCHAR(255) NOT NULL,
    CONSTRAINT fk_alarm_attr FOREIGN KEY (alarmId)
        REFERENCES alarms(alarmId) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- alarm_situations  (AlarmAssociation entity)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarm_situations (
    id               INTEGER NOT NULL DEFAULT nextval('alarmsNxtId'),
    situation_id     INTEGER,
    related_alarm_id INTEGER,
    mapped_time      TIMESTAMP,
    CONSTRAINT pk_alarm_situations PRIMARY KEY (id),
    CONSTRAINT uk_alarm_situations UNIQUE (situation_id, related_alarm_id),
    CONSTRAINT fk_as_situation FOREIGN KEY (situation_id)
        REFERENCES alarms(alarmId) ON DELETE CASCADE,
    CONSTRAINT fk_as_related FOREIGN KEY (related_alarm_id)
        REFERENCES alarms(alarmId) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- accesslocks  (used by AbstractDaoJpa.lock())
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS accesslocks (
    lockname VARCHAR(40) NOT NULL,
    CONSTRAINT pk_accesslocks PRIMARY KEY (lockname)
);

-- -------------------------------------------------------
-- Seed data
-- -------------------------------------------------------

-- Default monitoring location (required by node FK)
INSERT INTO monitoringLocations (id, monitoringArea)
VALUES ('Default', 'Default')
ON CONFLICT (id) DO NOTHING;

-- Local OpenNMS system (required by alarm FK)
INSERT INTO monitoringSystems (id, type, label, location)
VALUES ('00000000-0000-0000-0000-000000000000', 'OpenNMS', 'localhost', 'Default')
ON CONFLICT (id) DO NOTHING;

-- Access lock for alarm DAO
INSERT INTO accesslocks (lockname)
VALUES ('ONMSALARM_ACCESS')
ON CONFLICT (lockname) DO NOTHING;
