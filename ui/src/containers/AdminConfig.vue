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
            <AdminConfigDashboard />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BreadCrumbs from '@/components/Layout/BreadCrumbs.vue'
import AdminConfigSidebar from '@/components/AdminConfig/AdminConfigSidebar.vue'
import AdminConfigDashboard from '@/components/AdminConfig/AdminConfigDashboard.vue'
import { useAdminConfigStore } from '@/stores/adminConfigStore'
import { useMenuStore } from '@/stores/menuStore'
import { BreadCrumb } from '@/types'

const adminConfigStore = useAdminConfigStore()
const menuStore = useMenuStore()

const homeUrl = computed<string>(() => menuStore.mainMenu.homeUrl)

const breadcrumbs = computed<BreadCrumb[]>(() => {
  return [
    { label: 'Home', to: homeUrl.value, isAbsoluteLink: true },
    { label: 'Configuration', to: '#', position: 'last' }
  ]
})

onMounted(() => {
  adminConfigStore.fetchConfigNames()
})
</script>

<style lang="scss" scoped>
@import '@featherds/styles/mixins/typography';
@import '@featherds/styles/mixins/elevation';

.wrapper {
  margin-top: 20px;
  margin-left: 20px;
}
</style>
