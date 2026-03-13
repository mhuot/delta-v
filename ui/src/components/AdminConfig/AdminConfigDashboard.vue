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
  <div class="admin-config-dashboard">
    <div class="search-container">
      <FeatherInput
        label="Search configurations"
        :modelValue="searchTerm"
        @update:modelValue="handleSearchUpdate"
      />
    </div>

    <div v-if="adminConfigStore.isLoading" class="loading-indicator">
      Loading configurations...
    </div>

    <div v-else-if="filteredCategoryEntries.length === 0" class="empty-state">
      <p v-if="searchTerm">No configurations match your search.</p>
      <p v-else>No configurations available.</p>
    </div>

    <template v-else>
      <div
        v-for="[categoryName, categoryDomains] in filteredCategoryEntries"
        :key="categoryName"
        class="category-section"
      >
        <h3 class="category-header">{{ categoryName }}</h3>
        <div class="feather-row">
          <div
            v-for="domain in categoryDomains"
            :key="domain.configName"
            class="feather-col-4"
          >
            <div
              class="config-card"
              @click="navigateToDomain(domain.configName)"
              role="button"
              :tabindex="0"
              @keydown.enter="navigateToDomain(domain.configName)"
            >
              <div class="card-icon">{{ domain.icon }}</div>
              <div class="card-title">{{ domain.displayName }}</div>
              <div class="card-description">{{ domain.description }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { FeatherInput } from '@featherds/input'
import { useAdminConfigStore } from '@/stores/adminConfigStore'
import { getConfigDomainsByCategory } from '@/components/AdminConfig/configDomainRegistry'
import type { ConfigDomainDefinition } from '@/types/adminConfig'

const router = useRouter()
const adminConfigStore = useAdminConfigStore()

const searchTerm = ref('')

const handleSearchUpdate = (value: string | number | undefined) => {
  searchTerm.value = String(value ?? '')
}

const categoryMap = computed<Map<string, ConfigDomainDefinition[]>>(() => {
  return getConfigDomainsByCategory()
})

const filteredCategoryEntries = computed<[string, ConfigDomainDefinition[]][]>(() => {
  const lowerSearchTerm = searchTerm.value.toLowerCase()
  const filteredEntries: [string, ConfigDomainDefinition[]][] = []

  for (const [categoryName, categoryDomains] of categoryMap.value) {
    const matchingDomains = lowerSearchTerm
      ? categoryDomains.filter(
          (domain: ConfigDomainDefinition) =>
            domain.displayName.toLowerCase().includes(lowerSearchTerm) ||
            domain.description.toLowerCase().includes(lowerSearchTerm) ||
            domain.configName.toLowerCase().includes(lowerSearchTerm)
        )
      : categoryDomains

    if (matchingDomains.length > 0) {
      filteredEntries.push([categoryName, matchingDomains])
    }
  }

  return filteredEntries
})

const navigateToDomain = (configName: string) => {
  router.push(`/admin-config/${configName}`)
}
</script>

<style lang="scss" scoped>
@import '@featherds/styles/mixins/typography';

.admin-config-dashboard {
  padding: 16px;
}

.search-container {
  margin-bottom: 24px;
  max-width: 400px;
}

.loading-indicator,
.empty-state {
  padding: 24px;
  text-align: center;
}

.category-section {
  margin-bottom: 24px;
}

.category-header {
  text-transform: capitalize;
  font-weight: 600;
  font-size: 18px;
  margin-bottom: 12px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--feather-border-on-surface);
}

.config-card {
  padding: 16px;
  border: 1px solid var(--feather-border-on-surface);
  border-radius: 4px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  margin-bottom: 16px;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }

  &:focus {
    outline: 2px solid var(--feather-primary);
    outline-offset: 2px;
  }

  .card-icon {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: var(--feather-background);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 8px;
    font-size: 14px;
    color: var(--feather-primary);
  }

  .card-title {
    font-weight: 600;
    font-size: 16px;
    margin-bottom: 4px;
  }

  .card-description {
    font-size: 14px;
    color: var(--feather-secondary-text-on-surface);
  }
}
</style>
