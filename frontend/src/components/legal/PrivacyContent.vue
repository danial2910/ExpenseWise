<script setup lang="ts">
// Shared Privacy Policy copy — see TermsContent.vue for why this is
// extracted (rendered on both the authenticated About page and the public
// landing page footer).
</script>

<template>
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
      <p>For privacy questions, contact <a href="mailto:dsyafiq36@gmail.com" class="text-primary-300 hover:text-primary-200 font-semibold transition-colors duration-fast ease-out-expo">dsyafiq36@gmail.com</a>.</p>
    </section>
  </div>
</template>
