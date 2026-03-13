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

import { defineStore } from 'pinia'
import {
  getConfigNames as fetchConfigNamesApi,
  getConfigSchema as fetchConfigSchemaApi,
  getConfigIds as fetchConfigIdsApi,
  getConfig as fetchConfigApi,
  createConfig as createConfigApi,
  updateConfig as updateConfigApi,
  deleteConfig as deleteConfigApi
} from '@/services/adminConfigService'
import type { OpenApiSchema } from '@/types/adminConfig'

export const useAdminConfigStore = defineStore('adminConfigStore', () => {
  const configNames = ref<string[]>([])
  const currentConfigName = ref<string | null>(null)
  const currentConfigId = ref<string | null>(null)
  const currentConfigData = ref<Record<string, any> | null>(null)
  const currentSchema = ref<OpenApiSchema | null>(null)
  const configIds = ref<string[]>([])
  const isLoading = ref(false)
  const isSaving = ref(false)
  const isDirty = ref(false)
  const error = ref<string | null>(null)
  const searchTerm = ref('')

  const filteredConfigNames = computed(() => {
    if (!searchTerm.value) {
      return configNames.value
    }
    const lowerSearchTerm = searchTerm.value.toLowerCase()
    return configNames.value.filter((name) => name.toLowerCase().includes(lowerSearchTerm))
  })

  const hasUnsavedChanges = computed(() => isDirty.value)

  const fetchConfigNames = async () => {
    isLoading.value = true
    error.value = null
    try {
      const response = await fetchConfigNamesApi()
      if (response) {
        configNames.value = response
      }
    } catch (err) {
      error.value = 'Failed to fetch config names'
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  const fetchConfigSchema = async (configName: string) => {
    isLoading.value = true
    error.value = null
    try {
      const response = await fetchConfigSchemaApi(configName)
      if (response) {
        currentSchema.value = response
      }
    } catch (err) {
      error.value = `Failed to fetch schema for ${configName}`
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  const fetchConfigIds = async (configName: string) => {
    isLoading.value = true
    error.value = null
    try {
      const response = await fetchConfigIdsApi(configName)
      if (response) {
        configIds.value = response
      }
    } catch (err) {
      error.value = `Failed to fetch config IDs for ${configName}`
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  const fetchConfig = async (configName: string, configId: string) => {
    isLoading.value = true
    error.value = null
    try {
      const response = await fetchConfigApi(configName, configId)
      if (response) {
        currentConfigData.value = response
        currentConfigId.value = configId
        isDirty.value = false
      }
    } catch (err) {
      error.value = `Failed to fetch config ${configName}/${configId}`
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  const saveConfig = async () => {
    if (!currentConfigName.value || !currentConfigId.value || !currentConfigData.value) {
      error.value = 'No config selected to save'
      return
    }

    isSaving.value = true
    error.value = null
    try {
      const configIdExists = configIds.value.includes(currentConfigId.value)
      if (configIdExists) {
        await updateConfigApi(currentConfigName.value, currentConfigId.value, currentConfigData.value)
      } else {
        await createConfigApi(currentConfigName.value, currentConfigId.value, currentConfigData.value)
      }
      isDirty.value = false
    } catch (err) {
      error.value = 'Failed to save config'
      console.error(err)
    } finally {
      isSaving.value = false
    }
  }

  const deleteCurrentConfig = async () => {
    if (!currentConfigName.value || !currentConfigId.value) {
      error.value = 'No config selected to delete'
      return
    }

    isLoading.value = true
    error.value = null
    try {
      await deleteConfigApi(currentConfigName.value, currentConfigId.value)
      currentConfigData.value = null
      currentConfigId.value = null
      isDirty.value = false
      await fetchConfigIds(currentConfigName.value)
    } catch (err) {
      error.value = 'Failed to delete config'
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  const setCurrentDomain = async (configName: string) => {
    currentConfigName.value = configName
    currentConfigId.value = null
    currentConfigData.value = null
    currentSchema.value = null
    isDirty.value = false
    error.value = null

    await Promise.all([
      fetchConfigSchema(configName),
      fetchConfigIds(configName)
    ])
  }

  const setDirty = (dirty: boolean) => {
    isDirty.value = dirty
  }

  const resetError = () => {
    error.value = null
  }

  const $reset = () => {
    configNames.value = []
    currentConfigName.value = null
    currentConfigId.value = null
    currentConfigData.value = null
    currentSchema.value = null
    configIds.value = []
    isLoading.value = false
    isSaving.value = false
    isDirty.value = false
    error.value = null
    searchTerm.value = ''
  }

  return {
    configNames,
    currentConfigName,
    currentConfigId,
    currentConfigData,
    currentSchema,
    configIds,
    isLoading,
    isSaving,
    isDirty,
    error,
    searchTerm,
    filteredConfigNames,
    hasUnsavedChanges,
    fetchConfigNames,
    fetchConfigSchema,
    fetchConfigIds,
    fetchConfig,
    saveConfig,
    deleteCurrentConfig,
    setCurrentDomain,
    setDirty,
    resetError,
    $reset
  }
})
