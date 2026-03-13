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

test.describe('Schema Form Renderer', () => {
  test.beforeEach(async ({ adminPage }) => {
    await setupConfigMocks(adminPage)
  })

  test('renders form fields from provisiond schema', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    // Should have fields for importThreads, scanThreads, etc.
    await expect(adminPage.locator('.schema-form-field')).toHaveCount(6)
  })

  test('displays field labels from property names', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    // camelCase should be converted to Title Case
    await expect(adminPage.getByText('Import Threads')).toBeVisible()
    await expect(adminPage.getByText('Scan Threads')).toBeVisible()
  })

  test('shows current config values in form fields', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    // importThreads should show 8
    const importThreadsInput = adminPage.locator('input').first()
    await expect(importThreadsInput).toHaveValue('8')
  })

  test('marks required fields with asterisk', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    // importThreads and scanThreads are required
    await expect(adminPage.locator('.required-indicator')).toHaveCount(2)
  })

  test('save button is disabled when form is clean', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    const saveButton = adminPage.locator('button:has-text("Save")')
    await expect(saveButton).toBeDisabled()
  })

  test('save button is enabled after editing a field', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    const firstInput = adminPage.locator('input').first()
    await firstInput.fill('16')
    const saveButton = adminPage.locator('button:has-text("Save")')
    await expect(saveButton).toBeEnabled()
  })

  test('renders boolean fields as checkboxes', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/provisiond')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    // enableDiscovery is a boolean field
    await expect(adminPage.locator('input[type="checkbox"]')).toBeVisible()
  })

  test('renders eventd form with all field types', async ({ adminPage }) => {
    await adminPage.goto('#/admin-config/eventd')
    await expect(adminPage.locator('.schema-form-renderer')).toBeVisible()
    await expect(adminPage.locator('.schema-form-field')).toHaveCount(6)
  })
})
