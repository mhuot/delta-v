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

test.describe('Config CRUD Operations', () => {
  test.beforeEach(async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
  })

  test('loads provisiond config and displays form', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
  })

  test('sends PUT request when saving config changes', async ({ adminPage }) => {
    let putRequestBody: string | null = null

    await adminPage.route(/\/rest\/cm\/provisiond\/default$/, (route) => {
      const method = route.request().method()
      if (method === 'PUT') {
        putRequestBody = route.request().postData()
        return route.fulfill({ status: 200 })
      }
      if (method === 'GET') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            importThreads: 8, scanThreads: 10, rescanThreads: 10,
            writeThreads: 8, importSchedule: '0 0 0 * * ? *', enableDiscovery: true
          })
        })
      }
      return route.continue()
    })

    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()

    // Edit a field to enable save
    const firstInput = adminPage.locator('input').first()
    await firstInput.fill('16')

    await adminPage.getByRole('button', { name: 'Save' }).click()
    expect(putRequestBody).toBeTruthy()
  })

  test('cancel navigates back to dashboard', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    await adminPage.getByRole('button', { name: 'Cancel' }).click()
    await expect(adminPage).toHaveURL(/#\/admin-config$/)
  })

  test('reset reloads original config data', async ({ adminPage }) => {
    let fetchCount = 0

    await adminPage.route(/\/rest\/cm\/provisiond\/default$/, (route) => {
      if (route.request().method() === 'GET') {
        fetchCount++
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            importThreads: 8, scanThreads: 10, rescanThreads: 10,
            writeThreads: 8, importSchedule: '0 0 0 * * ? *', enableDiscovery: true
          })
        })
      }
      return route.continue()
    })

    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    const initialFetchCount = fetchCount

    await adminPage.getByRole('button', { name: 'Reset' }).click()
    // Should re-fetch the config
    expect(fetchCount).toBeGreaterThan(initialFetchCount)
  })
})
