<!--
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
-->

<template>
  <div class="feather-row">
    <div class="feather-col-12">
      <BreadCrumbs :items="breadcrumbs" />
    </div>
  </div>
  <div class="feather-row">
    <div class="feather-col-12">
      <div class="wrapper feather-container center">
        <div class="feather-row">
          <div class="feather-col-3">
            <AdminConfigSidebar />
          </div>
          <div class="feather-col-9">
            <div v-if="adminConfigStore.isLoading" class="loading-indicator">
              Loading configuration...
            </div>
            <div v-else-if="adminConfigStore.error" class="error-message">
              {{ adminConfigStore.error }}
            </div>
            <div v-else-if="domainDefinition?.customComponent" class="custom-editor-placeholder">
              <h2>{{ domainDefinition.displayName }}</h2>
              <p>Custom editor for {{ domainDefinition.displayName }}</p>
            </div>
            <div v-else-if="adminConfigStore.currentSchema && adminConfigStore.currentConfigData" class="schema-form-container">
              <SchemaFormRenderer
                v-if="adminConfigStore.currentSchema.properties"
                :schema="adminConfigStore.currentSchema"
                :modelValue="adminConfigStore.currentConfigData"
                :configName="domainDisplayName"
                @update:modelValue="handleFormUpdate"
                @save="handleSave"
                @cancel="handleCancel"
                @reset="handleReset"
              />
            </div>
            <div v-else class="empty-state">
              <p>Select a configuration ID or no schema available for this domain.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import BreadCrumbs from '@/components/Layout/BreadCrumbs.vue'
import AdminConfigSidebar from '@/components/AdminConfig/AdminConfigSidebar.vue'
import SchemaFormRenderer from '@/components/AdminConfig/SchemaForm/SchemaFormRenderer.vue'
import { FeatherButton } from '@featherds/button'
import { useAdminConfigStore } from '@/stores/adminConfigStore'
import { useMenuStore } from '@/stores/menuStore'
import { getConfigDomain } from '@/components/AdminConfig/configDomainRegistry'
import { BreadCrumb } from '@/types'
import type { ConfigDomainDefinition } from '@/types/adminConfig'

const props = defineProps<{
  configDomain: string
}>()

const router = useRouter()
const adminConfigStore = useAdminConfigStore()
const menuStore = useMenuStore()

const homeUrl = computed<string>(() => menuStore.mainMenu.homeUrl)

const domainDefinition = computed<ConfigDomainDefinition | undefined>(() => {
  return getConfigDomain(props.configDomain)
})

const domainDisplayName = computed<string>(() => {
  return domainDefinition.value?.displayName ?? props.configDomain
})

const breadcrumbs = computed<BreadCrumb[]>(() => {
  return [
    { label: 'Home', to: homeUrl.value, isAbsoluteLink: true },
    { label: 'Configuration', to: '/admin-config' },
    { label: domainDisplayName.value, to: '#', position: 'last' }
  ]
})

const loadDomainConfig = async (configDomainName: string) => {
  const domainDef = getConfigDomain(configDomainName)
  await adminConfigStore.setCurrentDomain(configDomainName)

  if (domainDef && !domainDef.customComponent) {
    await adminConfigStore.fetchConfig(configDomainName, domainDef.defaultConfigId)
  }
}

const handleFormUpdate = (updatedData: Record<string, any>) => {
  adminConfigStore.currentConfigData = updatedData
  adminConfigStore.setDirty(true)
}

const handleSave = async () => {
  await adminConfigStore.saveConfig()
}

const handleCancel = () => {
  router.push('/admin-config')
}

const handleReset = async () => {
  const domainDef = getConfigDomain(props.configDomain)
  if (domainDef) {
    await adminConfigStore.fetchConfig(props.configDomain, domainDef.defaultConfigId)
  }
}

onMounted(() => {
  loadDomainConfig(props.configDomain)
})

watch(
  () => props.configDomain,
  (newDomain: string) => {
    loadDomainConfig(newDomain)
  }
)
</script>

<style lang="scss" scoped>
@import '@featherds/styles/mixins/typography';
@import '@featherds/styles/mixins/elevation';

.wrapper {
  margin-top: 20px;
  margin-left: 20px;
}

.loading-indicator,
.error-message,
.empty-state {
  padding: 24px;
  text-align: center;
}

.error-message {
  color: var(--feather-error);
}

.custom-editor-placeholder {
  padding: 24px;

  h2 {
    margin-bottom: 8px;
  }
}

.schema-form-container {
  padding: 24px;

  h2 {
    margin-bottom: 16px;
  }
}

.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--feather-border-on-surface);
}
</style>
