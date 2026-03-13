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

export interface ConfigDomainDefinition {
  configName: string
  displayName: string
  description: string
  icon: string
  category: 'core' | 'collection' | 'notifications' | 'provisioning' | 'system'
  customComponent?: string
  defaultConfigId: string
  apiType: 'cm' | 'legacy'
}

export interface OpenApiSchema {
  type: string
  properties?: Record<string, OpenApiSchemaProperty>
  required?: string[]
  items?: OpenApiSchemaProperty
  additionalProperties?: boolean
}

export interface OpenApiSchemaProperty {
  type: string
  description?: string
  default?: any
  enum?: string[]
  format?: string
  pattern?: string
  minimum?: number
  maximum?: number
  minLength?: number
  maxLength?: number
  items?: OpenApiSchemaProperty
  properties?: Record<string, OpenApiSchemaProperty>
  required?: string[]
  $ref?: string
}

export interface AdminConfigState {
  configNames: string[]
  currentConfigName: string | null
  currentConfigId: string | null
  currentConfigData: Record<string, any> | null
  currentSchema: OpenApiSchema | null
  configIds: string[]
  isLoading: boolean
  isSaving: boolean
  isDirty: boolean
  error: string | null
  searchTerm: string
}
