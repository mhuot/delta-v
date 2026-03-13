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
  <div class="schema-form-array">
    <h4 class="array-header">{{ displayLabel }}</h4>
    <p v-if="property.description" class="array-description">{{ property.description }}</p>

    <div
      v-for="(item, index) in localItems"
      :key="index"
      class="array-item-card"
    >
      <div class="array-item-content">
        <SchemaFormObject
          v-if="isObjectItems"
          :property="property.items!"
          :modelValue="item"
          :fieldName="`${fieldName} ${Number(index) + 1}`"
          @update:modelValue="(newValue: Record<string, any>) => updateItem(Number(index), newValue)"
        />
        <SchemaFormField
          v-else
          :property="property.items!"
          :modelValue="item"
          :fieldName="`${fieldName} ${Number(index) + 1}`"
          :required="false"
          @update:modelValue="(newValue: any) => updateItem(Number(index), newValue)"
        />
      </div>
      <div class="array-item-actions">
        <FeatherButton
          icon="Remove Item"
          @click="removeItem(Number(index))"
        >
          <FeatherIcon :icon="DeleteIcon" class="delete-icon" />
        </FeatherButton>
      </div>
    </div>

    <div class="array-add-button">
      <FeatherButton primary @click="addItem">
        Add {{ displayLabel }}
      </FeatherButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FeatherButton } from '@featherds/button'
import { FeatherIcon } from '@featherds/icon'
import DeleteIcon from '@featherds/icon/action/Delete'
import { PropType } from 'vue'
import type { OpenApiSchemaProperty } from '@/types/adminConfig.d.ts'
import { getDefaultValue } from './SchemaFormFieldFactory'
import SchemaFormField from './SchemaFormField.vue'
import SchemaFormObject from './SchemaFormObject.vue'

const props = defineProps({
  property: { type: Object as PropType<OpenApiSchemaProperty>, required: true },
  modelValue: { type: Array as PropType<any[]>, default: () => [] },
  fieldName: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

/**
 * Converts a camelCase field name to a human-readable Title Case label.
 */
const displayLabel = computed(() => {
  const withSpaces = props.fieldName.replace(/([A-Z])/g, ' $1')
  return withSpaces.charAt(0).toUpperCase() + withSpaces.slice(1)
})

const localItems = computed(() => props.modelValue || [])

const isObjectItems = computed(() => {
  return props.property.items?.type === 'object' || !!props.property.items?.properties
})

const addItem = () => {
  const itemSchema = props.property.items
  if (!itemSchema) return

  const newItemValue = getDefaultValue(itemSchema)
  const updatedItems = [...localItems.value, newItemValue]
  emit('update:modelValue', updatedItems)
}

const removeItem = (index: number) => {
  const updatedItems = localItems.value.filter((_: any, itemIndex: number) => itemIndex !== index)
  emit('update:modelValue', updatedItems)
}

const updateItem = (index: number, newValue: any) => {
  const updatedItems = [...localItems.value]
  updatedItems[index] = newValue
  emit('update:modelValue', updatedItems)
}
</script>

<style lang="scss" scoped>
@import "@featherds/styles/mixins/typography";
@import "@featherds/styles/themes/variables";

.schema-form-array {
  margin-bottom: 24px;
}

.array-header {
  @include headline4();
  margin-bottom: 4px;
}

.array-description {
  @include body-small();
  color: var($secondary-text-on-surface);
  margin-bottom: 12px;
}

.array-item-card {
  display: flex;
  align-items: flex-start;
  border: 1px solid var($border-on-surface);
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 8px;
}

.array-item-content {
  flex: 1;
}

.array-item-actions {
  flex-shrink: 0;
  margin-left: 8px;
}

.delete-icon {
  color: var($error);
}

.array-add-button {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
