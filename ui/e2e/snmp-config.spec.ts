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

import { expect } from '@playwright/test'
import { test } from './fixtures/auth'
import { setupConfigMocks } from './fixtures/mock-configs'

test.describe('SNMP Config Editor', () => {
  test.beforeEach(async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
  })

  test('displays SNMP configuration editor for snmp-config domain', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/snmp-config')
    await expect(adminPage.locator('.snmp-config-editor')).toBeVisible()
    await expect(adminPage.getByText('SNMP Configuration')).toBeVisible()
  })

  test('has IP address lookup input and button', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/snmp-config')
    await expect(adminPage.locator('.ip-input')).toBeVisible()
    await expect(adminPage.getByRole('button', { name: 'Lookup' })).toBeVisible()
  })

  test('lookup button is disabled without IP address', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/snmp-config')
    await expect(adminPage.getByRole('button', { name: 'Lookup' })).toBeDisabled()
  })

  test('loads SNMP config on IP lookup', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/snmp-config')
    const ipInput = adminPage.locator('.ip-input input')
    await ipInput.fill('192.168.1.1')
    await adminPage.getByRole('button', { name: 'Lookup' }).click()
    // Should display config form with values
    await expect(adminPage.locator('.config-form')).toBeVisible()
  })

  test('shows v2c-specific fields by default', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/snmp-config')
    const ipInput = adminPage.locator('.ip-input input')
    await ipInput.fill('192.168.1.1')
    await adminPage.getByRole('button', { name: 'Lookup' }).click()
    await expect(adminPage.locator('.config-form')).toBeVisible()
    // Community string should be visible for v2c
    await expect(adminPage.getByText('Community String')).toBeVisible()
    // v3 fields should not be visible
    await expect(adminPage.getByText('Security Name')).not.toBeVisible()
  })

  test('sends PUT request on save', async ({ adminPage }) => {
    let saveRequestSent = false
    await adminPage.route('**/rest/snmpConfig/*', (route) => {
      if (route.request().method() === 'PUT') {
        saveRequestSent = true
        return route.fulfill({ status: 200 })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ version: 'v2c', community: 'public', port: 161, timeout: 3000, retries: 1 })
      })
    })

    await adminPage.goto('#/admin-config/snmp-config')
    const ipInput = adminPage.locator('.ip-input input')
    await ipInput.fill('192.168.1.1')
    await adminPage.getByRole('button', { name: 'Lookup' }).click()
    await expect(adminPage.locator('.config-form')).toBeVisible()
    await adminPage.getByRole('button', { name: 'Save' }).click()
    expect(saveRequestSent).toBe(true)
  })
})
