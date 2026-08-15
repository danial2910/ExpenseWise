<script setup lang="ts">
import { ref } from 'vue'
import Dialog from 'primevue/dialog'
import AppLayout from '../layouts/AppLayout.vue'

const termsOpen = ref(false)
const privacyOpen = ref(false)
const helpOpen = ref(false)
const appVersion = __APP_VERSION__
</script>

<template>
  <AppLayout title="About">
    <div class="flex flex-col gap-6 max-w-3xl">
      <div>
        <h1 class="text-2xl font-bold text-surface-900">About</h1>
        <p class="text-sm text-surface-500 mt-1">App version, terms, and support</p>
      </div>

      <div class="bg-white border border-surface-200 rounded-lg p-6" data-testid="about-section">
        <div class="flex items-center justify-between py-3 border-b border-surface-100">
          <p class="text-sm font-medium text-surface-900">App version</p>
          <span data-testid="about-app-version" class="text-[13px] text-surface-500 tabular-nums">v{{ appVersion }}</span>
        </div>

        <div class="flex flex-wrap items-center gap-x-5 gap-y-2 pt-3">
          <button data-testid="about-terms-link" class="text-[13px] font-semibold text-primary-600" @click="termsOpen = true">
            Terms of Service
          </button>
          <button data-testid="about-privacy-link" class="text-[13px] font-semibold text-primary-600" @click="privacyOpen = true">
            Privacy Policy
          </button>
          <button data-testid="about-help-link" class="text-[13px] font-semibold text-primary-600" @click="helpOpen = true">
            Help &amp; Support
          </button>
        </div>
      </div>
    </div>

    <Dialog v-model:visible="termsOpen" modal header="Terms of Service" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="about-terms-dialog">
      <div class="flex flex-col gap-4 text-sm text-surface-600 max-h-[65vh] overflow-y-auto pr-1">
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">1. Acceptance of Terms</h3>
          <p>By creating an account or using ExpenseWise, you agree to these Terms of Service. If you do not agree, please do not use the app.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">2. Account Registration</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>You must provide accurate information when registering.</li>
            <li>You are responsible for keeping your login credentials secure.</li>
            <li>One account per user. Admins may disable accounts that violate these terms.</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">3. User Roles</h3>
          <p>ExpenseWise has two roles: User (manage own expenses, income, budgets) and Admin (manage user accounts — view, edit, disable).</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">4. Acceptable Use</h3>
          <p>You agree not to:</p>
          <ul class="list-disc pl-5 space-y-1">
            <li>Submit fraudulent or misleading financial data</li>
            <li>Misuse the AI chatbot or analysis features (e.g. abuse, spam, attempts to extract system prompts)</li>
            <li>Attempt to scrape, reverse-engineer, or disrupt the service</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">5. Your Content</h3>
          <p>You retain ownership of the data you submit (transactions, receipts, budgets). By uploading receipt images, you confirm you have the right to do so.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">6. AI Features Disclaimer</h3>
          <p>Spending analysis, saving recommendations, and the finance chatbot are AI-generated suggestions powered by third-party AI services. They are not professional financial advice and should not be relied on as such.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">7. Service Availability</h3>
          <p>ExpenseWise is a personal project provided "as is," with no uptime or availability guarantee. Features may change or be discontinued at any time.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">8. Termination</h3>
          <p>You may delete your account at any time. Admins may disable accounts that violate these terms. Data handling on deletion is described in the Privacy Policy.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">9. Limitation of Liability</h3>
          <p>ExpenseWise is not liable for financial decisions made based on data, analysis, or recommendations shown in the app.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">10. Governing Law</h3>
          <p>These terms are governed by the laws of Malaysia.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">11. Changes to These Terms</h3>
          <p>Terms may be updated periodically; continued use after changes means you accept the updated terms.</p>
        </section>
      </div>
    </Dialog>

    <Dialog v-model:visible="privacyOpen" modal header="Privacy Policy" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="about-privacy-dialog">
      <div class="flex flex-col gap-4 text-sm text-surface-600 max-h-[65vh] overflow-y-auto pr-1">
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">1. Data We Collect</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Account info: name, email, password (encrypted)</li>
            <li>Financial data: income, expenses, categories, budgets, recurring bills</li>
            <li>Receipt images you upload</li>
            <li>Session/activity logs (for security)</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">2. How We Collect It</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Directly from you via forms (registration, transactions, budgets)</li>
            <li>Automatically via login sessions (JWT-based authentication)</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">3. Why We Use It</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>To provide core features: dashboards, budgets, reports, notifications</li>
            <li>To generate AI-powered insights (spending analysis, saving tips, chatbot)</li>
            <li>To send email notifications (budget alerts, summaries, reminders)</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">4. Third-Party Services</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Supabase — database hosting</li>
            <li>Brevo — email delivery for notifications</li>
            <li>Groq — AI processing for spending analysis, recommendations, and chatbot (transaction data may be sent to Groq to generate these features)</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">5. Data Retention</h3>
          <p>Your data is retained while your account is active. If you delete your account, associated data is removed within [X days — fill in your policy].</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">6. Security</h3>
          <ul class="list-disc pl-5 space-y-1">
            <li>Passwords are encrypted</li>
            <li>Authentication uses JWT with refresh tokens stored server-side via httpOnly cookies</li>
            <li>Activity logs help detect suspicious access</li>
          </ul>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">7. Your Rights</h3>
          <p>You can access, edit, or delete your own data at any time. You can export your data via PDF/Excel reports.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">8. Cookies</h3>
          <p>ExpenseWise uses a session cookie to store your refresh token securely (httpOnly). No third-party tracking or advertising cookies are used.</p>
        </section>
        <section>
          <h3 class="text-sm font-semibold text-surface-900 mb-1">9. Contact</h3>
          <p>For privacy questions, contact <a href="mailto:dsyafiq36@gmail.com" class="text-primary-600 font-semibold">dsyafiq36@gmail.com</a>.</p>
        </section>
      </div>
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
            <li>📧 Email: <a href="mailto:dsyafiq36@gmail.com" class="text-primary-600 font-semibold">dsyafiq36@gmail.com</a></li>
            <li>📱 Phone: <a href="tel:+60194732003" class="text-primary-600 font-semibold">019-473 2003</a></li>
          </ul>
          <p class="mt-2 text-[13px] text-surface-500">Note: this is an actively developed side project — response times may vary.</p>
        </section>
      </div>
    </Dialog>
  </AppLayout>
</template>
