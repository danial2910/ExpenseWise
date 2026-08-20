<script setup lang="ts">
import { ref } from 'vue'
import Dialog from 'primevue/dialog'
import TermsContent from '../components/legal/TermsContent.vue'
import PrivacyContent from '../components/legal/PrivacyContent.vue'
import logoIcon from '../assets/logo-icon.png'

const mobileMenuOpen = ref(false)
const termsOpen = ref(false)
const privacyOpen = ref(false)

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

// Static, illustrative copy only — this card mirrors the real Dashboard's
// visual language (see DashboardView.vue) but is never wired to the
// dashboard API. No <MoneyDisplay> here on purpose: that component exists
// to format real fetched amounts, and these are fixed marketing strings,
// not data.
const previewKpis = [
  { label: 'Total Balance', value: 'RM 12,480.50', icon: 'pi-wallet', tone: 'text-surface-900' },
  { label: 'Income', value: 'RM 4,200.00', icon: 'pi-arrow-up', tone: 'text-success' },
  { label: 'Expense', value: 'RM 2,150.75', icon: 'pi-arrow-down', tone: 'text-danger' },
  { label: 'Budget Remaining', value: 'RM 849.25', icon: 'pi-shield', tone: 'text-surface-900' },
]

const previewActivity = [
  { name: 'Payday', category: 'Salary', amount: '+ RM 4,200.00', tone: 'text-success', date: '01 Aug' },
  { name: 'Groceries run', category: 'Food', amount: '− RM 86.40', tone: 'text-danger', date: '03 Aug' },
  { name: 'Spotify', category: 'Bills', amount: '− RM 17.50', tone: 'text-danger', date: '05 Aug' },
]

const features = [
  { icon: 'pi-list', title: 'Track income & expenses', description: 'Log every transaction in one unified view, filter and search in seconds.' },
  { icon: 'pi-wallet', title: 'Budgets', description: 'Set overall or per-category limits and watch spending track against them in real time.' },
  { icon: 'pi-sync', title: 'Recurring transactions', description: 'Rent, salary, subscriptions, set them up once and let ExpenseWise post them on schedule.' },
  { icon: 'pi-sparkles', title: 'AI assistant', description: 'Ask questions about your spending and get answers grounded in your own transaction data.' },
  { icon: 'pi-file-export', title: 'Reports & exports', description: 'Generate monthly or yearly reports and export them as PDF or Excel in one click.' },
  { icon: 'pi-globe', title: 'Financial news', description: 'Curated headlines alongside your own numbers, so context is always one tab away.' },
]

const steps = [
  { number: '01', title: 'Create your account', description: 'Sign up in under a minute no credit card, no setup calls.' },
  { number: '02', title: 'Add your transactions', description: 'Log income and expenses, or set up recurring rules for the ones that repeat.' },
  { number: '03', title: 'See it come together', description: 'Budgets, reports, and the AI assistant all draw from that one place.' },
]
</script>

<template>
  <div class="min-h-screen bg-surface-100">
    <!-- nav -->
    <header class="aurora-backdrop-subtle sticky top-0 z-50 bg-surface-100/75 backdrop-blur-xl border-b border-surface-200">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <a href="#" class="flex items-center gap-2.5">
          <img :src="logoIcon" alt="ExpenseWise" class="w-8 h-8 object-contain" />
          <span class="font-display text-base font-semibold tracking-[0.08em] uppercase text-surface-900">ExpenseWise</span>
        </a>

        <nav class="hidden md:flex items-center gap-8">
          <a href="#features" class="text-sm font-medium text-surface-500 hover:text-surface-900 transition-colors duration-fast ease-out-expo">Features</a>
          <a href="#how-it-works" class="text-sm font-medium text-surface-500 hover:text-surface-900 transition-colors duration-fast ease-out-expo">How it works</a>
          <a href="#preview" class="text-sm font-medium text-surface-500 hover:text-surface-900 transition-colors duration-fast ease-out-expo">Preview</a>
        </nav>

        <div class="hidden md:flex items-center gap-5">
          <router-link data-testid="landing-nav-signin-link" to="/login" class="text-sm font-semibold text-surface-600 hover:text-surface-900 transition-colors duration-fast ease-out-expo">
            Sign in
          </router-link>
          <router-link
            data-testid="landing-nav-get-started-button"
            to="/register"
            class="text-sm font-semibold text-primary-300 border border-primary-300/50 rounded-full px-4 py-2 shadow-glow-accent hover:text-primary-200 hover:border-primary-200/60 active:scale-[0.97] transition-all duration-fast ease-out-expo"
          >
            Get Started
          </router-link>
        </div>

        <button
          data-testid="landing-mobile-menu-button"
          type="button"
          aria-label="Open menu"
          class="md:hidden w-11 h-11 flex items-center justify-center text-surface-600"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <i :class="['pi', mobileMenuOpen ? 'pi-times' : 'pi-bars']" />
        </button>
      </div>

      <Transition name="field-in">
        <div v-if="mobileMenuOpen" class="md:hidden border-t border-surface-200 px-4 py-4 flex flex-col gap-1 bg-surface-100/95 backdrop-blur-xl">
          <a href="#features" class="min-h-11 flex items-center text-sm font-medium text-surface-600" @click="closeMobileMenu">Features</a>
          <a href="#how-it-works" class="min-h-11 flex items-center text-sm font-medium text-surface-600" @click="closeMobileMenu">How it works</a>
          <a href="#preview" class="min-h-11 flex items-center text-sm font-medium text-surface-600" @click="closeMobileMenu">Preview</a>
          <div class="flex items-center gap-3 pt-2 mt-2 border-t border-surface-200">
            <router-link data-testid="landing-mobile-signin-link" to="/login" class="flex-1 min-h-11 flex items-center justify-center text-sm font-semibold text-surface-700 border border-surface-300 rounded-lg">
              Sign in
            </router-link>
            <router-link data-testid="landing-mobile-get-started-button" to="/register" class="flex-1 min-h-11 flex items-center justify-center text-sm font-semibold text-surface-100 bg-primary-500 rounded-lg">
              Get Started
            </router-link>
          </div>
        </div>
      </Transition>
    </header>

    <main>
      <!-- hero -->
      <section class="aurora-backdrop relative px-4 sm:px-6 lg:px-8 pt-20 pb-24 md:pt-28 md:pb-32 text-center">
        <div class="max-w-3xl mx-auto flex flex-col items-center gap-6">
          <span class="inline-flex items-center gap-2 rounded-full border border-surface-300 bg-surface-50 px-3.5 py-1.5 text-xs font-medium text-surface-600">
            <span class="w-1.5 h-1.5 rounded-full bg-primary-500" />
            Personal finance, built to stay out of your way
          </span>

          <h1 class="font-display text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-normal tracking-tight leading-[1.05] text-surface-900">
            Take control of your money.
            <br />
            One dashboard for everything.
          </h1>

          <p class="text-base md:text-lg text-surface-500 max-w-xl leading-relaxed">
            Track income and expenses, set budgets that actually hold, and let recurring
            bills post themselves all in one calm, private place.
          </p>

          <div class="flex flex-col sm:flex-row items-center gap-4 mt-2">
            <router-link
              data-testid="landing-hero-get-started-button"
              to="/register"
              class="cta-pill inline-flex items-center gap-2 text-sm font-semibold px-6 py-3.5 rounded-full active:scale-[0.98]"
            >
              Get Started
              <i class="pi pi-arrow-right text-xs" />
            </router-link>
            <a href="#how-it-works" class="text-sm font-semibold text-surface-600 hover:text-surface-900 transition-colors duration-fast ease-out-expo">
              See how it works
            </a>
          </div>
        </div>
      </section>

      <!-- how it works -->
      <section id="how-it-works" class="px-4 sm:px-6 lg:px-8 py-20">
        <div class="max-w-5xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-8">
          <div v-for="step in steps" :key="step.number" v-reveal class="flex flex-col gap-2">
            <span class="font-display text-sm font-semibold text-primary-400 tabular-nums">{{ step.number }}</span>
            <h3 class="font-display text-lg font-semibold text-surface-900">{{ step.title }}</h3>
            <p class="text-sm text-surface-500 leading-relaxed">{{ step.description }}</p>
          </div>
        </div>
      </section>

      <!-- features -->
      <section id="features" class="px-4 sm:px-6 lg:px-8 py-20 bg-surface-0/40 border-y border-surface-200">
        <div class="max-w-5xl mx-auto flex flex-col gap-12">
          <div v-reveal class="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-6">
            <div class="flex flex-col gap-3 max-w-xl">
              <span class="font-mono text-xs font-semibold text-primary-400 tracking-wide">// FEATURES</span>
              <h2 class="font-display text-3xl md:text-4xl font-normal tracking-tight leading-tight">
                <span class="text-surface-900">Everything you need to</span>
                <br />
                <span class="text-primary-400">manage your money.</span>
              </h2>
              <p class="text-sm md:text-base text-surface-500 leading-relaxed">
                No spreadsheets, no guesswork  just the tools a personal budget actually needs.
              </p>
            </div>
            <a
              href="#preview"
              class="shrink-0 inline-flex items-center gap-2 text-sm font-semibold text-surface-700 border border-surface-300 rounded-full px-5 py-2.5 hover:border-primary-300 hover:text-primary-300 transition-colors duration-fast ease-out-expo w-fit"
            >
              See it in action
              <i class="pi pi-arrow-right text-xs" />
            </a>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            <div
              v-for="feature in features"
              :key="feature.title"
              v-reveal
              class="bg-surface-0 border border-surface-300 rounded-xl p-6 flex flex-col gap-3 hover:border-primary-300/50 transition-colors duration-fast ease-out-expo"
            >
              <div class="w-10 h-10 rounded-lg bg-primary-50 text-primary-300 flex items-center justify-center">
                <i :class="['pi', feature.icon]" />
              </div>
              <h3 class="font-display text-base font-semibold text-surface-900">{{ feature.title }}</h3>
              <p class="text-sm text-surface-500 leading-relaxed">{{ feature.description }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- dashboard preview -->
      <section id="preview" class="px-4 sm:px-6 lg:px-8 py-20">
        <div class="max-w-5xl mx-auto flex flex-col gap-10">
          <div v-reveal class="flex flex-col items-center text-center gap-3">
            <h2 class="font-display text-3xl md:text-4xl font-normal tracking-tight text-surface-900">See your money, clearly.</h2>
            <p class="text-sm md:text-base text-surface-500 max-w-lg leading-relaxed">
              A live preview of the dashboard waiting for you after sign-up. The numbers
              below are illustrative and yours will be real.
            </p>
          </div>

          <div v-reveal class="bg-surface-0 border border-surface-300 rounded-xl shadow-soft-lg p-4 sm:p-6 lg:p-8">
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 mb-6">
              <div v-for="kpi in previewKpis" :key="kpi.label" class="bg-surface-50 border border-surface-300 rounded-lg p-3.5">
                <div class="flex items-center gap-1.5 mb-2">
                  <i :class="['pi', kpi.icon, 'text-surface-500 text-[11px]']" />
                  <span class="text-[10px] font-semibold text-surface-500 uppercase tracking-wide truncate">{{ kpi.label }}</span>
                </div>
                <span class="text-sm sm:text-base font-bold tabular-nums font-display" :class="kpi.tone">{{ kpi.value }}</span>
              </div>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-12 gap-4 sm:gap-6">
              <div class="lg:col-span-7 bg-surface-50 border border-surface-300 rounded-lg p-4 sm:p-5">
                <div class="flex items-center justify-between mb-4">
                  <span class="text-sm font-semibold text-surface-900">Income vs Expense</span>
                  <div class="flex items-center gap-3 text-xs text-surface-500">
                    <span class="flex items-center gap-1.5"><span class="w-2 h-2 rounded-full bg-success" />Income</span>
                    <span class="flex items-center gap-1.5"><span class="w-2 h-2 rounded-full bg-danger" />Expense</span>
                  </div>
                </div>
                <!-- Static, decorative SVG — not a real chart component; no
                     Chart.js instance and no dashboard data are involved. -->
                <svg viewBox="0 0 400 140" class="w-full h-28 sm:h-36" preserveAspectRatio="none" aria-hidden="true">
                  <defs>
                    <linearGradient id="landing-preview-fill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="var(--color-primary-500)" stop-opacity="0.35" />
                      <stop offset="100%" stop-color="var(--color-primary-500)" stop-opacity="0" />
                    </linearGradient>
                  </defs>
                  <path
                    d="M0,120 C60,120 90,120 130,118 C170,116 190,20 230,18 C265,16 280,90 320,95 C345,98 370,60 400,40 L400,140 L0,140 Z"
                    fill="url(#landing-preview-fill)"
                  />
                  <path
                    d="M0,120 C60,120 90,120 130,118 C170,116 190,20 230,18 C265,16 280,90 320,95 C345,98 370,60 400,40"
                    fill="none"
                    stroke="var(--color-success)"
                    stroke-width="2.5"
                    stroke-linecap="round"
                  />
                  <path
                    d="M320,95 C345,98 370,60 400,40"
                    fill="none"
                    stroke="var(--color-danger)"
                    stroke-width="2.5"
                    stroke-linecap="round"
                  />
                </svg>
                <div class="flex justify-between text-[10px] text-surface-400 mt-2">
                  <span>Mar</span><span>Apr</span><span>May</span><span>Jun</span><span>Jul</span><span>Aug</span>
                </div>
              </div>

              <div class="lg:col-span-5 bg-surface-50 border border-surface-300 rounded-lg overflow-hidden">
                <div class="px-4 py-3 border-b border-surface-200 text-sm font-semibold text-surface-900">Recent Activity</div>
                <div v-for="row in previewActivity" :key="row.name" class="flex items-center justify-between gap-3 px-4 py-3 border-b border-surface-100 last:border-b-0">
                  <div class="min-w-0">
                    <p class="text-sm font-medium text-surface-900 truncate">{{ row.name }}</p>
                    <p class="text-xs text-surface-400">{{ row.category }} · {{ row.date }}</p>
                  </div>
                  <span class="text-sm font-semibold tabular-nums shrink-0" :class="row.tone">{{ row.amount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- closing CTA -->
      <section class="aurora-backdrop-subtle px-4 sm:px-6 lg:px-8 py-20">
        <div v-reveal class="max-w-2xl mx-auto text-center flex flex-col items-center gap-6">
          <h2 class="font-display text-3xl md:text-4xl font-normal tracking-tight text-surface-900">Ready to take control?</h2>
          <p class="text-sm md:text-base text-surface-500">
            Create a free account and see your first budget come together in minutes.
          </p>
          <router-link
            data-testid="landing-cta-get-started-button"
            to="/register"
            class="cta-pill inline-flex items-center gap-2 text-sm font-semibold px-6 py-3.5 rounded-full active:scale-[0.98]"
          >
            Get Started
            <i class="pi pi-arrow-right text-xs" />
          </router-link>
          <p class="text-xs text-surface-400">Secure sign-in, private by default.</p>
        </div>
      </section>
    </main>

    <footer class="border-t border-surface-200 px-4 sm:px-6 lg:px-8 py-10">
      <div class="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-6">
        <div class="flex items-center gap-2.5">
          <img :src="logoIcon" alt="ExpenseWise" class="w-7 h-7 object-contain" />
          <span class="font-display text-sm font-semibold tracking-[0.08em] uppercase text-surface-900">ExpenseWise</span>
        </div>

        <div class="flex items-center gap-6">
          <button data-testid="landing-footer-terms-link" type="button" class="text-xs font-medium text-surface-500 hover:text-surface-800 transition-colors duration-fast ease-out-expo" @click="termsOpen = true">
            Terms
          </button>
          <button data-testid="landing-footer-privacy-link" type="button" class="text-xs font-medium text-surface-500 hover:text-surface-800 transition-colors duration-fast ease-out-expo" @click="privacyOpen = true">
            Privacy
          </button>
          <router-link data-testid="landing-footer-signin-link" to="/login" class="text-xs font-medium text-surface-500 hover:text-surface-800 transition-colors duration-fast ease-out-expo">
            Sign in
          </router-link>
        </div>

        <p class="text-xs text-surface-400">© 2026 ExpenseWise. An academic project.</p>
      </div>
    </footer>

    <Dialog v-model:visible="termsOpen" modal header="Terms of Service" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="landing-terms-dialog">
      <TermsContent />
    </Dialog>
    <Dialog v-model:visible="privacyOpen" modal header="Privacy Policy" :style="{ width: '640px' }" :breakpoints="{ '768px': '92vw' }" data-testid="landing-privacy-dialog">
      <PrivacyContent />
    </Dialog>
  </div>
</template>
