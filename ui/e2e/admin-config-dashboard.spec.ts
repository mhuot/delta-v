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

test.describe('Admin Config Dashboard', () => {
  test('loads dashboard and displays config domain cards', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await expect(adminPage.locator('.admin-config-dashboard')).toBeVisible()
    await expect(adminPage.locator('.config-card')).toHaveCount(10)
  })

  test('displays category sections', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await expect(adminPage.locator('.category-header')).toHaveCount(5)
  })

  test('filters domains by search term', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await adminPage.locator('.search-container input').fill('provision')
    await expect(adminPage.locator('.config-card')).toHaveCount(1)
    await expect(adminPage.locator('.card-title')).toContainText('Provisioning')
  })

  test('shows empty state when no results match search', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await adminPage.locator('.search-container input').fill('nonexistent')
    await expect(adminPage.locator('.empty-state')).toBeVisible()
    await expect(adminPage.locator('.config-card')).toHaveCount(0)
  })

  test('navigates to domain page on card click', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await adminPage.locator('.config-card').first().click()
    await expect(adminPage).toHaveURL(/#\/admin-config\//)
  })

  test('displays sidebar navigation', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    await expect(adminPage.locator('.sidebar')).toBeVisible()
    await expect(adminPage.locator('.sidebar-item')).toHaveCount(10)
  })

  test('renders breadcrumbs with Home and Configuration', async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
    await adminPage.goto('#/admin-config')
    const breadcrumbs = adminPage.locator('.breadcrumbs')
    await expect(breadcrumbs).toBeVisible()
    await expect(breadcrumbs).toContainText('Home')
    await expect(breadcrumbs).toContainText('Configuration')
  })

  test('redirects non-admin users away from admin-config', async ({ userPage }) => {
    await setupConfigMocks(userPage)
    await userPage.goto('#/admin-config')
    // Should not see the dashboard since user lacks ROLE_ADMIN
    await userPage.waitForTimeout(1000)
    await expect(userPage).not.toHaveURL(/#\/admin-config$/)
  })
})
