<!--
Licensed to The OpenNMS Group, Inc (TOG) under one or more
contributor license agreements.  See the LICENSE.md file
distributed with this work for additional information
regarding copyright ownership.

TOG licenses this file to You under the GNU Affero General
Public License Version 3 (the "License") or (at your option)
any later version.  You may not use this file except in
compliance with the License.  You may obtain a copy of the
License at:

     https://www.gnu.org/licenses/agpl-3.0.txt

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied.  See the License for the specific
language governing permissions and limitations under the
License.
-->

<template>
  <div class="schema-form-renderer">
    <div class="form-header">
      <h2 class="form-title">{{ displayConfigName }}</h2>
    </div>

    <div class="form-body">
      <div class="feather-row">
        <template v-for="(property, fieldName) in schema.properties" :key="fieldName">
          <div
            :class="getFieldColumnClass(property)"
          >
            <SchemaFormArray
              v-if="property.type === 'array'"
              :property="property"
              :modelValue="localValue[fieldName as string] || []"
              :fieldName="String(fieldName)"
              @update:modelValue="(newValue: any[]) => updateField(String(fieldName), newValue)"
            />

            <SchemaFormObject
              v-else-if="property.type === 'object' || property.properties"
              :property="property"
              :modelValue="localValue[fieldName as string] || {}"
              :fieldName="String(fieldName)"
              @update:modelValue="(newValue: Record<string, any>) => updateField(String(fieldName), newValue)"
            />

            <SchemaFormField
              v-else
              :property="property"
              :modelValue="localValue[fieldName as string]"
              :fieldName="String(fieldName)"
              :required="isFieldRequired(String(fieldName))"
              @update:modelValue="(newValue: any) => updateField(String(fieldName), newValue)"
            />
          </div>
        </template>
      </div>
    </div>

    <div class="form-actions">
      <FeatherButton
        text
        @click="emit('reset')"
      >
        Reset
      </FeatherButton>
      <FeatherButton
        secondary
        @click="emit('cancel')"
      >
        Cancel
      </FeatherButton>
      <FeatherButton
        primary
        :disabled="!isDirty || hasValidationErrors"
        @click="emit('save')"
      >
        Save
      </FeatherButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FeatherButton } from '@featherds/button'
import { PropType } from 'vue'
import type { OpenApiSchema, OpenApiSchemaProperty } from '@/types/adminConfig.d.ts'
import { validateField } from './SchemaFormFieldFactory'
import SchemaFormField from './SchemaFormField.vue'
import SchemaFormArray from './SchemaFormArray.vue'
import SchemaFormObject from './SchemaFormObject.vue'

const props = defineProps({
  schema: { type: Object as PropType<OpenApiSchema>, required: true },
  modelValue: { type: Object as PropType<Record<string, any>>, required: true },
  configName: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue', 'save', 'cancel', 'reset'])

/**
 * Deep clone of the original data taken on mount, used to track dirty state.
 */
const originalData = ref<Record<string, any>>({})

onMounted(() => {
  originalData.value = JSON.parse(JSON.stringify(props.modelValue))
})

watch(() => props.modelValue, (newValue: Record<string, any>) => {
  if (!originalData.value || Object.keys(originalData.value).length === 0) {
    originalData.value = JSON.parse(JSON.stringify(newValue))
  }
}, { immediate: true })

/**
 * Converts the configName to a human-readable Title Case label.
 */
const displayConfigName = computed(() => {
  const withSpaces = props.configName.replace(/([A-Z])/g, ' $1')
  return withSpaces.charAt(0).toUpperCase() + withSpaces.slice(1)
})

const localValue = computed(() => props.modelValue || {})

/**
 * Determines whether the form has been modified from its original state.
 */
const isDirty = computed(() => {
  return JSON.stringify(props.modelValue) !== JSON.stringify(originalData.value)
})

/**
 * Checks all top-level primitive fields for validation errors.
 */
const hasValidationErrors = computed(() => {
  if (!props.schema.properties) return false

  const schemaProperties = props.schema.properties as Record<string, OpenApiSchemaProperty>
  for (const [fieldName, property] of Object.entries(schemaProperties)) {
    const fieldValue = localValue.value[fieldName]
    const isRequired = isFieldRequired(fieldName)

    if (isPrimitiveProperty(property)) {
      const errorMessage = validateField(fieldValue, property, isRequired)
      if (errorMessage) return true
    }
  }

  return false
})

const isFieldRequired = (fieldName: string): boolean => {
  return props.schema.required?.includes(fieldName) ?? false
}

/**
 * Returns true for property types that are rendered as simple form fields
 * (not arrays or nested objects).
 */
const isPrimitiveProperty = (property: OpenApiSchemaProperty): boolean => {
  return property.type !== 'array' && property.type !== 'object' && !property.properties
}

/**
 * Primitive fields get feather-col-6 for side-by-side layout;
 * complex fields (arrays, objects) get feather-col-12 for full width.
 */
const getFieldColumnClass = (property: OpenApiSchemaProperty): string => {
  if (isPrimitiveProperty(property)) {
    return 'feather-col-6'
  }
  return 'feather-col-12'
}

const updateField = (fieldName: string, newValue: any) => {
  const updatedData = { ...localValue.value, [fieldName]: newValue }
  emit('update:modelValue', updatedData)
}
</script>

<style lang="scss" scoped>
@import "@featherds/styles/mixins/typography";
@import "@featherds/styles/themes/variables";

.schema-form-renderer {
  padding: 16px 0;
}

.form-header {
  margin-bottom: 24px;
}

.form-title {
  @include headline3();
}

.form-body {
  margin-bottom: 24px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var($border-on-surface);
}
</style>
