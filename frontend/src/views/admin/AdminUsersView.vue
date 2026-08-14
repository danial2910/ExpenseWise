<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import Dialog from 'primevue/dialog'
import Drawer from 'primevue/drawer'
import Paginator from 'primevue/paginator'
import AppLayout from '../../layouts/AppLayout.vue'
import FormError from '../../components/common/FormError.vue'
import {
  createAdminUser,
  deleteAdminUser,
  fetchAdminUser,
  fetchAdminUsers,
  updateAdminUserAccess,
  updateAdminUserStatus,
} from '../../api/admin'
import { ALL_FEATURES, FEATURE_LABELS } from '../../types/admin'
import type { Feature } from '../../types/admin'
import type { UserResponse, ApiErrorResponse } from '../../types/auth'

type LoadState = 'loading' | 'error' | 'ready'
type RoleFilter = 'ALL' | 'USER' | 'ADMIN'
type StatusFilter = 'ALL' | 'ACTIVE' | 'DISABLED'

const PAGE_SIZE = 20

const loadState = ref<LoadState>('loading')
const users = ref<UserResponse[]>([])
const totalRecords = ref(0)
const page = ref(0)

const search = ref('')
const roleFilter = ref<RoleFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')

const roleFilterOptions = [
  { label: 'All roles', value: 'ALL' },
  { label: 'User', value: 'USER' },
  { label: 'Admin', value: 'ADMIN' },
]
const statusFilterOptions = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Disabled', value: 'DISABLED' },
]

async function loadUsers() {
  loadState.value = 'loading'
  try {
    const response = await fetchAdminUsers({
      search: search.value.trim() || undefined,
      role: roleFilter.value === 'ALL' ? undefined : roleFilter.value,
      active: statusFilter.value === 'ALL' ? undefined : statusFilter.value === 'ACTIVE',
      page: page.value,
      size: PAGE_SIZE,
    })
    users.value = response.content
    totalRecords.value = response.totalElements
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

onMounted(loadUsers)
watch([search, roleFilter, statusFilter], () => {
  page.value = 0
  loadUsers()
})

function onPageChange(event: { page: number }) {
  page.value = event.page
  loadUsers()
}

function initials(name: string) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

// --- create/edit panel ---
const panelOpen = ref(false)
const panelMode = ref<'create' | 'edit'>('create')
const editingUser = ref<UserResponse | null>(null)

const formName = ref('')
const formEmail = ref('')
const formRole = ref<'USER' | 'ADMIN'>('USER')
const formActive = ref(true)
const formFeatures = ref<Record<Feature, boolean>>(defaultFeatures(true))
const formNameTouched = ref(false)
const formEmailTouched = ref(false)
const saving = ref(false)
const saveError = ref('')

function defaultFeatures(enabled: boolean): Record<Feature, boolean> {
  const result = {} as Record<Feature, boolean>
  for (const feature of ALL_FEATURES) {
    result[feature] = enabled
  }
  return result
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const nameError = computed(() => (formNameTouched.value && !formName.value.trim() ? 'Full name is required.' : ''))
const emailError = computed(() =>
  formEmailTouched.value && !emailPattern.test(formEmail.value.trim()) ? 'Enter a valid email address.' : '',
)

function openCreate() {
  panelMode.value = 'create'
  editingUser.value = null
  formName.value = ''
  formEmail.value = ''
  formRole.value = 'USER'
  formActive.value = true
  formFeatures.value = defaultFeatures(true)
  formNameTouched.value = false
  formEmailTouched.value = false
  saveError.value = ''
  panelOpen.value = true
}

async function openEdit(user: UserResponse) {
  panelMode.value = 'edit'
  editingUser.value = user
  formRole.value = user.role
  formActive.value = user.active
  saveError.value = ''
  panelOpen.value = true
  formFeatures.value = defaultFeatures(true)
  try {
    const detail = await fetchAdminUser(user.id)
    formFeatures.value = detail.enabledFeatures
  } catch {
    // keep the all-enabled fallback; save will still show current server state on reload
  }
}

function closePanel() {
  panelOpen.value = false
}

function toggleFeature(feature: Feature) {
  formFeatures.value = { ...formFeatures.value, [feature]: !formFeatures.value[feature] }
}

async function savePanel() {
  if (panelMode.value === 'create') {
    formNameTouched.value = true
    formEmailTouched.value = true
    if (!formName.value.trim() || !emailPattern.test(formEmail.value.trim())) {
      return
    }
  }

  saving.value = true
  saveError.value = ''
  try {
    if (panelMode.value === 'create') {
      await createAdminUser({
        fullName: formName.value.trim(),
        email: formEmail.value.trim(),
        role: formRole.value,
        enabledFeatures: ALL_FEATURES.filter((f) => formFeatures.value[f]),
      })
    } else if (editingUser.value) {
      await updateAdminUserAccess(editingUser.value.id, {
        role: formRole.value,
        active: formActive.value,
        enabledFeatures: ALL_FEATURES.filter((f) => formFeatures.value[f]),
      })
    }
    panelOpen.value = false
    await loadUsers()
  } catch (error) {
    saveError.value =
      isAxiosError<ApiErrorResponse>(error) && error.response?.data.message
        ? error.response.data.message
        : 'Something went wrong. Please try again.'
  } finally {
    saving.value = false
  }
}

// --- deactivate/activate/delete confirm ---
const confirmOpen = ref(false)
const confirmMode = ref<'deactivate' | 'activate' | 'delete'>('deactivate')
const confirmTarget = ref<UserResponse | null>(null)
const confirmError = ref('')
const confirming = ref(false)

const confirmTitle = computed(() => {
  if (confirmMode.value === 'delete') return 'Delete this user?'
  return confirmMode.value === 'deactivate' ? 'Deactivate this user?' : 'Activate this user?'
})
const confirmBody = computed(() => {
  const name = confirmTarget.value?.fullName ?? 'this user'
  if (confirmMode.value === 'delete') {
    return `This permanently removes ${name}'s account and all associated data. This action cannot be undone.`
  }
  return confirmMode.value === 'deactivate'
    ? 'The user will immediately lose access and be signed out.'
    : 'The user will regain access to sign in.'
})
const confirmActionLabel = computed(() => {
  if (confirmMode.value === 'delete') return 'Delete permanently'
  return confirmMode.value === 'deactivate' ? 'Deactivate' : 'Activate'
})

function openConfirm(user: UserResponse, mode: 'deactivate' | 'activate' | 'delete') {
  confirmTarget.value = user
  confirmMode.value = mode
  confirmError.value = ''
  confirmOpen.value = true
}

function closeConfirm() {
  confirmOpen.value = false
}

async function runConfirm() {
  if (!confirmTarget.value) return
  confirming.value = true
  confirmError.value = ''
  try {
    if (confirmMode.value === 'delete') {
      await deleteAdminUser(confirmTarget.value.id)
    } else {
      await updateAdminUserStatus(confirmTarget.value.id, confirmMode.value === 'activate')
    }
    confirmOpen.value = false
    await loadUsers()
  } catch (error) {
    confirmError.value =
      isAxiosError<ApiErrorResponse>(error) && error.response?.data.message
        ? error.response.data.message
        : 'Could not complete this action.'
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <AppLayout title="Admin · User Management" fab-label="Create user" @fab="openCreate">
    <div class="flex flex-col gap-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-surface-900">User Management</h1>
          <p class="text-sm text-surface-500 mt-1">Manage accounts, roles, and feature access</p>
        </div>
        <Button
          data-testid="create-user-button"
          label="Create user"
          icon="pi pi-plus"
          class="hidden md:inline-flex"
          @click="openCreate"
        />
      </div>

      <div class="flex items-center gap-3 flex-wrap">
        <span class="p-input-icon-left flex-1 min-w-[220px] max-w-xs">
          <InputText
            v-model="search"
            data-testid="user-search-input"
            class="w-full"
            placeholder="Search name or email"
          />
        </span>
        <Select
          v-model="roleFilter"
          data-testid="user-role-filter"
          :options="roleFilterOptions"
          option-label="label"
          option-value="value"
        />
        <Select
          v-model="statusFilter"
          data-testid="user-status-filter"
          :options="statusFilterOptions"
          option-label="label"
          option-value="value"
        />
      </div>

      <!-- error state -->
      <div
        v-if="loadState === 'error'"
        data-testid="admin-users-error-state"
        class="flex flex-col items-center justify-center gap-4 py-14 px-6 lg:py-16 lg:px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-red-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-red-600 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Couldn't load users</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">Something went wrong while fetching user accounts. Try again.</p>
        <Button data-testid="admin-users-retry-button" label="Retry" icon="pi pi-refresh" @click="loadUsers" />
      </div>

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="admin-users-loading-skeleton" class="bg-white border border-surface-200 rounded-lg overflow-hidden">
        <div v-for="n in 8" :key="n" class="flex items-center gap-4 px-5 py-3.5 border-b border-surface-100">
          <div class="w-8 h-8 rounded-lg bg-surface-200 animate-pulse shrink-0" />
          <div class="flex-1 h-3.5 rounded bg-surface-200 animate-pulse" />
          <div class="w-16 h-5 rounded-lg bg-surface-200 animate-pulse" />
          <div class="w-16 h-5 rounded-lg bg-surface-200 animate-pulse" />
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="bg-white border border-surface-200 rounded-lg overflow-hidden">
        <div
          v-if="users.length === 0"
          data-testid="admin-users-empty-state"
          class="flex flex-col items-center justify-center gap-3 py-14 px-6 lg:py-16 lg:px-8"
        >
          <p class="text-sm font-semibold text-surface-900">No users found</p>
          <p class="text-sm text-surface-500 text-center max-w-sm">No accounts match your filters, or none have been created yet.</p>
          <Button data-testid="admin-users-empty-create-button" label="Create user" size="small" @click="openCreate" />
        </div>

        <template v-else>
          <div class="hidden lg:grid grid-cols-[1fr_220px_100px_100px_120px_180px] px-5 py-3 border-b border-surface-200 bg-surface-50">
            <span class="text-xs font-semibold text-surface-500 uppercase">Name</span>
            <span class="text-xs font-semibold text-surface-500 uppercase">Email</span>
            <span class="text-xs font-semibold text-surface-500 uppercase">Role</span>
            <span class="text-xs font-semibold text-surface-500 uppercase">Status</span>
            <span class="text-xs font-semibold text-surface-500 uppercase">Joined</span>
            <span></span>
          </div>

          <div
            v-for="user in users"
            :key="user.id"
            :data-testid="`admin-user-row-${user.id}`"
            class="border-b border-surface-100 last:border-b-0"
          >
            <!-- desktop row -->
            <div class="hidden lg:grid grid-cols-[1fr_220px_100px_100px_120px_180px] items-center px-5 py-3.5">
              <div class="flex items-center gap-2.5 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-primary-50 text-primary-600 text-xs font-semibold flex items-center justify-center shrink-0">
                  {{ initials(user.fullName) }}
                </div>
                <span class="text-sm font-medium text-surface-900 truncate">{{ user.fullName }}</span>
              </div>
              <span class="text-sm text-surface-500 truncate pr-2">{{ user.email }}</span>
              <span>
                <span
                  class="text-xs font-medium px-2.5 py-1 rounded-lg"
                  :class="user.role === 'ADMIN' ? 'text-primary-600 bg-primary-50' : 'text-surface-600 bg-surface-100'"
                >
                  {{ user.role === 'ADMIN' ? 'Admin' : 'User' }}
                </span>
              </span>
              <span class="flex items-center gap-1.5">
                <span class="w-1.5 h-1.5 rounded-full" :class="user.active ? 'bg-green-700' : 'bg-surface-300'" />
                <span class="text-sm" :class="user.active ? 'text-green-700' : 'text-surface-400'">
                  {{ user.active ? 'Active' : 'Disabled' }}
                </span>
              </span>
              <span class="text-sm text-surface-500">{{ formatDate(user.createdAt) }}</span>
              <span class="flex items-center justify-end gap-1">
                <button
                  :data-testid="`admin-user-edit-${user.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-100"
                  @click="openEdit(user)"
                >
                  <i class="pi pi-pencil text-xs" />
                </button>
                <button
                  :data-testid="`admin-user-deactivate-${user.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-100"
                  @click="openConfirm(user, user.active ? 'deactivate' : 'activate')"
                >
                  <i :class="['pi text-xs', user.active ? 'pi-ban' : 'pi-check']" />
                </button>
                <button
                  :data-testid="`admin-user-delete-${user.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-red-50 hover:text-red-600"
                  @click="openConfirm(user, 'delete')"
                >
                  <i class="pi pi-trash text-xs" />
                </button>
              </span>
            </div>

            <!-- tablet/mobile card -->
            <div class="lg:hidden flex flex-col gap-2.5 px-4 py-3.5">
              <div class="flex items-center gap-2.5 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-primary-50 text-primary-600 text-xs font-semibold flex items-center justify-center shrink-0">
                  {{ initials(user.fullName) }}
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-surface-900 truncate">{{ user.fullName }}</p>
                  <p class="text-xs text-surface-400 truncate">{{ user.email }}</p>
                </div>
              </div>
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <span
                    class="text-xs font-medium px-2.5 py-1 rounded-lg"
                    :class="user.role === 'ADMIN' ? 'text-primary-600 bg-primary-50' : 'text-surface-600 bg-surface-100'"
                  >
                    {{ user.role === 'ADMIN' ? 'Admin' : 'User' }}
                  </span>
                  <span class="flex items-center gap-1.5">
                    <span class="w-1.5 h-1.5 rounded-full" :class="user.active ? 'bg-green-700' : 'bg-surface-300'" />
                    <span class="text-xs" :class="user.active ? 'text-green-700' : 'text-surface-400'">
                      {{ user.active ? 'Active' : 'Disabled' }}
                    </span>
                  </span>
                </div>
                <span class="text-xs text-surface-400 shrink-0">{{ formatDate(user.createdAt) }}</span>
              </div>
              <div class="flex items-center gap-1 border-t border-surface-100 pt-2">
                <button
                  :data-testid="`mobile-admin-user-edit-${user.id}`"
                  class="flex-1 h-9 rounded-md flex items-center justify-center gap-1.5 text-sm text-surface-600 hover:bg-surface-100"
                  @click="openEdit(user)"
                >
                  <i class="pi pi-pencil text-xs" /> Edit
                </button>
                <button
                  :data-testid="`mobile-admin-user-deactivate-${user.id}`"
                  class="flex-1 h-9 rounded-md flex items-center justify-center gap-1.5 text-sm text-surface-600 hover:bg-surface-100"
                  @click="openConfirm(user, user.active ? 'deactivate' : 'activate')"
                >
                  <i :class="['pi text-xs', user.active ? 'pi-ban' : 'pi-check']" />
                  {{ user.active ? 'Deactivate' : 'Activate' }}
                </button>
                <button
                  :data-testid="`mobile-admin-user-delete-${user.id}`"
                  class="flex-1 h-9 rounded-md flex items-center justify-center gap-1.5 text-sm text-surface-600 hover:bg-red-50 hover:text-red-600"
                  @click="openConfirm(user, 'delete')"
                >
                  <i class="pi pi-trash text-xs" /> Delete
                </button>
              </div>
            </div>
          </div>

          <div class="overflow-x-auto">
            <Paginator
              data-testid="admin-users-paginator"
              :rows="PAGE_SIZE"
              :total-records="totalRecords"
              :first="page * PAGE_SIZE"
              @page="onPageChange"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- create/edit drawer -->
    <Drawer
      v-model:visible="panelOpen"
      position="right"
      :style="{ width: '440px' }"
      :breakpoints="{ '768px': '92vw' }"
      data-testid="user-panel-drawer"
    >
      <template #header>
        <span class="text-base font-semibold text-surface-900">{{ panelMode === 'edit' ? 'Edit user' : 'Create user' }}</span>
      </template>

      <div class="flex flex-col gap-5">
        <FormError v-if="saveError" :message="saveError" testid="user-panel-error-banner" />

        <div v-if="panelMode === 'edit' && editingUser" class="flex items-center gap-3 pb-4 border-b border-surface-100">
          <div class="w-11 h-11 rounded-lg bg-primary-50 text-primary-600 text-sm font-semibold flex items-center justify-center">
            {{ initials(editingUser.fullName) }}
          </div>
          <div>
            <p class="text-sm font-semibold text-surface-900">{{ editingUser.fullName }}</p>
            <p class="text-sm text-surface-400">{{ editingUser.email }}</p>
          </div>
        </div>

        <template v-if="panelMode === 'create'">
          <div>
            <label for="user-name-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Full name</label>
            <InputText
              id="user-name-input"
              v-model="formName"
              data-testid="user-name-input"
              class="w-full"
              :invalid="!!nameError"
              @blur="formNameTouched = true"
            />
            <p v-if="nameError" class="text-xs text-red-600 mt-1.5">{{ nameError }}</p>
          </div>
          <div>
            <label for="user-email-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Email</label>
            <InputText
              id="user-email-input"
              v-model="formEmail"
              data-testid="user-email-input"
              class="w-full"
              placeholder="name@example.com"
              :invalid="!!emailError"
              @blur="formEmailTouched = true"
            />
            <p v-if="emailError" class="text-xs text-red-600 mt-1.5">{{ emailError }}</p>
          </div>
        </template>

        <div>
          <span class="block text-xs font-semibold text-surface-600 mb-1.5">Role</span>
          <div class="flex items-center gap-0.5 bg-surface-100 rounded-lg p-0.5 w-fit">
            <button
              data-testid="user-role-user-button"
              type="button"
              class="px-5 py-2 rounded-md text-sm font-semibold"
              :class="formRole === 'USER' ? 'bg-white text-surface-900 shadow-sm' : 'text-surface-500'"
              @click="formRole = 'USER'"
            >
              User
            </button>
            <button
              data-testid="user-role-admin-button"
              type="button"
              class="px-5 py-2 rounded-md text-sm font-semibold"
              :class="formRole === 'ADMIN' ? 'bg-white text-surface-900 shadow-sm' : 'text-surface-500'"
              @click="formRole = 'ADMIN'"
            >
              Admin
            </button>
          </div>
        </div>

        <div v-if="panelMode === 'edit'" class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold text-surface-900">Account active</p>
            <p class="text-xs text-surface-500 mt-0.5">Disabled users cannot sign in.</p>
          </div>
          <ToggleSwitch v-model="formActive" data-testid="user-active-toggle" />
        </div>

        <div>
          <p class="text-xs font-semibold text-surface-600 mb-2.5">Feature access</p>
          <div class="flex flex-col border border-surface-200 rounded-lg overflow-hidden">
            <div
              v-for="feature in ALL_FEATURES"
              :key="feature"
              class="flex items-center justify-between px-3.5 py-3 border-b border-surface-100 last:border-b-0"
            >
              <span class="text-sm font-medium text-surface-700">{{ FEATURE_LABELS[feature] }}</span>
              <ToggleSwitch
                :model-value="formFeatures[feature]"
                :data-testid="`user-feature-toggle-${feature}`"
                @update:model-value="toggleFeature(feature)"
              />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-3 w-full">
          <Button data-testid="user-panel-cancel-button" label="Cancel" severity="secondary" text @click="closePanel" />
          <Button
            data-testid="user-panel-save-button"
            :label="panelMode === 'edit' ? 'Save changes' : 'Create user'"
            :loading="saving"
            @click="savePanel"
          />
        </div>
      </template>
    </Drawer>

    <!-- deactivate/activate/delete confirm -->
    <Dialog
      v-model:visible="confirmOpen"
      modal
      :style="{ width: '400px' }"
      :breakpoints="{ '768px': '92vw' }"
      data-testid="user-confirm-dialog"
      :header="confirmTitle"
    >
      <FormError v-if="confirmError" :message="confirmError" testid="user-confirm-error-banner" />
      <p class="text-sm text-surface-500">{{ confirmBody }}</p>
      <template #footer>
        <Button data-testid="user-confirm-cancel-button" label="Cancel" severity="secondary" text @click="closeConfirm" />
        <Button
          data-testid="user-confirm-action-button"
          :label="confirmActionLabel"
          severity="danger"
          :loading="confirming"
          @click="runConfirm"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>
