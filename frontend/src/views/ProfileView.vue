<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import PasswordStrengthMeter from '../components/common/PasswordStrengthMeter.vue'
import ErrorState from '../components/common/ErrorState.vue'
import { useAuthStore } from '../stores/auth'
import {
  changePassword,
  fetchCurrentUser,
  fetchLoginHistory,
  logoutOtherSessions,
  removeAvatar,
  updateProfile,
  uploadAvatar,
} from '../api/users'
import type { ApiErrorResponse, UserResponse } from '../types/auth'
import type { LoginHistoryEntry } from '../types/user'

type LoadState = 'loading' | 'error' | 'ready'

const authStore = useAuthStore()

const loadState = ref<LoadState>('loading')
const activeTab = ref<'personal' | 'security'>('personal')

function extractErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.message) {
    return error.response.data.message
  }
  return fallback
}

// --- profile fields ---
const fullName = ref('')
const email = ref('')
const phone = ref('')
const dateOfBirth = ref('')
const gender = ref('')
const address = ref('')
const avatarUrl = ref<string | null>(null)

function applyUser(user: UserResponse) {
  fullName.value = user.fullName
  email.value = user.email
  phone.value = user.phone ?? ''
  dateOfBirth.value = user.dateOfBirth ?? ''
  gender.value = user.gender ?? ''
  address.value = user.address ?? ''
  avatarUrl.value = user.avatarUrl
}

const initials = computed(() =>
  fullName.value
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join(''),
)

async function load() {
  loadState.value = 'loading'
  try {
    const user = await fetchCurrentUser()
    authStore.user = user
    applyUser(user)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

const profileSubmitted = ref(false)
const profileSaving = ref(false)
const profileSuccess = ref(false)
const profileSaveError = ref('')
const profileFieldErrors = ref<Record<string, string>>({})

const fullNameError = computed(() => {
  if (profileSubmitted.value && !fullName.value.trim()) return 'Full name is required.'
  return profileFieldErrors.value.fullName ?? ''
})
const phoneError = computed(() => profileFieldErrors.value.phone ?? '')
const dobError = computed(() => profileFieldErrors.value.dateOfBirth ?? '')
const genderError = computed(() => profileFieldErrors.value.gender ?? '')
const addressError = computed(() => profileFieldErrors.value.address ?? '')

async function saveProfile() {
  profileSubmitted.value = true
  profileSuccess.value = false
  if (!fullName.value.trim()) return

  profileSaving.value = true
  profileSaveError.value = ''
  profileFieldErrors.value = {}
  try {
    const updated = await updateProfile({
      fullName: fullName.value.trim(),
      phone: phone.value,
      dateOfBirth: dateOfBirth.value || null,
      gender: gender.value,
      address: address.value,
    })
    authStore.user = updated
    applyUser(updated)
    profileSuccess.value = true
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.fieldErrors) {
      profileFieldErrors.value = error.response.data.fieldErrors
    } else {
      profileSaveError.value = extractErrorMessage(error, 'Something went wrong. Please try again.')
    }
  } finally {
    profileSaving.value = false
  }
}

// --- avatar ---
const avatarUploading = ref(false)
const avatarError = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerAvatarPicker() {
  fileInputRef.value?.click()
}

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  avatarUploading.value = true
  avatarError.value = ''
  try {
    const updated = await uploadAvatar(file)
    authStore.user = updated
    applyUser(updated)
  } catch (error) {
    avatarError.value = extractErrorMessage(error, 'Could not upload the photo. Please try again.')
  } finally {
    avatarUploading.value = false
  }
}

async function onRemoveAvatar() {
  avatarUploading.value = true
  avatarError.value = ''
  try {
    const updated = await removeAvatar()
    authStore.user = updated
    applyUser(updated)
  } catch (error) {
    avatarError.value = extractErrorMessage(error, 'Could not remove the photo. Please try again.')
  } finally {
    avatarUploading.value = false
  }
}

// --- change password ---
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordSubmitted = ref(false)
const passwordSaving = ref(false)
const passwordSuccess = ref(false)
const passwordError = ref('')

function meetsPasswordPolicy(pw: string): boolean {
  return pw.length >= 8 && /[A-Z]/.test(pw) && /[a-z]/.test(pw) && /[0-9]/.test(pw)
}

const currentPasswordFieldError = computed(() =>
  passwordSubmitted.value && !currentPassword.value ? 'Current password is required.' : '',
)
const newPasswordFieldError = computed(() =>
  passwordSubmitted.value && !meetsPasswordPolicy(newPassword.value)
    ? 'Password must be at least 8 characters and include a number.'
    : '',
)
const confirmPasswordFieldError = computed(() =>
  passwordSubmitted.value && newPassword.value !== confirmPassword.value ? 'Passwords do not match.' : '',
)

async function savePassword() {
  passwordSubmitted.value = true
  passwordSuccess.value = false
  if (currentPasswordFieldError.value || newPasswordFieldError.value || confirmPasswordFieldError.value) return

  passwordSaving.value = true
  passwordError.value = ''
  try {
    await changePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value })
    passwordSuccess.value = true
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    passwordSubmitted.value = false
  } catch (error) {
    passwordError.value = extractErrorMessage(error, 'Current password is incorrect.')
  } finally {
    passwordSaving.value = false
  }
}

// --- login history ---
const loginHistory = ref<LoginHistoryEntry[]>([])

async function loadLoginHistory() {
  try {
    const page = await fetchLoginHistory(0, 5)
    loginHistory.value = page.content
  } catch {
    loginHistory.value = []
  }
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-MY', { dateStyle: 'medium', timeStyle: 'short' })
}

// --- log out of all other sessions ---
const signOutConfirmOpen = ref(false)
const signOutSuccess = ref(false)
const signOutSaving = ref(false)
const signOutError = ref('')

function openSignOutConfirm() {
  signOutConfirmOpen.value = true
  signOutSuccess.value = false
}
function closeSignOutConfirm() {
  signOutConfirmOpen.value = false
}

async function confirmSignOut() {
  signOutSaving.value = true
  signOutError.value = ''
  try {
    await logoutOtherSessions()
    signOutConfirmOpen.value = false
    signOutSuccess.value = true
  } catch (error) {
    signOutError.value = extractErrorMessage(error, 'Could not sign out other sessions. Please try again.')
  } finally {
    signOutSaving.value = false
  }
}

onMounted(() => {
  load()
  loadLoginHistory()
})
</script>

<template>
  <AppLayout title="Profile">
    <div class="flex flex-col gap-6 max-w-3xl">
      <div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Profile</h1>
        <p class="text-sm text-surface-500 mt-1">Manage your personal information and account security</p>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="profile-error-state"
        retry-testid="profile-retry-button"
        title="Couldn't load your profile"
        description="Something went wrong. Check your connection and try again."
        class="bg-surface-0 border border-surface-200 rounded-xl"
        @retry="load"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="profile-loading-skeleton" class="flex flex-col gap-4">
        <div class="bg-surface-0 border border-surface-200 rounded-xl p-6 flex flex-col gap-4">
          <div class="w-18 h-18 rounded-lg bg-surface-200 animate-pulse" style="width: 72px; height: 72px" />
          <div class="w-36 h-4 rounded bg-surface-200 animate-pulse" />
          <div class="w-full h-11 rounded-lg bg-surface-200 animate-pulse" />
          <div class="w-full h-11 rounded-lg bg-surface-200 animate-pulse" />
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="flex flex-col gap-4">
        <div class="flex items-center gap-0.5 bg-surface-100 rounded-lg p-0.5 w-fit">
          <button
            data-testid="profile-tab-personal"
            class="px-5 py-2 rounded-md text-[13px] font-semibold transition-colors duration-fast ease-out-expo"
            :class="activeTab === 'personal' ? 'bg-surface-0 text-primary-300 shadow-soft-sm' : 'text-surface-500'"
            @click="activeTab = 'personal'"
          >
            Personal Information
          </button>
          <button
            data-testid="profile-tab-security"
            class="px-5 py-2 rounded-md text-[13px] font-semibold transition-colors duration-fast ease-out-expo"
            :class="activeTab === 'security' ? 'bg-surface-0 text-primary-300 shadow-soft-sm' : 'text-surface-500'"
            @click="activeTab = 'security'"
          >
            Security
          </button>
        </div>

        <!-- PERSONAL INFORMATION -->
        <div v-if="activeTab === 'personal'" class="bg-surface-0 border border-surface-200 rounded-xl p-6">
          <Transition name="field-in">
            <div v-if="profileSuccess" class="bg-success-bg border border-success/30 text-success text-[13px] rounded-lg px-3 py-2.5 mb-4">
              Profile updated.
            </div>
          </Transition>
          <FormError v-if="profileSaveError" :message="profileSaveError" testid="profile-save-error-banner" />
          <FormError v-if="avatarError" :message="avatarError" testid="avatar-error-banner" />

          <div class="flex items-center gap-4 mb-6 pb-6 border-b border-surface-100">
            <img
              v-if="avatarUrl"
              :src="avatarUrl"
              alt="Profile photo"
              data-testid="profile-avatar-image"
              class="w-16 h-16 rounded-lg object-cover shrink-0 bg-surface-100"
            />
            <div
              v-else
              data-testid="profile-avatar-placeholder"
              class="w-16 h-16 rounded-lg bg-primary-50 text-primary-300 text-xl font-semibold flex items-center justify-center shrink-0"
            >
              <span v-if="initials">{{ initials }}</span>
              <i v-else class="pi pi-user text-surface-400 text-2xl" />
            </div>
            <div class="flex flex-col gap-2">
              <div class="flex items-center gap-2.5">
                <button
                  data-testid="avatar-upload-button"
                  class="min-h-9 flex items-center px-3.5 border border-surface-200 rounded-lg text-[13px] font-semibold text-surface-700 hover:border-primary-300 hover:text-primary-300 disabled:opacity-60 transition-colors duration-fast ease-out-expo"
                  :disabled="avatarUploading"
                  @click="triggerAvatarPicker"
                >
                  <i v-if="avatarUploading" class="pi pi-spin pi-spinner mr-1.5 text-xs" />
                  {{ avatarUploading ? 'Uploading…' : avatarUrl ? 'Change photo' : 'Upload photo' }}
                </button>
                <button
                  v-if="avatarUrl"
                  data-testid="avatar-remove-button"
                  class="min-h-9 flex items-center px-3.5 text-[13px] font-semibold text-danger hover:text-danger/80 disabled:opacity-60 transition-colors duration-fast ease-out-expo"
                  :disabled="avatarUploading"
                  @click="onRemoveAvatar"
                >
                  Remove
                </button>
                <input
                  ref="fileInputRef"
                  data-testid="avatar-upload-input"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  class="hidden"
                  @change="onAvatarSelected"
                />
              </div>
              <div class="text-xs text-surface-400">JPG, PNG, or WEBP — up to 2 MB.</div>
            </div>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
            <div>
              <label for="profile-fullname-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Full name</label>
              <input
                id="profile-fullname-input"
                v-model="fullName"
                data-testid="profile-fullname-input"
                type="text"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border bg-surface-0"
                :class="fullNameError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="fullNameError" class="text-xs text-danger mt-1.5">{{ fullNameError }}</p>
            </div>
            <div>
              <div class="flex items-center gap-2 mb-1.5">
                <label for="profile-email-input" class="text-xs font-semibold text-surface-600">Email</label>
                <span class="text-[10px] font-semibold text-surface-400 bg-surface-100 px-1.5 py-0.5 rounded-lg">Read-only</span>
              </div>
              <input
                id="profile-email-input"
                data-testid="profile-email-input"
                type="email"
                :value="email"
                disabled
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border border-surface-200 bg-surface-50 text-surface-400"
              />
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
            <div>
              <label for="profile-phone-input" class="block text-xs font-semibold text-surface-600 mb-1.5">
                Phone number <span class="text-surface-400 font-normal">(optional)</span>
              </label>
              <input
                id="profile-phone-input"
                v-model="phone"
                data-testid="profile-phone-input"
                type="tel"
                placeholder="+60 12-345 6789"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border"
                :class="phoneError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="phoneError" class="text-xs text-danger mt-1.5">{{ phoneError }}</p>
            </div>
            <div>
              <label for="profile-dob-input" class="block text-xs font-semibold text-surface-600 mb-1.5">
                Date of birth <span class="text-surface-400 font-normal">(optional)</span>
              </label>
              <input
                id="profile-dob-input"
                v-model="dateOfBirth"
                data-testid="profile-dob-input"
                type="date"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border"
                :class="dobError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="dobError" class="text-xs text-danger mt-1.5">{{ dobError }}</p>
            </div>
          </div>

          <div class="mb-4">
            <label for="profile-gender-select" class="block text-xs font-semibold text-surface-600 mb-1.5">
              Gender <span class="text-surface-400 font-normal">(optional)</span>
            </label>
            <select
              id="profile-gender-select"
              v-model="gender"
              data-testid="profile-gender-select"
              class="w-full min-h-11 px-3 rounded-lg text-sm border bg-surface-0"
              :class="genderError ? 'border-danger' : 'border-surface-200'"
            >
              <option value="">Select…</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="NON_BINARY">Non-binary</option>
              <option value="SELF_DESCRIBED">Prefer to self-describe</option>
              <option value="NOT_SPECIFIED">Prefer not to say</option>
            </select>
            <p v-if="genderError" class="text-xs text-danger mt-1.5">{{ genderError }}</p>
          </div>

          <div class="mb-5">
            <label for="profile-address-textarea" class="block text-xs font-semibold text-surface-600 mb-1.5">
              Home address <span class="text-surface-400 font-normal">(optional)</span>
            </label>
            <textarea
              id="profile-address-textarea"
              v-model="address"
              data-testid="profile-address-textarea"
              rows="3"
              placeholder="Street, city, postcode, state"
              class="w-full px-3 py-2 rounded-lg text-sm border resize-none"
              :class="addressError ? 'border-danger' : 'border-surface-200'"
            />
            <p v-if="addressError" class="text-xs text-danger mt-1.5">{{ addressError }}</p>
          </div>

          <Button
            data-testid="profile-save-button"
            label="Save changes"
            :loading="profileSaving"
            class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
            @click="saveProfile"
          />
        </div>

        <!-- SECURITY -->
        <div v-else class="flex flex-col gap-4">
          <div class="bg-surface-0 border border-surface-200 rounded-xl p-6">
            <p class="text-sm font-semibold text-surface-900 mb-1.5">Change password</p>
            <p class="text-[13px] text-surface-500 mb-5">Changing your password will sign you out of all other sessions.</p>

            <Transition name="field-in">
              <div v-if="passwordSuccess" class="bg-success-bg border border-success/30 text-success text-[13px] rounded-lg px-3 py-2.5 mb-4">
                Password changed. Other sessions have been signed out.
              </div>
            </Transition>
            <FormError v-if="passwordError" :message="passwordError" testid="change-password-error-banner" />

            <div class="mb-4">
              <label for="change-password-current-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Current password</label>
              <input
                id="change-password-current-input"
                v-model="currentPassword"
                data-testid="change-password-current-input"
                type="password"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border"
                :class="currentPasswordFieldError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="currentPasswordFieldError" class="text-xs text-danger mt-1.5">{{ currentPasswordFieldError }}</p>
            </div>

            <div class="mb-2">
              <label for="change-password-new-input" class="block text-xs font-semibold text-surface-600 mb-1.5">New password</label>
              <input
                id="change-password-new-input"
                v-model="newPassword"
                data-testid="change-password-new-input"
                type="password"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border"
                :class="newPasswordFieldError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="newPasswordFieldError" class="text-xs text-danger mt-1.5">{{ newPasswordFieldError }}</p>
            </div>
            <PasswordStrengthMeter v-if="newPassword" :password="newPassword" />

            <div class="mb-5 mt-4">
              <label for="change-password-confirm-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Confirm new password</label>
              <input
                id="change-password-confirm-input"
                v-model="confirmPassword"
                data-testid="change-password-confirm-input"
                type="password"
                class="w-full min-h-11 px-3 py-2 rounded-lg text-sm border"
                :class="confirmPasswordFieldError ? 'border-danger' : 'border-surface-200'"
              />
              <p v-if="confirmPasswordFieldError" class="text-xs text-danger mt-1.5">{{ confirmPasswordFieldError }}</p>
            </div>

            <Button
              data-testid="change-password-submit-button"
              label="Update password"
              :loading="passwordSaving"
              class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
              @click="savePassword"
            />
          </div>

          <div class="bg-surface-0 border border-surface-200 rounded-xl p-6">
            <p class="text-sm font-semibold text-surface-900 mb-4">Login history</p>
            <div v-if="loginHistory.length === 0" data-testid="login-history-empty" class="py-4 text-sm text-surface-400">
              No login activity yet.
            </div>
            <template v-else>
              <!-- desktop/tablet table -->
              <div class="hidden md:grid grid-cols-[1fr_160px_100px] pb-2.5 border-b border-surface-200">
                <span class="text-[11px] font-semibold text-surface-500 uppercase">Date &amp; time</span>
                <span class="text-[11px] font-semibold text-surface-500 uppercase">IP address</span>
                <span class="text-[11px] font-semibold text-surface-500 uppercase text-right">Status</span>
              </div>
              <div
                v-for="(entry, index) in loginHistory"
                :key="index"
              >
                <!-- desktop/tablet row -->
                <div
                  data-testid="login-history-row"
                  class="hidden md:grid grid-cols-[1fr_160px_100px] py-3 border-b border-surface-100 items-center"
                >
                  <span class="text-[13px] text-surface-700">{{ formatDateTime(entry.occurredAt) }}</span>
                  <span class="text-[13px] text-surface-500 tabular-nums">{{ entry.ipAddress ?? '—' }}</span>
                  <span
                    class="text-xs font-medium text-right"
                    :class="entry.status === 'Success' ? 'text-success' : 'text-danger'"
                  >
                    {{ entry.status }}
                  </span>
                </div>
                <!-- mobile card -->
                <div
                  data-testid="login-history-row-mobile"
                  class="md:hidden flex flex-col gap-0.5 py-2.5 border-b border-surface-100"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-[13px] text-surface-700">{{ formatDateTime(entry.occurredAt) }}</span>
                    <span
                      class="text-xs font-medium shrink-0"
                      :class="entry.status === 'Success' ? 'text-success' : 'text-danger'"
                    >
                      {{ entry.status }}
                    </span>
                  </div>
                  <span class="text-xs text-surface-400 tabular-nums">{{ entry.ipAddress ?? '—' }}</span>
                </div>
              </div>
            </template>
          </div>

          <div class="bg-surface-0 border border-surface-200 rounded-xl p-6">
            <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-surface-900">Active sessions</p>
                <p class="text-[13px] text-surface-500 mt-1">Sign out everywhere except this device.</p>
              </div>
              <button
                v-if="!signOutConfirmOpen"
                data-testid="logout-others-button"
                class="min-h-11 flex items-center justify-center px-4 border border-danger/30 text-danger text-[13px] font-semibold rounded-lg hover:bg-danger-bg transition-colors duration-fast ease-out-expo"
                @click="openSignOutConfirm"
              >
                Log out of all other sessions
              </button>
            </div>

            <FormError v-if="signOutError" :message="signOutError" testid="logout-others-error-banner" />

            <div
              v-if="signOutConfirmOpen"
              data-testid="logout-others-confirm-panel"
              class="mt-4 p-4 bg-danger-bg border border-danger/30 rounded-lg flex flex-col gap-3"
            >
              <p class="text-[13px] text-surface-800">This will immediately sign out every other device using your account. Continue?</p>
              <div class="flex items-center gap-3">
                <button
                  data-testid="logout-others-confirm-button"
                  class="min-h-9 flex-1 md:flex-none flex items-center justify-center px-3.5 bg-danger text-surface-100 text-[13px] font-semibold rounded-lg disabled:opacity-60 active:scale-[0.98] transition-transform duration-fast ease-out-expo"
                  :disabled="signOutSaving"
                  @click="confirmSignOut"
                >
                  Yes, sign out others
                </button>
                <button
                  data-testid="logout-others-cancel-button"
                  class="min-h-9 flex-1 md:flex-none flex items-center justify-center px-3.5 text-surface-600 text-[13px] font-semibold"
                  @click="closeSignOutConfirm"
                >
                  Cancel
                </button>
              </div>
            </div>
            <div
              v-if="signOutSuccess"
              data-testid="logout-others-success-banner"
              class="mt-4 bg-success-bg border border-success/30 text-success text-[13px] rounded-lg px-3 py-2.5"
            >
              All other sessions have been signed out.
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
