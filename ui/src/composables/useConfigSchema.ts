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

import { getConfigSchema } from '@/services/adminConfigService'
import type { OpenApiSchema, OpenApiSchemaProperty } from '@/types/adminConfig'

const schemaCache = new Map<string, OpenApiSchema>()

const getPropertyType = (property: OpenApiSchemaProperty): 'string' | 'number' | 'boolean' | 'array' | 'object' | 'enum' => {
  if (property.enum && property.enum.length > 0) {
    return 'enum'
  }

  switch (property.type) {
    case 'integer':
    case 'number':
      return 'number'
    case 'boolean':
      return 'boolean'
    case 'array':
      return 'array'
    case 'object':
      return 'object'
    default:
      return 'string'
  }
}

const getDefaultValue = (property: OpenApiSchemaProperty): any => {
  if (property.default !== undefined) {
    return property.default
  }

  const propertyType = getPropertyType(property)

  switch (propertyType) {
    case 'string':
      return ''
    case 'number':
      return property.minimum ?? 0
    case 'boolean':
      return false
    case 'array':
      return []
    case 'object':
      return {}
    case 'enum':
      return property.enum?.[0] ?? ''
    default:
      return null
  }
}

const useConfigSchema = () => {
  const schema = ref<OpenApiSchema | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const fetchSchema = async (configName: string) => {
    const cachedSchema = schemaCache.get(configName)
    if (cachedSchema) {
      schema.value = cachedSchema
      return
    }

    isLoading.value = true
    error.value = null
    try {
      const response = await getConfigSchema(configName)
      if (response) {
        schemaCache.set(configName, response)
        schema.value = response
      }
    } catch (err) {
      error.value = `Failed to fetch schema for ${configName}`
      console.error(err)
    } finally {
      isLoading.value = false
    }
  }

  return {
    schema,
    isLoading,
    error,
    fetchSchema,
    getPropertyType,
    getDefaultValue
  }
}

export default useConfigSchema
