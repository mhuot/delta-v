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
  <div class="snmp-config-editor">
    <h3>SNMP Configuration</h3>
    <div class="lookup-section">
      <FeatherInput
        v-model="ipAddress"
        label="IP Address"
        class="ip-input"
      />
      <FeatherButton
        primary
        :disabled="!ipAddress"
        @click="lookupConfig"
      >
        Lookup
      </FeatherButton>
    </div>

    <div v-if="isLoading" class="loading">Loading...</div>

    <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>

    <div v-if="snmpConfig" class="config-form">
      <FeatherSelect
        v-model="snmpConfig.version"
        label="SNMP Version"
        :options="versionOptions"
        text-prop="option"
      />

      <FeatherInput
        v-if="isV1OrV2c"
        v-model="snmpConfig.community"
        label="Community String"
      />

      <div class="feather-row">
        <div class="feather-col-4">
          <FeatherInput
            v-model="snmpConfig.port"
            label="Port"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="snmpConfig.timeout"
            label="Timeout (ms)"
            type="number"
          />
        </div>
        <div class="feather-col-4">
          <FeatherInput
            v-model="snmpConfig.retries"
            label="Retries"
            type="number"
          />
        </div>
      </div>

      <template v-if="isV3">
        <h4>SNMPv3 Settings</h4>
        <FeatherInput
          v-model="snmpConfig.securityName"
          label="Security Name"
        />

        <FeatherSelect
          v-model="snmpConfig.securityLevel"
          label="Security Level"
          :options="securityLevelOptions"
          text-prop="option"
        />

        <FeatherSelect
          v-model="snmpConfig.authProtocol"
          label="Auth Protocol"
          :options="authProtocolOptions"
          text-prop="option"
        />

        <FeatherInput
          v-model="snmpConfig.authPassphrase"
          label="Auth Passphrase"
          type="password"
        />

        <FeatherSelect
          v-model="snmpConfig.privProtocol"
          label="Privacy Protocol"
          :options="privProtocolOptions"
          text-prop="option"
        />

        <FeatherInput
          v-model="snmpConfig.privPassphrase"
          label="Privacy Passphrase"
          type="password"
        />
      </template>

      <div class="button-bar">
        <FeatherButton primary @click="saveConfig">Save</FeatherButton>
        <FeatherButton @click="resetConfig">Reset</FeatherButton>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FeatherInput } from '@featherds/input'
import { FeatherButton } from '@featherds/button'
import { FeatherSelect } from '@featherds/select'
import { rest } from '@/services/axiosInstances'

interface SnmpSelectOption {
  [key: string]: string
  id: string
  option: string
}

interface SnmpConfiguration {
  version: SnmpSelectOption
  community: string
  port: number
  timeout: number
  retries: number
  securityName: string
  securityLevel: SnmpSelectOption
  authProtocol: SnmpSelectOption
  authPassphrase: string
  privProtocol: SnmpSelectOption
  privPassphrase: string
}

const ipAddress = ref('')
const snmpConfig = ref<SnmpConfiguration | null>(null)
const originalConfig = ref<SnmpConfiguration | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')

const versionOptions: SnmpSelectOption[] = [
  { id: 'v1', option: 'v1' },
  { id: 'v2c', option: 'v2c' },
  { id: 'v3', option: 'v3' }
]

const securityLevelOptions: SnmpSelectOption[] = [
  { id: 'noAuthNoPriv', option: 'noAuthNoPriv' },
  { id: 'authNoPriv', option: 'authNoPriv' },
  { id: 'authPriv', option: 'authPriv' }
]

const authProtocolOptions: SnmpSelectOption[] = [
  { id: 'MD5', option: 'MD5' },
  { id: 'SHA', option: 'SHA' },
  { id: 'SHA-224', option: 'SHA-224' },
  { id: 'SHA-256', option: 'SHA-256' },
  { id: 'SHA-384', option: 'SHA-384' },
  { id: 'SHA-512', option: 'SHA-512' }
]

const privProtocolOptions: SnmpSelectOption[] = [
  { id: 'DES', option: 'DES' },
  { id: 'AES', option: 'AES' },
  { id: 'AES192', option: 'AES192' },
  { id: 'AES256', option: 'AES256' }
]

const isV3 = computed(() => snmpConfig.value?.version?.id === 'v3')
const isV1OrV2c = computed(() => {
  const versionId = snmpConfig.value?.version?.id
  return versionId === 'v1' || versionId === 'v2c'
})

const findOption = (options: SnmpSelectOption[], identifier: string): SnmpSelectOption => {
  return options.find((optionItem) => optionItem.id === identifier) || options[0]
}

const mapResponseToConfig = (responseData: Record<string, any>): SnmpConfiguration => {
  return {
    version: findOption(versionOptions, responseData.version || 'v2c'),
    community: responseData.community || 'public',
    port: responseData.port || 161,
    timeout: responseData.timeout || 3000,
    retries: responseData.retries || 1,
    securityName: responseData.securityName || '',
    securityLevel: findOption(securityLevelOptions, responseData.securityLevel || 'noAuthNoPriv'),
    authProtocol: findOption(authProtocolOptions, responseData.authProtocol || 'MD5'),
    authPassphrase: responseData.authPassphrase || '',
    privProtocol: findOption(privProtocolOptions, responseData.privProtocol || 'DES'),
    privPassphrase: responseData.privPassphrase || ''
  }
}

const mapConfigToPayload = (configData: SnmpConfiguration): Record<string, any> => {
  const payload: Record<string, any> = {
    version: configData.version.id,
    port: configData.port,
    timeout: configData.timeout,
    retries: configData.retries
  }

  if (configData.version.id === 'v1' || configData.version.id === 'v2c') {
    payload.community = configData.community
  } else if (configData.version.id === 'v3') {
    payload.securityName = configData.securityName
    payload.securityLevel = configData.securityLevel.id
    payload.authProtocol = configData.authProtocol.id
    payload.authPassphrase = configData.authPassphrase
    payload.privProtocol = configData.privProtocol.id
    payload.privPassphrase = configData.privPassphrase
  }

  return payload
}

const lookupConfig = async () => {
  if (!ipAddress.value) return

  isLoading.value = true
  errorMessage.value = ''
  snmpConfig.value = null

  try {
    const response = await rest.get(`/snmpConfig/${encodeURIComponent(ipAddress.value)}`)
    if (response.status === 200) {
      const mappedConfig = mapResponseToConfig(response.data)
      snmpConfig.value = mappedConfig
      originalConfig.value = JSON.parse(JSON.stringify(mappedConfig))
    }
  } catch (fetchError: any) {
    errorMessage.value = fetchError?.response?.status === 404
      ? `No SNMP configuration found for ${ipAddress.value}`
      : 'Failed to load SNMP configuration. Please check the IP address and try again.'
    console.error('Failed to lookup SNMP config', fetchError)
  } finally {
    isLoading.value = false
  }
}

const saveConfig = async () => {
  if (!snmpConfig.value || !ipAddress.value) return

  errorMessage.value = ''

  try {
    const payload = mapConfigToPayload(snmpConfig.value)
    await rest.put(`/snmpConfig/${encodeURIComponent(ipAddress.value)}`, payload)
    originalConfig.value = JSON.parse(JSON.stringify(snmpConfig.value))
  } catch (saveError) {
    errorMessage.value = 'Failed to save SNMP configuration.'
    console.error('Failed to save SNMP config', saveError)
  }
}

const resetConfig = () => {
  if (originalConfig.value) {
    snmpConfig.value = JSON.parse(JSON.stringify(originalConfig.value))
  }
  errorMessage.value = ''
}
</script>

<style lang="scss" scoped>
@import "@featherds/styles/themes/variables";
@import "@featherds/styles/mixins/typography";

.snmp-config-editor {
  h3 {
    @include headline3;
    margin-bottom: 16px;
  }
  h4 {
    @include headline4;
    margin: 16px 0 8px;
  }
  .lookup-section {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    margin-bottom: 16px;
    .ip-input {
      flex: 1;
      max-width: 400px;
    }
  }
  .config-form {
    max-width: 600px;
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
