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

import type { OpenApiSchemaProperty } from '@/types/adminConfig.d.ts'

interface ComponentMapping {
  component: string
  props: Record<string, any>
}

/**
 * Maps an OpenAPI schema property to the appropriate Feather Design System
 * component and its props for rendering in a schema-driven form.
 */
export const mapSchemaTypeToComponent = (property: OpenApiSchemaProperty): ComponentMapping => {
  if (property.enum && property.enum.length > 0) {
    const enumOptions = property.enum.map((enumValue) => ({
      text: enumValue,
      value: enumValue
    }))
    return { component: 'FeatherSelect', props: { options: enumOptions } }
  }

  if (property.type === 'array') {
    return { component: 'SchemaFormArray', props: {} }
  }

  if (property.type === 'object' || property.properties) {
    return { component: 'SchemaFormObject', props: {} }
  }

  if (property.type === 'boolean') {
    return { component: 'FeatherCheckbox', props: {} }
  }

  if (property.type === 'number' || property.type === 'integer') {
    return { component: 'FeatherInput', props: { type: 'number' } }
  }

  if (property.type === 'string') {
    const isTextarea = property.format === 'textarea' ||
      (property.maxLength !== undefined && property.maxLength > 200)

    if (isTextarea) {
      return { component: 'FeatherTextarea', props: {} }
    }

    return { component: 'FeatherInput', props: { type: 'text' } }
  }

  return { component: 'FeatherInput', props: { type: 'text' } }
}

/**
 * Returns a sensible default value for a schema property based on its type
 * and any declared default in the schema.
 */
export const getDefaultValue = (property: OpenApiSchemaProperty): any => {
  if (property.default !== undefined) {
    return property.default
  }

  switch (property.type) {
    case 'string':
      return ''
    case 'number':
    case 'integer':
      return property.minimum ?? 0
    case 'boolean':
      return false
    case 'array':
      return []
    case 'object':
      return {}
    default:
      return null
  }
}

/**
 * Validates a field value against the constraints defined in its schema property.
 * Returns an error message string if validation fails, or null if the value is valid.
 */
export const validateField = (
  value: any,
  property: OpenApiSchemaProperty,
  isRequired = false
): string | null => {
  const isEmpty = value === undefined || value === null || value === ''

  if (isRequired && isEmpty) {
    return 'This field is required'
  }

  if (isEmpty) {
    return null
  }

  if (property.type === 'string' && typeof value === 'string') {
    if (property.minLength !== undefined && value.length < property.minLength) {
      return `Must be at least ${property.minLength} characters`
    }

    if (property.maxLength !== undefined && value.length > property.maxLength) {
      return `Must be at most ${property.maxLength} characters`
    }

    if (property.pattern) {
      const patternRegex = new RegExp(property.pattern)
      if (!patternRegex.test(value)) {
        return `Must match pattern: ${property.pattern}`
      }
    }
  }

  if ((property.type === 'number' || property.type === 'integer') && typeof value === 'number') {
    if (property.minimum !== undefined && value < property.minimum) {
      return `Must be at least ${property.minimum}`
    }

    if (property.maximum !== undefined && value > property.maximum) {
      return `Must be at most ${property.maximum}`
    }

    if (property.type === 'integer' && !Number.isInteger(value)) {
      return 'Must be a whole number'
    }
  }

  return null
}
