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

<template>
  <div class="discovery-config-editor">
    <h3>Discovery Configuration</h3>

    <div v-if="isLoading" class="loading">Loading...</div>

    <div v-else-if="errorMessage" class="error-message">{{ errorMessage }}</div>

    <div v-else-if="config" class="config-form">
      <h4>General Settings</h4>
      <div class="feather-row">
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.threads"
            label="Threads"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.retries"
            label="Retries"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.timeout"
            label="Timeout (ms)"
            type="number"
          />
        </div>
      </div>
      <div class="feather-row">
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.initialSleepTime"
            label="Initial Sleep Time (ms)"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.restartSleepTime"
            label="Restart Sleep Time (ms)"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="config.chunkSize"
            label="Chunk Size"
            type="number"
          />
        </div>
      </div>

      <!-- Include Ranges -->
      <h4>Include Ranges</h4>
      <div
        v-for="(includeRange, rangeIndex) in includeRanges"
        :key="'include-' + rangeIndex"
        class="range-item"
      >
        <FeatherInput
          v-model="includeRange.begin"
          label="Begin IP"
          class="range-input"
        />
        <FeatherInput
          v-model="includeRange.end"
          label="End IP"
          class="range-input"
        />
        <FeatherInput
          v-model="includeRange.retries"
          label="Retries"
          type="number"
          class="range-number-input"
        />
        <FeatherInput
          v-model="includeRange.timeout"
          label="Timeout"
          type="number"
          class="range-number-input"
        />
        <FeatherButton text @click="removeIncludeRange(rangeIndex)">Remove</FeatherButton>
      </div>
      <FeatherButton @click="addIncludeRange">Add Include Range</FeatherButton>

      <!-- Exclude Ranges -->
      <h4>Exclude Ranges</h4>
      <div
        v-for="(excludeRange, excludeIndex) in excludeRanges"
        :key="'exclude-' + excludeIndex"
        class="range-item"
      >
        <FeatherInput
          v-model="excludeRange.begin"
          label="Begin IP"
          class="range-input"
        />
        <FeatherInput
          v-model="excludeRange.end"
          label="End IP"
          class="range-input"
        />
        <FeatherButton text @click="removeExcludeRange(excludeIndex)">Remove</FeatherButton>
      </div>
      <FeatherButton @click="addExcludeRange">Add Exclude Range</FeatherButton>

      <!-- Specifics -->
      <h4>Specifics</h4>
      <div
        v-for="(specificEntry, specificIndex) in specifics"
        :key="'specific-' + specificIndex"
        class="range-item"
      >
        <FeatherInput
          v-model="specificEntry.address"
          label="IP Address"
          class="range-input"
        />
        <FeatherInput
          v-model="specificEntry.retries"
          label="Retries"
          type="number"
          class="range-number-input"
        />
        <FeatherInput
          v-model="specificEntry.timeout"
          label="Timeout"
          type="number"
          class="range-number-input"
        />
        <FeatherButton text @click="removeSpecific(specificIndex)">Remove</FeatherButton>
      </div>
      <FeatherButton @click="addSpecific">Add Specific</FeatherButton>

      <div class="button-bar">
        <FeatherButton primary @click="saveConfig">Save</FeatherButton>
        <FeatherButton @click="cancelEdit">Cancel</FeatherButton>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FeatherInput } from '@featherds/input'
import { FeatherButton } from '@featherds/button'
import { getConfig, updateConfig } from '@/services/adminConfigService'

interface IncludeRange {
  begin: string
  end: string
  retries: number
  timeout: number
}

interface ExcludeRange {
  begin: string
  end: string
}

interface SpecificEntry {
  address: string
  retries: number
  timeout: number
}

interface DiscoveryConfiguration {
  threads: number
  retries: number
  timeout: number
  initialSleepTime: number
  restartSleepTime: number
  chunkSize: number
  [key: string]: any
}

const configName = 'discovery'
const configId = 'default'

const config = ref<DiscoveryConfiguration | null>(null)
const originalConfig = ref<DiscoveryConfiguration | null>(null)
const includeRanges = ref<IncludeRange[]>([])
const excludeRanges = ref<ExcludeRange[]>([])
const specifics = ref<SpecificEntry[]>([])
const isLoading = ref(false)
const errorMessage = ref('')

const loadConfig = async () => {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const responseData = await getConfig(configName, configId)
    if (responseData) {
      config.value = {
        threads: responseData.threads || 1,
        retries: responseData.retries || 1,
        timeout: responseData.timeout || 2000,
        initialSleepTime: responseData['initial-sleep-time'] ?? responseData.initialSleepTime ?? 30000,
        restartSleepTime: responseData['restart-sleep-time'] ?? responseData.restartSleepTime ?? 86400000,
        chunkSize: responseData['chunk-size'] ?? responseData.chunkSize ?? 100
      }

      includeRanges.value = (responseData['include-range'] || responseData.includeRanges || []).map(
        (rangeEntry: any) => ({
          begin: rangeEntry.begin || '',
          end: rangeEntry.end || '',
          retries: rangeEntry.retries || 1,
          timeout: rangeEntry.timeout || 2000
        })
      )

      excludeRanges.value = (responseData['exclude-range'] || responseData.excludeRanges || []).map(
        (rangeEntry: any) => ({
          begin: rangeEntry.begin || '',
          end: rangeEntry.end || ''
        })
      )

      specifics.value = (responseData.specific || responseData.specifics || []).map(
        (specificItem: any) => {
          if (typeof specificItem === 'string') {
            return { address: specificItem, retries: 1, timeout: 2000 }
          }
          return {
            address: specificItem.address || specificItem.content || '',
            retries: specificItem.retries || 1,
            timeout: specificItem.timeout || 2000
          }
        }
      )

      originalConfig.value = JSON.parse(JSON.stringify({
        config: config.value,
        includeRanges: includeRanges.value,
        excludeRanges: excludeRanges.value,
        specifics: specifics.value
      }))
    }
  } catch (loadError) {
    errorMessage.value = 'Failed to load discovery configuration.'
    console.error('Failed to load discovery config', loadError)
  } finally {
    isLoading.value = false
  }
}

const addIncludeRange = () => {
  includeRanges.value.push({ begin: '', end: '', retries: 1, timeout: 2000 })
}

const removeIncludeRange = (rangeIndex: number) => {
  includeRanges.value.splice(rangeIndex, 1)
}

const addExcludeRange = () => {
  excludeRanges.value.push({ begin: '', end: '' })
}

const removeExcludeRange = (rangeIndex: number) => {
  excludeRanges.value.splice(rangeIndex, 1)
}

const addSpecific = () => {
  specifics.value.push({ address: '', retries: 1, timeout: 2000 })
}

const removeSpecific = (specificIndex: number) => {
  specifics.value.splice(specificIndex, 1)
}

const buildPayload = (): Record<string, any> => {
  if (!config.value) return {}

  return {
    threads: config.value.threads,
    retries: config.value.retries,
    timeout: config.value.timeout,
    'initial-sleep-time': config.value.initialSleepTime,
    'restart-sleep-time': config.value.restartSleepTime,
    'chunk-size': config.value.chunkSize,
    'include-range': includeRanges.value.filter(
      (rangeEntry) => rangeEntry.begin && rangeEntry.end
    ),
    'exclude-range': excludeRanges.value.filter(
      (rangeEntry) => rangeEntry.begin && rangeEntry.end
    ),
    specific: specifics.value
      .filter((specificEntry) => specificEntry.address)
      .map((specificEntry) => ({
        address: specificEntry.address,
        retries: specificEntry.retries,
        timeout: specificEntry.timeout
      }))
  }
}

const saveConfig = async () => {
  if (!config.value) return

  errorMessage.value = ''

  try {
    const payload = buildPayload()
    await updateConfig(configName, configId, payload)
    originalConfig.value = JSON.parse(JSON.stringify({
      config: config.value,
      includeRanges: includeRanges.value,
      excludeRanges: excludeRanges.value,
      specifics: specifics.value
    }))
  } catch (saveError) {
    errorMessage.value = 'Failed to save discovery configuration.'
    console.error('Failed to save discovery config', saveError)
  }
}

const cancelEdit = () => {
  if (originalConfig.value) {
    config.value = JSON.parse(JSON.stringify(originalConfig.value.config))
    includeRanges.value = JSON.parse(JSON.stringify(originalConfig.value.includeRanges))
    excludeRanges.value = JSON.parse(JSON.stringify(originalConfig.value.excludeRanges))
    specifics.value = JSON.parse(JSON.stringify(originalConfig.value.specifics))
  }
  errorMessage.value = ''
}

onMounted(() => {
  loadConfig()
})
</script>

<style lang="scss" scoped>
@import "@featherds/styles/themes/variables";
@import "@featherds/styles/mixins/typography";

.discovery-config-editor {
  h3 {
    @include headline3;
    margin-bottom: 16px;
  }
  h4 {
    @include headline4;
    margin: 16px 0 8px;
  }
  .config-form {
    max-width: 800px;
  }
  .range-item {
    display: flex;
    gap: 8px;
    align-items: flex-end;
    margin-bottom: 8px;
  }
  .range-input {
    flex: 1;
  }
  .range-number-input {
    width: 120px;
    flex-shrink: 0;
  }
  .button-bar {
    display: flex;
    gap: 8px;
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid var($border-on-surface);
  }
  .loading {
    padding: 24px;
    text-align: center;
  }
  .error-message {
    color: var($error);
    padding: 8px 0;
  }
}
</style>
