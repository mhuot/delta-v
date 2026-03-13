///
/// Licensed to The OpenNMS Group, Inc (TOG) under one or more
/// contributor license agreements.  See the LICENSE.md file
/// distributed with this work for additional information
/// regarding copyright ownership.
///
/// TOG licenses this file to You under the GNU Affero General
/// Public License Version 3 (the "License") or (at your option)
/// any later version.  You may not use this file except in
/// compliance with the License.  You may obtain a copy of the
/// License at:
///
///      https://www.gnu.org/licenses/agpl-3.0.txt
///
/// Unless required by applicable law or agreed to in writing,
/// software distributed under the License is distributed on an
/// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
/// either express or implied.  See the License for the specific
/// language governing permissions and limitations under the
/// License.
///

import type { ConfigDomainDefinition } from '@/types/adminConfig'

const configDomains: ConfigDomainDefinition[] = [
  {
    configName: 'provisiond',
    displayName: 'Provisioning',
    description: 'Configure provisioning settings and behavior',
    icon: 'settings',
    category: 'provisioning',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'discovery',
    displayName: 'Discovery',
    description: 'Configure network discovery parameters',
    icon: 'search',
    category: 'provisioning',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'collectd',
    displayName: 'Data Collection',
    description: 'Configure data collection services and packages',
    icon: 'database',
    category: 'collection',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'pollerd',
    displayName: 'Polling',
    description: 'Configure polling services and packages',
    icon: 'activity',
    category: 'collection',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'notifd',
    displayName: 'Notifications',
    description: 'Configure notification routing and destinations',
    icon: 'bell',
    category: 'notifications',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'eventd',
    displayName: 'Event Daemon',
    description: 'Configure event processing and handling',
    icon: 'zap',
    category: 'core',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'syslogd',
    displayName: 'Syslog',
    description: 'Configure syslog message reception and parsing',
    icon: 'file-text',
    category: 'core',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'trapd',
    displayName: 'SNMP Traps',
    description: 'Configure SNMP trap reception and processing',
    icon: 'inbox',
    category: 'core',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'ticketer',
    displayName: 'Ticketing',
    description: 'Configure trouble ticketing integration',
    icon: 'tag',
    category: 'system',
    defaultConfigId: 'default',
    apiType: 'cm'
  },
  {
    configName: 'snmp-config',
    displayName: 'SNMP',
    description: 'Configure SNMP community strings and profiles',
    icon: 'cpu',
    category: 'system',
    customComponent: 'SnmpConfigEditor',
    defaultConfigId: 'default',
    apiType: 'cm'
  }
]

const getConfigDomain = (configName: string): ConfigDomainDefinition | undefined => {
  return configDomains.find((domain) => domain.configName === configName)
}

const getConfigDomainsByCategory = (): Map<string, ConfigDomainDefinition[]> => {
  const categoryMap = new Map<string, ConfigDomainDefinition[]>()

  for (const domain of configDomains) {
    const existingDomains = categoryMap.get(domain.category)
    if (existingDomains) {
      existingDomains.push(domain)
    } else {
      categoryMap.set(domain.category, [domain])
    }
  }

  return categoryMap
}

export { configDomains, getConfigDomain, getConfigDomainsByCategory }
