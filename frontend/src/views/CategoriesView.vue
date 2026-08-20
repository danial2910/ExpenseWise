<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import Dialog from 'primevue/dialog'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import { createCategory, deleteCategory, fetchCategories, updateCategory } from '../api/categories'
import { categoryIconClass, ICON_PICKER_OPTIONS } from '../lib/categoryIcons'
import type { CategoryResponse, CategoryType } from '../types/category'
import type { ApiErrorResponse } from '../types/auth'

type LoadState = 'loading' | 'error' | 'ready'
type TypeFilter = 'ALL' | CategoryType

const loadState = ref<LoadState>('loading')
const categories = ref<CategoryResponse[]>([])

const typeFilterOptions: { label: string; value: TypeFilter }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Income', value: 'INCOME' },
  { label: 'Expense', value: 'EXPENSE' },
]
const typeFilter = ref<TypeFilter>('ALL')

function matchesFilter(category: CategoryResponse) {
  return typeFilter.value === 'ALL' || category.type === typeFilter.value
}

const systemCategories = computed(() => categories.value.filter((c) => c.system && matchesFilter(c)))
const customCategories = computed(() => categories.value.filter((c) => !c.system && matchesFilter(c)))

async function loadCategories() {
  loadState.value = 'loading'
  try {
    categories.value = await fetchCategories()
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}
onMounted(loadCategories)

function typeLabel(type: CategoryType) {
  return type === 'INCOME' ? 'Income' : 'Expense'
}
function typeColorClass(type: CategoryType) {
  return type === 'INCOME' ? 'text-success' : 'text-danger'
}
function typePillClass(type: CategoryType) {
  return type === 'INCOME' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'
}

// --- add/edit editor ---
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const editName = ref('')
const editType = ref<CategoryType>('EXPENSE')
const editIcon = ref<string>(ICON_PICKER_OPTIONS[0])
const nameTouched = ref(false)
const saveError = ref('')
const saving = ref(false)

const typeToggleOptions: { label: string; value: CategoryType }[] = [
  { label: 'Expense', value: 'EXPENSE' },
  { label: 'Income', value: 'INCOME' },
]

const editorTitle = computed(() => (editingId.value === null ? 'Add category' : 'Edit category'))
const nameError = computed(() =>
  nameTouched.value && !editName.value.trim() ? 'Category name is required.' : '',
)

function openAdd() {
  editingId.value = null
  editName.value = ''
  editType.value = 'EXPENSE'
  editIcon.value = ICON_PICKER_OPTIONS[0]
  nameTouched.value = false
  saveError.value = ''
  editorOpen.value = true
}

function openEdit(category: CategoryResponse) {
  editingId.value = category.id
  editName.value = category.name
  editType.value = category.type
  editIcon.value = category.icon ?? ICON_PICKER_OPTIONS[0]
  nameTouched.value = false
  saveError.value = ''
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

async function saveEditor() {
  nameTouched.value = true
  if (!editName.value.trim()) {
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    const request = { name: editName.value.trim(), type: editType.value, icon: editIcon.value }
    if (editingId.value === null) {
      categories.value.push(await createCategory(request))
    } else {
      const updated = await updateCategory(editingId.value, request)
      const index = categories.value.findIndex((c) => c.id === editingId.value)
      if (index !== -1) {
        categories.value[index] = updated
      }
    }
    editorOpen.value = false
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.fieldErrors?.name) {
      saveError.value = error.response.data.fieldErrors.name
    } else {
      saveError.value = 'Something went wrong. Please try again.'
    }
  } finally {
    saving.value = false
  }
}

// --- delete ---
const deleteErrorId = ref<number | null>(null)
const deleteErrorMessage = ref('')

async function onDelete(category: CategoryResponse) {
  deleteErrorId.value = null
  deleteErrorMessage.value = ''
  try {
    await deleteCategory(category.id)
    categories.value = categories.value.filter((c) => c.id !== category.id)
  } catch (error) {
    deleteErrorId.value = category.id
    deleteErrorMessage.value =
      isAxiosError<ApiErrorResponse>(error) && error.response?.status === 409
        ? error.response.data.message
        : 'Could not delete this category.'
  }
}
</script>

<template>
  <AppLayout title="Categories" fab-label="Add category" @fab="openAdd">
    <div class="flex flex-col gap-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Categories</h1>
          <p class="text-sm text-surface-500 mt-1">Organize your income and expenses</p>
        </div>
        <Button
          data-testid="add-category-button"
          label="Add category"
          icon="pi pi-plus"
          class="hidden md:inline-flex active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          @click="openAdd"
        />
      </div>

      <SelectButton
        v-model="typeFilter"
        data-testid="category-type-filter"
        :options="typeFilterOptions"
        option-label="label"
        option-value="value"
        :allow-empty="false"
      />

      <ErrorState
        v-if="loadState === 'error'"
        testid="categories-error-state"
        retry-testid="categories-retry-button"
        title="Couldn't load categories"
        description="Something went wrong while fetching your categories. Check your connection and try again."
        class="bg-surface-0 border border-surface-300 rounded-xl"
        @retry="loadCategories"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="categories-loading-skeleton" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <div v-for="n in 9" :key="n" class="bg-surface-0 border border-surface-300 rounded-xl p-4 flex items-center gap-3">
          <div class="w-10 h-10 rounded-lg bg-surface-200 animate-pulse shrink-0" />
          <div class="flex-1 flex flex-col gap-1.5">
            <div class="w-2/3 h-3.5 rounded bg-surface-200 animate-pulse" />
            <div class="w-2/5 h-3 rounded bg-surface-200 animate-pulse" />
          </div>
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="flex flex-col gap-6">
        <div>
          <h2 class="text-xs font-semibold text-surface-500 uppercase tracking-wide mb-3">System categories</h2>
          <div data-testid="system-categories-grid" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 md:gap-4">
            <div
              v-for="category in systemCategories"
              :key="category.id"
              :data-testid="`system-category-card-${category.id}`"
              class="bg-surface-50 border border-surface-300 rounded-xl p-3 md:p-4 flex items-center gap-3"
            >
              <div class="w-9 h-9 md:w-10 md:h-10 rounded-lg bg-surface-100 text-surface-500 flex items-center justify-center shrink-0">
                <i :class="['pi', categoryIconClass(category.icon)]" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="text-sm font-semibold text-surface-700 truncate">{{ category.name }}</p>
                <div class="flex items-center gap-1.5 mt-1.5">
                  <span class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold" :class="typePillClass(category.type)">
                    {{ typeLabel(category.type) }}
                  </span>
                  <span class="text-[10px] font-semibold text-surface-400 bg-surface-200 px-1.5 py-0.5 rounded-full">System</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div>
          <h2 class="text-xs font-semibold text-surface-500 uppercase tracking-wide mb-3">Your categories</h2>

          <div v-if="deleteErrorMessage" class="mb-3">
            <FormError :message="deleteErrorMessage" testid="category-delete-error-banner" />
          </div>

          <EmptyState
            v-if="customCategories.length === 0"
            testid="custom-categories-empty-state"
            icon="pi-tags"
            title="No custom categories yet"
            description="Create your own categories to track spending exactly how you want."
            class="bg-surface-0 border border-dashed border-surface-300 rounded-xl"
          >
            <template #action>
              <Button
                data-testid="add-first-category-button"
                label="Add your first category"
                icon="pi pi-plus"
                size="small"
                class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
                @click="openAdd"
              />
            </template>
          </EmptyState>

          <div v-else data-testid="custom-categories-grid" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 md:gap-4">
            <div
              v-for="category in customCategories"
              :key="category.id"
              :data-testid="`custom-category-card-${category.id}`"
              class="bg-surface-0 border border-surface-300 rounded-xl p-3 md:p-4 flex items-center gap-3"
            >
              <div class="w-9 h-9 md:w-10 md:h-10 rounded-lg bg-primary-50 text-primary-300 flex items-center justify-center shrink-0">
                <i :class="['pi', categoryIconClass(category.icon)]" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="text-sm font-semibold text-surface-900 truncate">{{ category.name }}</p>
                <span class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold mt-1" :class="typePillClass(category.type)">
                  {{ typeLabel(category.type) }}
                </span>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <button
                  :data-testid="`category-edit-button-${category.id}`"
                  class="w-11 h-11 md:w-7 md:h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-50 hover:text-surface-800 transition-colors duration-fast ease-out-expo"
                  @click="openEdit(category)"
                >
                  <i class="pi pi-pencil text-xs" />
                </button>
                <button
                  :data-testid="`category-delete-button-${category.id}`"
                  class="w-11 h-11 md:w-7 md:h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-danger-bg hover:text-danger transition-colors duration-fast ease-out-expo"
                  @click="onDelete(category)"
                >
                  <i class="pi pi-trash text-xs" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <Dialog
      v-model:visible="editorOpen"
      modal
      :header="editorTitle"
      :style="{ width: '420px' }"
      :breakpoints="{ '768px': '92vw' }"
      data-testid="category-editor-dialog"
    >
      <div class="flex flex-col gap-4">
        <FormError v-if="saveError" :message="saveError" testid="category-save-error-banner" />

        <div>
          <label for="category-name-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Name</label>
          <InputText
            id="category-name-input"
            v-model="editName"
            data-testid="category-name-input"
            class="w-full"
            placeholder="e.g. Pet Care"
            :invalid="!!nameError"
            @blur="nameTouched = true"
          />
          <p v-if="nameError" class="text-xs text-danger mt-1.5">{{ nameError }}</p>
        </div>

        <div>
          <span class="block text-xs font-semibold text-surface-600 mb-1.5">Type</span>
          <SelectButton
            v-model="editType"
            data-testid="category-type-toggle"
            :options="typeToggleOptions"
            option-label="label"
            option-value="value"
            :allow-empty="false"
          />
        </div>

        <div>
          <span class="block text-xs font-semibold text-surface-600 mb-2">Icon</span>
          <div class="grid grid-cols-7 gap-2">
            <button
              v-for="icon in ICON_PICKER_OPTIONS"
              :key="icon"
              type="button"
              :data-testid="`icon-option-${icon}`"
              class="w-10 h-10 rounded-lg flex items-center justify-center border transition-colors duration-fast ease-out-expo"
              :class="editIcon === icon
                ? 'bg-primary-50 border-primary-500 text-primary-300'
                : 'bg-surface-50 border-surface-200 text-surface-500 hover:border-surface-400'"
              @click="editIcon = icon"
            >
              <i :class="['pi', categoryIconClass(icon)]" />
            </button>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          data-testid="category-cancel-button"
          label="Cancel"
          severity="secondary"
          text
          @click="closeEditor"
        />
        <Button
          data-testid="category-save-button"
          label="Save"
          :loading="saving"
          class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          @click="saveEditor"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>
