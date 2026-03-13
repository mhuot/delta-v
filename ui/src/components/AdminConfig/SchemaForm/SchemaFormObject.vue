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
  <div class="schema-form-object">
    <FeatherExpansionPanel
      :title="displayLabel"
      :modelValue="isExpanded"
      @update:modelValue="(val: boolean) => isExpanded = val"
    >
      <div class="object-fields">
        <template v-for="(childProperty, childFieldName) in property.properties" :key="childFieldName">
          <SchemaFormArray
            v-if="childProperty.type === 'array'"
            :property="childProperty"
            :modelValue="localValue[childFieldName as string] || []"
            :fieldName="String(childFieldName)"
            @update:modelValue="(newValue: any[]) => updateField(String(childFieldName), newValue)"
          />

          <SchemaFormObject
            v-else-if="childProperty.type === 'object' || childProperty.properties"
            :property="childProperty"
            :modelValue="localValue[childFieldName as string] || {}"
            :fieldName="String(childFieldName)"
            @update:modelValue="(newValue: Record<string, any>) => updateField(String(childFieldName), newValue)"
          />

          <SchemaFormField
            v-else
            :property="childProperty"
            :modelValue="localValue[childFieldName as string]"
            :fieldName="String(childFieldName)"
            :required="isFieldRequired(String(childFieldName))"
            @update:modelValue="(newValue: any) => updateField(String(childFieldName), newValue)"
          />
        </template>
      </div>
    </FeatherExpansionPanel>
  </div>
</template>

<script setup lang="ts">
import { FeatherExpansionPanel } from '@featherds/expansion'
import { PropType } from 'vue'
import type { OpenApiSchemaProperty } from '@/types/adminConfig.d.ts'
import SchemaFormField from './SchemaFormField.vue'
import SchemaFormArray from './SchemaFormArray.vue'

const props = defineProps({
  property: { type: Object as PropType<OpenApiSchemaProperty>, required: true },
  modelValue: { type: Object as PropType<Record<string, any>>, default: () => ({}) },
  fieldName: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

const isExpanded = ref(false)

/**
 * Converts a camelCase field name to a human-readable Title Case label.
 */
const displayLabel = computed(() => {
  const withSpaces = props.fieldName.replace(/([A-Z])/g, ' $1')
  return withSpaces.charAt(0).toUpperCase() + withSpaces.slice(1)
})

const localValue = computed(() => props.modelValue || {})

const isFieldRequired = (childFieldName: string): boolean => {
  return props.property.required?.includes(childFieldName) ?? false
}

const updateField = (childFieldName: string, newValue: any) => {
  const updatedObject = { ...localValue.value, [childFieldName]: newValue }
  emit('update:modelValue', updatedObject)
}
</script>

<style lang="scss" scoped>
@import "@featherds/styles/themes/variables";

.schema-form-object {
  margin-bottom: 16px;
}

.object-fields {
  padding: 8px 0;
}
</style>
