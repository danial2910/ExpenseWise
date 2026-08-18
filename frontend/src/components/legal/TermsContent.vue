<script setup lang="ts">
// Shared Terms of Service copy — rendered inside a Dialog on the
// authenticated About page and inside a Dialog on the public landing page.
// Extracted so the two never drift apart; content only, no Dialog wrapper.
</script>

<template>
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
</template>
