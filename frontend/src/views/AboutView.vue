<script setup lang="ts">
import { ref } from 'vue'
import Dialog from 'primevue/dialog'
import AppLayout from '../layouts/AppLayout.vue'
import TermsContent from '../components/legal/TermsContent.vue'
import PrivacyContent from '../components/legal/PrivacyContent.vue'

const termsOpen = ref(false)
const privacyOpen = ref(false)
const helpOpen = ref(false)
const appVersion = __APP_VERSION__
</script>

<template>
  <AppLayout title="About">
    <div class="flex flex-col gap-6 max-w-3xl">
      <div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">About</h1>
        <p class="text-sm text-surface-500 mt-1">App version, terms, and support</p>
      </div>

      <div class="bg-surface-0 border border-surface-300 rounded-xl p-6" data-testid="about-section">
        <div class="flex items-center justify-between py-3 border-b border-surface-100">
          <p class="text-sm font-medium text-surface-900">App version</p>
          <span data-testid="about-app-version" class="text-[13px] text-surface-500 tabular-nums">v{{ appVersion }}</span>
        </div>

        <div class="flex flex-wrap items-center gap-x-5 gap-y-2 pt-3">
          <button data-testid="about-terms-link" class="text-[13px] font-semibold text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo" @click="termsOpen = true">
            Terms of Service
          </button>
          <button data-testid="about-privacy-link" class="text-[13px] font-semibold text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo" @click="privacyOpen = true">
            Privacy Policy
          </button>
          <button data-testid="about-help-link" class="text-[13px] font-semibold text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo" @click="helpOpen = true">
            Help &amp; Support
          </button>
        </div>
      </div>
    </div>

    <Dialog v-model:visible="termsOpen" modal header="Terms of Service" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="about-terms-dialog">
      <TermsContent />
    </Dialog>

    <Dialog v-model:visible="privacyOpen" modal header="Privacy Policy" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="about-privacy-dialog">
      <PrivacyContent />
    </Dialog>

    <Dialog v-model:visible="helpOpen" modal header="Help &amp; Support" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="about-help-dialog">
      <div class="flex flex-col gap-4 text-sm text-surface-600 max-h-[65vh] overflow-y-auto pr-1">
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">Getting Started</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>How to register and log in</li>
            <li>How to set up your first budget and category</li>
            <li>How to add income and expense entries</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">FAQ</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>How are recurring expenses tracked?</li>
            <li>How is budget utilisation % calculated?</li>
            <li>Does the AI chatbot see my transaction data?</li>
            <li>How do I export a report?</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">Account &amp; Security</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Resetting your password</li>
            <li>Managing your session/login</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">Reports &amp; Exports</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Generating PDF/Excel reports</li>
            <li>Printing summaries</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">Notifications</h3>
          <p>Not receiving budget alerts or reminders? Check spam or notification settings.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">Contact Us</h3>
          <p>Have a question, found a bug, or want to give feedback?</p>
          <ul class="list-none space-y-1 mt-1">
            <li class="flex items-center gap-2"><i class="pi pi-envelope text-surface-400 text-xs" /> Email: <a href="mailto:dsyafiq36@gmail.com" class="text-primary-300 hover:text-primary-200 font-semibold transition-colors duration-fast ease-out-expo">dsyafiq36@gmail.com</a></li>
            <li class="flex items-center gap-2"><i class="pi pi-phone text-surface-400 text-xs" /> Phone: <a href="tel:+60194732003" class="text-primary-300 hover:text-primary-200 font-semibold transition-colors duration-fast ease-out-expo">019-473 2003</a></li>
          </ul>
          <p class="mt-2 text-[13px] text-surface-500">Note: this is an actively developed side project — response times may vary.</p>
        </section>
      </div>
    </Dialog>
  </AppLayout>
</template>
