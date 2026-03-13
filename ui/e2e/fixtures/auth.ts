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

import { test as base, type Page } from '@playwright/test'

const mockWhoAmIAdmin = {
  fullName: 'Admin User',
  id: 'admin',
  internal: true,
  roles: ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_REST']
}

const mockWhoAmIUser = {
  fullName: 'Regular User',
  id: 'user',
  internal: true,
  roles: ['ROLE_USER']
}

const mockMainMenu = {
  baseHref: '/opennms/',
  baseNodeUrl: '/opennms/element/node.jsp?node=',
  homeUrl: '/opennms/index.jsp',
  formattedTime: '2026-03-12T10:00:00-05:00',
  noticeStatus: 'Unknown',
  username: 'admin',
  zenithConnectEnabled: false,
  menus: []
}

const mockAppInfo = {
  datetimeformatConfig: { zoneId: 'America/New_York', datetimeformat: 'yyyy-MM-dd\'T\'HH:mm:ssxxx' },
  displayVersion: '36.0.0-SNAPSHOT',
  packageDescription: 'OpenNMS',
  packageName: 'opennms',
  services: {},
  ticketerConfig: { plugin: null, enabled: false },
  version: '36.0.0'
}

async function setupCommonMocks(page: Page) {
  await page.route('**/rest/menu/main', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockMainMenu) })
  )
  await page.route('**/rest/info', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockAppInfo) })
  )
  await page.route('**/api/v2/notifications/summary', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ totalCount: 0, user: 'admin' }) })
  )
}

export const test = base.extend<{ adminPage: Page; userPage: Page }>({
  adminPage: async ({ page }, use) => {
    await setupCommonMocks(page)
    await page.route('**/rest/whoami', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockWhoAmIAdmin) })
    )
    await use(page)
  },
  userPage: async ({ page }, use) => {
    await setupCommonMocks(page)
    await page.route('**/rest/whoami', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockWhoAmIUser) })
    )
    await use(page)
  }
})

export { mockWhoAmIAdmin, mockWhoAmIUser, mockMainMenu }
