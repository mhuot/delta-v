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

import type { Page } from '@playwright/test'
import { mockProvisiondSchema, mockCollectdSchema, mockEventdSchema } from './mock-schemas'

export const mockConfigNames = [
  'provisiond', 'discovery', 'collectd', 'pollerd',
  'notifd', 'eventd', 'syslogd', 'trapd',
  'ticketer', 'snmp-config'
]

export const mockProvisiondConfig = {
  importThreads: 8,
  scanThreads: 10,
  rescanThreads: 10,
  writeThreads: 8,
  importSchedule: '0 0 0 * * ? *',
  enableDiscovery: true
}

export const mockEventdConfig = {
  tcpAddress: '127.0.0.1',
  tcpPort: 5817,
  udpAddress: '127.0.0.1',
  udpPort: 5817,
  receivers: 5,
  logEventSummaries: true
}

export const mockSnmpConfig = {
  version: 'v2c',
  community: 'public',
  port: 161,
  timeout: 3000,
  retries: 1
}

const schemaMap: Record<string, object> = {
  provisiond: mockProvisiondSchema,
  collectd: mockCollectdSchema,
  eventd: mockEventdSchema
}

const configMap: Record<string, object> = {
  provisiond: mockProvisiondConfig,
  eventd: mockEventdConfig
}

export async function setupConfigMocks(page: Page) {
  await page.route('**/rest/cm/', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockConfigNames)
      })
    }
    return route.continue()
  })

  await page.route('**/rest/cm/schema/*', (route) => {
    const url = route.request().url()
    const configName = url.split('/rest/cm/schema/')[1]?.split('?')[0]
    const schema = schemaMap[configName]

    if (schema) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(schema)
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ type: 'object', properties: {} })
    })
  })

  await page.route(/\/rest\/cm\/[^/]+$/, (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['default'])
      })
    }
    return route.continue()
  })

  await page.route(/\/rest\/cm\/[^/]+\/[^/]+$/, (route) => {
    const url = route.request().url()
    const method = route.request().method()
    const segments = url.split('/rest/cm/')[1]?.split('/')
    const configName = segments?.[0]

    if (method === 'GET') {
      const configData = configMap[configName ?? '']
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(configData ?? {})
      })
    }

    if (method === 'PUT') {
      return route.fulfill({ status: 200 })
    }

    if (method === 'DELETE') {
      return route.fulfill({ status: 204 })
    }

    return route.continue()
  })

  await page.route('**/rest/snmpConfig/*', (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSnmpConfig)
      })
    }
    if (method === 'PUT') {
      return route.fulfill({ status: 200 })
    }
    return route.continue()
  })
}
