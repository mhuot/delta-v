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
  <div class="notification-config-editor">
    <h3>Notification Configuration</h3>

    <div v-if="isLoading" class="loading">Loading...</div>

    <div v-else-if="errorMessage" class="error-message">{{ errorMessage }}</div>

    <div v-else-if="config" class="config-form">
      <div class="feather-row">
        <div class="feather-col-6">
          <FeatherCheckbox
            v-model="config.status"
            label="Notifications Enabled"
          />
        </div>
        <div class="feather-col-6">
          <FeatherCheckbox
            v-model="config.matchAll"
            label="Match All"
          />
        </div>
      </div>

      <FeatherExpansionPanel title="Auto-Acknowledge">
        <div class="auto-ack-section">
          <FeatherCheckbox
            v-model="autoAcknowledge.enabled"
            label="Enabled"
          />
          <FeatherInput
            v-model="autoAcknowledge.prefix"
            label="Prefix"
          />
          <FeatherInput
            v-model="autoAcknowledge.resolutionPrefix"
            label="Resolution Prefix"
          />
        </div>
      </FeatherExpansionPanel>

      <FeatherExpansionPanel
        v-if="queueSettings.length > 0"
        title="Queue Settings"
      >
        <div
          v-for="(queueEntry, queueIndex) in queueSettings"
          :key="'queue-' + queueIndex"
          class="queue-item"
        >
          <FeatherInput
            v-model="queueEntry.name"
            label="Queue Name"
          />
          <FeatherInput
            v-model="queueEntry.interval"
            label="Interval"
          />
        </div>
      </FeatherExpansionPanel>

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
import { FeatherCheckbox } from '@featherds/checkbox'
import { FeatherExpansionPanel } from '@featherds/expansion'
import { getConfig, updateConfig } from '@/services/adminConfigService'

interface NotificationConfiguration {
  status: boolean
  matchAll: boolean
  [key: string]: any
}

interface AutoAcknowledgeSettings {
  enabled: boolean
  prefix: string
  resolutionPrefix: string
}

interface QueueSettingsEntry {
  name: string
  interval: string
}

const configName = 'notifd'
const configId = 'default'

const config = ref<NotificationConfiguration | null>(null)
const originalConfig = ref<Record<string, any> | null>(null)
const autoAcknowledge = ref<AutoAcknowledgeSettings>({
  enabled: false,
  prefix: '',
  resolutionPrefix: ''
})
const queueSettings = ref<QueueSettingsEntry[]>([])
const isLoading = ref(false)
const errorMessage = ref('')

const parseStatusBoolean = (statusValue: any): boolean => {
  if (typeof statusValue === 'boolean') return statusValue
  if (typeof statusValue === 'string') return statusValue.toLowerCase() === 'on' || statusValue.toLowerCase() === 'true'
  return false
}

const loadConfig = async () => {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const responseData = await getConfig(configName, configId)
    if (responseData) {
      config.value = {
        status: parseStatusBoolean(responseData.status),
        matchAll: responseData['match-all'] ?? responseData.matchAll ?? false
      }

      const autoAckData = responseData['auto-acknowledge'] || responseData.autoAcknowledge || {}
      autoAcknowledge.value = {
        enabled: parseStatusBoolean(autoAckData.enabled ?? autoAckData.state ?? false),
        prefix: autoAckData.prefix || '',
        resolutionPrefix: autoAckData['resolution-prefix'] ?? autoAckData.resolutionPrefix ?? ''
      }

      const queuesData = responseData.queues || responseData.queue || []
      const queuesArray = Array.isArray(queuesData) ? queuesData : [queuesData]
      queueSettings.value = queuesArray
        .filter((queueItem: any) => queueItem && queueItem.name)
        .map((queueItem: any) => ({
          name: queueItem.name || '',
          interval: queueItem.interval || ''
        }))

      originalConfig.value = JSON.parse(JSON.stringify({
        config: config.value,
        autoAcknowledge: autoAcknowledge.value,
        queueSettings: queueSettings.value
      }))
    }
  } catch (loadError) {
    errorMessage.value = 'Failed to load notification configuration.'
    console.error('Failed to load notification config', loadError)
  } finally {
    isLoading.value = false
  }
}

const buildPayload = (): Record<string, any> => {
  if (!config.value) return {}

  const payload: Record<string, any> = {
    status: config.value.status ? 'on' : 'off',
    'match-all': config.value.matchAll,
    'auto-acknowledge': {
      state: autoAcknowledge.value.enabled ? 'on' : 'off',
      prefix: autoAcknowledge.value.prefix,
      'resolution-prefix': autoAcknowledge.value.resolutionPrefix
    }
  }

  if (queueSettings.value.length > 0) {
    payload.queue = queueSettings.value
      .filter((queueEntry) => queueEntry.name)
      .map((queueEntry) => ({
        name: queueEntry.name,
        interval: queueEntry.interval
      }))
  }

  return payload
}

const saveConfig = async () => {
  if (!config.value) return

  errorMessage.value = ''

  try {
    const payload = buildPayload()
    await updateConfig(configName, configId, payload)
    originalConfig.value = JSON.parse(JSON.stringify({
      config: config.value,
      autoAcknowledge: autoAcknowledge.value,
      queueSettings: queueSettings.value
    }))
  } catch (saveError) {
    errorMessage.value = 'Failed to save notification configuration.'
    console.error('Failed to save notification config', saveError)
  }
}

const cancelEdit = () => {
  if (originalConfig.value) {
    config.value = JSON.parse(JSON.stringify(originalConfig.value.config))
    autoAcknowledge.value = JSON.parse(JSON.stringify(originalConfig.value.autoAcknowledge))
    queueSettings.value = JSON.parse(JSON.stringify(originalConfig.value.queueSettings))
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

.notification-config-editor {
  h3 {
    @include headline3;
    margin-bottom: 16px;
  }
  h4 {
    @include headline4;
    margin: 16px 0 8px;
  }
  .config-form {
    max-width: 600px;
  }
  .auto-ack-section {
    padding: 8px 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .queue-item {
    display: flex;
    gap: 8px;
    align-items: flex-end;
    margin-bottom: 8px;
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
