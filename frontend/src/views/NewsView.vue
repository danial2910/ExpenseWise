<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { isAxiosError } from 'axios'
import AppLayout from '../layouts/AppLayout.vue'
import ErrorState from '../components/common/ErrorState.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchNews } from '../api/news'
import type { Article } from '../types/news'

type LoadState = 'loading' | 'error' | 'locked' | 'ready'

const loadState = ref<LoadState>('loading')
const articles = ref<Article[]>([])
const brokenImages = ref<Set<number>>(new Set())

async function loadNews() {
  loadState.value = 'loading'
  brokenImages.value = new Set()
  try {
    articles.value = await fetchNews()
    loadState.value = 'ready'
  } catch (error) {
    // A disabled News entitlement gets its own calm "not available" state,
    // not the generic error — this is an expected, designed outcome (an
    // admin turned the feature off), not something retrying would fix.
    loadState.value = isAxiosError(error) && error.response?.status === 403 ? 'locked' : 'error'
  }
}
onMounted(loadNews)

function formatDate(iso: string | null): string {
  if (!iso) {
    return ''
  }
  // "This month"-style presentation rule: convert to Asia/Kuala_Lumpur in
  // the presentation layer only — publishedAt arrives as UTC.
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'Asia/Kuala_Lumpur',
  }).format(new Date(iso))
}

function onImageError(index: number) {
  brokenImages.value = new Set(brokenImages.value).add(index)
}
</script>

<template>
  <AppLayout title="News">
    <div class="flex flex-col gap-6">
      <div>
        <h1 data-testid="news-title" class="font-display text-2xl font-semibold tracking-tight text-surface-900">Financial News</h1>
        <p class="text-sm text-surface-500 mt-1">Curated headlines to help you stay on top of your money</p>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="news-error-state"
        retry-testid="news-retry-button"
        title="Couldn't load news"
        description="Something went wrong while fetching the latest articles. Try again."
        class="bg-surface-0 border border-surface-300 rounded-xl"
        @retry="loadNews"
      />

      <EmptyState
        v-else-if="loadState === 'locked'"
        testid="news-locked-state"
        icon="pi-lock"
        title="News isn't available on your account"
        description="This feature isn't turned on for you right now. Contact an admin if you'd like it enabled."
        class="bg-surface-0 border border-surface-300 rounded-xl"
      />

      <!-- loading state -->
      <div
        v-else-if="loadState === 'loading'"
        data-testid="news-loading-skeleton"
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5"
      >
        <div v-for="n in 6" :key="n" class="bg-surface-0 border border-surface-300 rounded-xl overflow-hidden">
          <div class="w-full h-[140px] bg-surface-200 animate-pulse" />
          <div class="p-4 flex flex-col gap-2">
            <div class="w-20 h-3 rounded bg-surface-200 animate-pulse" />
            <div class="w-full h-4 rounded bg-surface-200 animate-pulse" />
            <div class="w-3/5 h-4 rounded bg-surface-200 animate-pulse" />
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="articles.length === 0"
        testid="news-empty-state"
        icon="pi-inbox"
        title="No articles right now"
        description="Check back later for new headlines."
        class="bg-surface-0 border border-surface-300 rounded-xl"
      />

      <!-- ready state -->
      <div v-else data-testid="news-articles-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        <div
          v-for="(article, index) in articles"
          :key="article.url + index"
          :data-testid="`news-article-card-${index}`"
          class="bg-surface-0 border border-surface-300 rounded-xl overflow-hidden flex flex-col hover:border-surface-300 transition-colors duration-fast ease-out-expo"
        >
          <img
            v-if="article.imageUrl && !brokenImages.has(index)"
            :src="article.imageUrl"
            :alt="article.title"
            class="w-full h-[140px] object-cover shrink-0"
            @error="onImageError(index)"
          />
          <div v-else class="w-full h-[140px] bg-surface-100 shrink-0 flex items-center justify-center text-surface-400">
            <i class="pi pi-image text-3xl" />
          </div>

          <div class="p-4 flex flex-col gap-2 flex-1">
            <span :data-testid="`news-article-source-${index}`" class="text-xs text-surface-400">
              {{ article.source }} · {{ formatDate(article.publishedAt) }}
            </span>
            <p :data-testid="`news-article-title-${index}`" class="text-[15px] font-semibold text-surface-900 leading-snug">
              {{ article.title }}
            </p>
            <p v-if="article.snippet" class="text-[13px] text-surface-500 leading-relaxed flex-1">
              {{ article.snippet }}
            </p>
            <a
              :data-testid="`news-article-readmore-${index}`"
              :href="article.url"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[13px] font-semibold text-primary-300 hover:text-primary-200 no-underline mt-1 inline-flex items-center gap-1 transition-colors duration-fast ease-out-expo"
            >
              Read more
              <i class="pi pi-arrow-up-right text-[11px]" />
            </a>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
