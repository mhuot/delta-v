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
  <div class="sidebar">
    <h3 class="sidebar-title">Configuration</h3>

    <template v-for="[categoryName, categoryDomains] in categoryEntries" :key="categoryName">
      <div class="sidebar-category">{{ categoryName }}</div>
      <router-link
        v-for="domain in categoryDomains"
        :key="domain.configName"
        :to="`/admin-config/${domain.configName}`"
        class="sidebar-item"
        :class="{ active: isActiveDomain(domain.configName) }"
      >
        {{ domain.displayName }}
      </router-link>
    </template>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { getConfigDomainsByCategory } from '@/components/AdminConfig/configDomainRegistry'
import type { ConfigDomainDefinition } from '@/types/adminConfig'

const route = useRoute()

const categoryEntries = computed<[string, ConfigDomainDefinition[]][]>(() => {
  const categoryMap = getConfigDomainsByCategory()
  return Array.from(categoryMap.entries())
})

const isActiveDomain = (configName: string): boolean => {
  return route.params.configDomain === configName
}
</script>

<style lang="scss" scoped>
@import '@featherds/styles/mixins/typography';

.sidebar {
  border-right: 1px solid var(--feather-border-on-surface);
  padding: 16px;
  min-height: 400px;
}

.sidebar-title {
  font-weight: 600;
  font-size: 18px;
  margin-bottom: 16px;
}

.sidebar-category {
  font-weight: 600;
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.5px;
  color: var(--feather-secondary-text-on-surface);
  margin: 16px 0 8px;
}

.sidebar-item {
  padding: 8px 12px;
  border-radius: 4px;
  display: block;
  text-decoration: none;
  color: var(--feather-primary-text-on-surface);

  &:hover {
    background: var(--feather-background);
  }

  &.active {
    background: var(--feather-background);
    font-weight: 600;
  }
}
</style>
