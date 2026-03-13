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
  <div class="schema-form-field">
    <FeatherCheckbox
      v-if="componentMapping.component === 'FeatherCheckbox'"
      :modelValue="!!modelValue"
      @update:modelValue="handleUpdate"
    >
      {{ displayLabel }}
      <span v-if="required" class="required-indicator">*</span>
    </FeatherCheckbox>

    <FeatherSelect
      v-else-if="componentMapping.component === 'FeatherSelect'"
      :label="displayLabel + (required ? ' *' : '')"
      :hint="property.description || ''"
      textProp="text"
      :options="componentMapping.props.options"
      :modelValue="selectedEnumOption"
      @update:modelValue="handleEnumUpdate"
      :error="validationError || ''"
    />

    <FeatherTextarea
      v-else-if="componentMapping.component === 'FeatherTextarea'"
      :label="displayLabel + (required ? ' *' : '')"
      :hint="property.description || ''"
      :modelValue="modelValue ?? ''"
      @update:modelValue="handleUpdate"
      :error="validationError || ''"
    />

    <FeatherInput
      v-else-if="componentMapping.component === 'FeatherInput'"
      :type="componentMapping.props.type"
      :label="displayLabel + (required ? ' *' : '')"
      :hint="property.description || ''"
      :modelValue="modelValue ?? ''"
      @update:modelValue="handleUpdate"
      :error="validationError || ''"
    />

    <div v-if="validationError && componentMapping.component === 'FeatherCheckbox'" class="validation-error">
      {{ validationError }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { FeatherInput } from '@featherds/input'
import { FeatherSelect } from '@featherds/select'
import { FeatherCheckbox } from '@featherds/checkbox'
import { FeatherTextarea } from '@featherds/textarea'
import { PropType } from 'vue'
import type { OpenApiSchemaProperty } from '@/types/adminConfig.d.ts'
import { mapSchemaTypeToComponent, validateField } from './SchemaFormFieldFactory'

const props = defineProps({
  property: { type: Object as PropType<OpenApiSchemaProperty>, required: true },
  modelValue: { type: [String, Number, Boolean, Object, Array] as PropType<any>, default: undefined },
  fieldName: { type: String, required: true },
  required: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const componentMapping = computed(() => mapSchemaTypeToComponent(props.property))

/**
 * Converts a camelCase field name to a human-readable Title Case label.
 * For example, "importSchedule" becomes "Import Schedule".
 */
const displayLabel = computed(() => {
  const withSpaces = props.fieldName.replace(/([A-Z])/g, ' $1')
  return withSpaces.charAt(0).toUpperCase() + withSpaces.slice(1)
})

/**
 * For enum selects, we need to find the matching option object
 * from the options array based on the current model value.
 */
const selectedEnumOption = computed(() => {
  if (componentMapping.value.component !== 'FeatherSelect') return null
  const options = componentMapping.value.props.options || []
  return options.find((option: { value: string }) => option.value === props.modelValue) || null
})

const validationError = computed(() => {
  return validateField(props.modelValue, props.property, props.required)
})

const handleUpdate = (newValue: any) => {
  emit('update:modelValue', newValue)
}

const handleEnumUpdate = (selectedOption: any) => {
  emit('update:modelValue', selectedOption?.value ?? null)
}
</script>

<style lang="scss" scoped>
@import "@featherds/styles/themes/variables";

.schema-form-field {
  margin-bottom: 16px;
}

.required-indicator {
  color: var($error);
  margin-left: 2px;
}

.validation-error {
  color: var($error);
  font-size: 12px;
  margin-top: 4px;
}
</style>
