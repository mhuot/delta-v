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

test.describe('Admin Config Navigation', () => {
  test('sidebar highlights active domain', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config/provisiond')
    const activeItem = adminPage.locator('.sidebar-item.active')
    await expect(activeItem).toBeVisible()
    await expect(activeItem).toContainText('Provisioning')
  })

  test('sidebar navigation changes domain', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.sidebar')).toBeVisible()

    // Click on a different domain in sidebar
    await adminPage.locator('.sidebar-item:has-text("Event Daemon")').click()
    await expect(adminPage).toHaveURL(/#\/admin-config\/eventd/)
  })

  test('breadcrumbs show correct hierarchy on domain page', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config/provisiond')
    const breadcrumbs = adminPage.locator('.breadcrumbs')
    await expect(breadcrumbs).toContainText('Home')
    await expect(breadcrumbs).toContainText('Configuration')
    await expect(breadcrumbs).toContainText('Provisioning')
  })

  test('breadcrumb Configuration link navigates to dashboard', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config/provisiond')
    await adminPage.locator('.breadcrumbs a:has-text("Configuration")').click()
    await expect(adminPage).toHaveURL(/#\/admin-config$/)
  })

  test('non-admin user cannot access admin-config domain page', async ({ userPage }) => {
    await setupConfigMocks(userPage)
    await userPage.goto('#/admin-config/provisiond')
    await userPage.waitForTimeout(1000)
    await expect(userPage).not.toHaveURL(/#\/admin-config\/provisiond/)
  })

  test('card click navigates to correct domain', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    // Find and click the Provisioning card
    await adminPage.locator('.config-card:has-text("Provisioning")').click()
    await expect(adminPage).toHaveURL(/#\/admin-config\/provisiond/)
  })
})
