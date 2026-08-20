<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import AppLayout from '../layouts/AppLayout.vue'
import {
  createConversation,
  deleteConversation,
  fetchConversation,
  fetchConversations,
  fetchInsights,
  postMessage,
} from '../api/ai'
import type { AiConversationSummaryResponse, AiMessageResponse, InsightResponse, InsightSeverity } from '../types/ai'

type LoadState = 'loading' | 'error' | 'ready'

const SUGGESTED_PROMPTS = [
  'How much did I spend on dining this month?',
  'Am I on track with my budgets?',
  'Where can I cut back on spending?',
  'How does this month compare to last month?',
]

const loadState = ref<LoadState>('loading')
const conversations = ref<AiConversationSummaryResponse[]>([])
const activeConversationId = ref<number | null>(null)
const messages = ref<AiMessageResponse[]>([])
const inputText = ref('')
const sending = ref(false)
const isError = ref(false)
const messageScrollRef = ref<HTMLElement | null>(null)

const insightsLoadState = ref<LoadState>('loading')
const insights = ref<InsightResponse[]>([])

// Responsive-only UI state: below lg, the conversation rail collapses into a
// drawer opened via a header button; below md, the insights panel collapses
// into a Chat/Insights tab switcher instead of a persistent side column.
const historyDrawerOpen = ref(false)
const mobileTab = ref<'chat' | 'insights'>('chat')

const chatTitle = computed(() => {
  if (activeConversationId.value === null) return 'New conversation'
  return conversations.value.find((c) => c.id === activeConversationId.value)?.title ?? 'Conversation'
})

function formatDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString('en-MY', { day: 'numeric', month: 'short' })
}

function severityColorClass(severity: InsightSeverity) {
  if (severity === 'CRITICAL') return 'text-danger'
  if (severity === 'WARNING') return 'text-warning'
  return 'text-success'
}

async function scrollToBottom() {
  await nextTick()
  if (messageScrollRef.value) {
    messageScrollRef.value.scrollTop = messageScrollRef.value.scrollHeight
  }
}

async function loadConversations() {
  const page = await fetchConversations()
  conversations.value = page.content
}

async function loadInsights() {
  insightsLoadState.value = 'loading'
  try {
    const response = await fetchInsights()
    insights.value = response.insights
    insightsLoadState.value = 'ready'
  } catch {
    insightsLoadState.value = 'error'
  }
}

async function openConversation(id: number) {
  loadState.value = 'loading'
  try {
    const conversation = await fetchConversation(id)
    activeConversationId.value = conversation.id
    messages.value = conversation.messages
    loadState.value = 'ready'
    scrollToBottom()
  } catch {
    loadState.value = 'error'
  }
}

function startNewChat() {
  activeConversationId.value = null
  messages.value = []
  isError.value = false
}

async function onDeleteConversation(id: number) {
  try {
    await deleteConversation(id)
    conversations.value = conversations.value.filter((c) => c.id !== id)
    if (activeConversationId.value === id) {
      startNewChat()
    }
  } catch {
    // The conversation list is refreshed on next load; a failed delete
    // leaves it as-is, so the user can just retry the click.
  }
}

async function sendMessage(text: string) {
  const content = text.trim()
  if (!content || sending.value) return

  inputText.value = ''
  isError.value = false
  const optimisticUserMessage: AiMessageResponse = {
    id: -Date.now(),
    role: 'user',
    content,
    createdAt: new Date().toISOString(),
  }
  messages.value = [...messages.value, optimisticUserMessage]
  scrollToBottom()
  sending.value = true

  try {
    if (activeConversationId.value === null) {
      const conversation = await createConversation({ firstMessage: content })
      activeConversationId.value = conversation.id
      messages.value = conversation.messages
      conversations.value = [
        { id: conversation.id, title: conversation.title, createdAt: conversation.createdAt },
        ...conversations.value,
      ]
    } else {
      const assistantMessage = await postMessage(activeConversationId.value, { content })
      messages.value = [...messages.value, assistantMessage]
    }
    scrollToBottom()
  } catch {
    // The backend rolls the whole exchange back on failure, so the
    // optimistic message never actually persisted — drop it, restore the
    // input, and surface the error banner instead of leaving a message
    // that looks sent.
    messages.value = messages.value.filter((m) => m.id !== optimisticUserMessage.id)
    isError.value = true
    inputText.value = content
  } finally {
    sending.value = false
  }
}

function onSubmit() {
  sendMessage(inputText.value)
}

onMounted(async () => {
  try {
    await loadConversations()
    if (conversations.value.length > 0) {
      await openConversation(conversations.value[0].id)
    } else {
      loadState.value = 'ready'
    }
  } catch {
    loadState.value = 'error'
  }
  loadInsights()
})
</script>

<template>
  <AppLayout title="AI Assistant">
    <div class="flex gap-4 lg:gap-6 h-[calc(100vh-200px)] md:h-[calc(100vh-176px)] lg:h-[calc(100vh-160px)]">
      <!-- conversation rail — persistent sidebar from lg up, a drawer below lg -->
      <div class="hidden lg:flex w-64 shrink-0 bg-surface-0 border border-surface-300 rounded-xl flex-col overflow-hidden">
        <div class="p-4 border-b border-surface-100">
          <Button
            data-testid="ai-new-chat-button"
            label="New chat"
            icon="pi pi-plus"
            severity="secondary"
            outlined
            class="w-full active:scale-[0.98] transition-transform duration-fast ease-out-expo"
            @click="startNewChat"
          />
        </div>
        <div class="flex-1 overflow-y-auto p-2">
          <div v-if="conversations.length === 0" data-testid="ai-conversations-empty" class="text-center text-sm text-surface-400 py-6">
            No conversations yet
          </div>
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :data-testid="`ai-conversation-item-${conv.id}`"
            class="group flex items-center gap-1 px-3 py-2.5 rounded-lg mb-1 cursor-pointer transition-colors duration-fast ease-out-expo"
            :class="conv.id === activeConversationId ? 'bg-primary-50' : 'hover:bg-surface-50'"
            @click="openConversation(conv.id)"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-surface-900 truncate">{{ conv.title }}</p>
              <p class="text-xs text-surface-400 mt-0.5">{{ formatDate(conv.createdAt) }}</p>
            </div>
            <button
              :data-testid="`ai-delete-conversation-${conv.id}`"
              class="w-6 h-6 rounded-md flex items-center justify-center text-surface-400 opacity-0 group-hover:opacity-100 hover:bg-danger-bg hover:text-danger shrink-0 transition-colors duration-fast ease-out-expo"
              @click.stop="onDeleteConversation(conv.id)"
            >
              <i class="pi pi-trash text-xs" />
            </button>
          </div>
        </div>
      </div>

      <!-- chat -->
      <div class="flex-1 bg-surface-0 border border-surface-300 rounded-xl flex flex-col min-w-0">
        <div class="h-14 lg:h-16 shrink-0 flex items-center justify-between px-4 lg:px-6 border-b border-surface-200">
          <button
            data-testid="ai-history-toggle-button"
            type="button"
            aria-label="Open conversation history"
            class="lg:hidden w-11 h-11 -ml-2 flex items-center justify-center text-surface-500 shrink-0"
            @click="historyDrawerOpen = true"
          >
            <i class="pi pi-bars" />
          </button>
          <span class="text-base font-semibold text-surface-900 truncate">{{ chatTitle }}</span>
          <div class="lg:hidden w-11 shrink-0" aria-hidden="true" />
        </div>

        <!-- mobile-only Chat/Insights tabs -->
        <div class="flex md:hidden border-b border-surface-200">
          <button
            data-testid="ai-tab-chat"
            type="button"
            class="flex-1 min-h-11 flex items-center justify-center text-sm font-semibold border-b-2 transition-colors duration-fast ease-out-expo"
            :class="mobileTab === 'chat' ? 'text-primary-300 border-primary-500' : 'text-surface-500 border-transparent'"
            @click="mobileTab = 'chat'"
          >
            Chat
          </button>
          <button
            data-testid="ai-tab-insights"
            type="button"
            class="flex-1 min-h-11 flex items-center justify-center text-sm font-semibold border-b-2 transition-colors duration-fast ease-out-expo"
            :class="mobileTab === 'insights' ? 'text-primary-300 border-primary-500' : 'text-surface-500 border-transparent'"
            @click="mobileTab = 'insights'"
          >
            Insights
          </button>
        </div>

        <Transition name="field-in">
          <div v-if="isError" data-testid="ai-error-banner" class="mx-4 lg:mx-6 mt-4 px-4 py-3 bg-warning-bg border border-warning/30 rounded-lg flex items-center gap-2.5">
            <i class="pi pi-exclamation-triangle text-warning" />
            <span class="text-sm text-surface-800">
              AI assistant is temporarily unavailable. Your data and the rest of the app are unaffected — try again shortly.
            </span>
          </div>
        </Transition>

        <!-- tablet-only horizontal insight strip (desktop uses the side panel, mobile uses the Insights tab) -->
        <div
          v-if="insightsLoadState === 'ready' && insights.length > 0"
          data-testid="ai-insight-strip"
          class="hidden md:flex lg:hidden gap-2.5 overflow-x-auto px-4 pt-4"
        >
          <div
            v-for="(insight, i) in insights"
            :key="i"
            :data-testid="`ai-insight-strip-${i}`"
            class="min-w-[220px] shrink-0 border border-surface-300 rounded-lg p-3"
          >
            <span class="text-[10px] font-semibold text-surface-400 uppercase tracking-wide block mb-1">Insight</span>
            <p class="text-xs font-semibold" :class="severityColorClass(insight.severity)">{{ insight.title }}</p>
          </div>
        </div>

        <div v-if="loadState === 'loading'" data-testid="ai-chat-loading" class="flex-1 flex items-center justify-center">
          <i class="pi pi-spin pi-spinner text-surface-300 text-2xl" />
        </div>
        <div v-else-if="loadState === 'error'" data-testid="ai-chat-load-error" class="flex-1 flex items-center justify-center">
          <span class="text-sm text-surface-500">Couldn't load this conversation.</span>
        </div>
        <template v-else>
          <div
            ref="messageScrollRef"
            class="flex-1 overflow-y-auto p-4 lg:p-6 flex-col gap-4"
            :class="mobileTab === 'chat' ? 'flex' : 'hidden md:flex'"
          >
            <div v-if="messages.length === 0" data-testid="ai-empty-state" class="flex-1 flex flex-col items-center justify-center gap-4 text-center">
              <div class="w-12 h-12 rounded-full bg-primary-50 flex items-center justify-center">
                <i class="pi pi-sparkles text-primary-300" />
              </div>
              <p class="font-display text-base font-semibold text-surface-900">Ask about your spending</p>
              <p class="text-sm text-surface-500 max-w-sm">
                The assistant answers using your transaction and budget data. Try one of these:
              </p>
              <div class="flex flex-col gap-2 w-full max-w-sm">
                <button
                  v-for="(prompt, i) in SUGGESTED_PROMPTS"
                  :key="i"
                  :data-testid="`ai-suggested-prompt-${i}`"
                  class="text-left px-3.5 py-3 border border-surface-300 rounded-lg text-sm text-surface-700 hover:bg-surface-50 hover:border-surface-300 transition-colors duration-fast ease-out-expo"
                  @click="sendMessage(prompt)"
                >
                  {{ prompt }}
                </button>
              </div>
            </div>

            <template v-else>
              <TransitionGroup name="field-in" tag="div" class="contents">
                <div
                  v-for="message in messages"
                  :key="message.id"
                  class="flex"
                  :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                >
                  <div class="max-w-[85%] lg:max-w-[75%] flex flex-col gap-1">
                    <span v-if="message.role === 'assistant'" class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">
                      Assistant
                    </span>
                    <div
                      :data-testid="`ai-message-${message.id}`"
                      class="px-4 py-3 rounded-lg text-sm leading-relaxed whitespace-pre-wrap"
                      :class="message.role === 'user' ? 'bg-primary-50 text-surface-900' : 'bg-surface-50 text-surface-900'"
                    >
                      {{ message.content }}
                    </div>
                  </div>
                </div>
              </TransitionGroup>

              <div v-if="sending" data-testid="ai-loading-indicator" class="flex justify-start">
                <div class="max-w-[85%] lg:max-w-[75%] flex flex-col gap-1.5">
                  <span class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">Assistant</span>
                  <div class="px-4 py-3 rounded-lg bg-surface-50 flex items-center gap-2.5">
                    <span class="flex gap-1">
                      <span class="w-1.5 h-1.5 rounded-full bg-surface-500 animate-bounce" style="animation-delay: 0ms" />
                      <span class="w-1.5 h-1.5 rounded-full bg-surface-500 animate-bounce" style="animation-delay: 150ms" />
                      <span class="w-1.5 h-1.5 rounded-full bg-surface-500 animate-bounce" style="animation-delay: 300ms" />
                    </span>
                    <span class="text-sm text-surface-500">Analyzing your transactions — this can take up to 15 seconds</span>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <div
            class="p-3 lg:p-4 border-t border-surface-200 flex-col gap-2"
            :class="mobileTab === 'chat' ? 'flex' : 'hidden md:flex'"
          >
            <div class="flex items-center gap-2.5">
              <input
                v-model="inputText"
                data-testid="ai-message-input"
                type="text"
                placeholder="Ask about your spending, budgets, or savings…"
                class="flex-1 min-h-[44px] px-3.5 border border-surface-300 rounded-lg text-sm text-surface-900 bg-surface-0"
                :disabled="sending"
                @keydown.enter="onSubmit"
              />
              <button
                data-testid="ai-send-button"
                class="w-11 h-11 rounded-lg bg-primary-500 flex items-center justify-center shrink-0 disabled:opacity-50 active:scale-[0.95] transition-transform duration-fast ease-out-expo"
                :disabled="sending || !inputText.trim()"
                @click="onSubmit"
              >
                <i class="pi pi-send text-surface-100 text-sm" />
              </button>
            </div>
            <p data-testid="ai-disclaimer" class="text-xs text-surface-400">
              General information only — not professional financial advice.
            </p>
          </div>

          <!-- mobile-only Insights tab content (desktop/tablet show insights via the side panel / strip above) -->
          <div
            class="md:hidden flex-1 overflow-y-auto p-4 flex-col gap-3"
            :class="mobileTab === 'insights' ? 'flex' : 'hidden'"
          >
            <div v-if="insightsLoadState === 'loading'" data-testid="ai-insights-loading-mobile" class="flex flex-col gap-3">
              <div v-for="n in 3" :key="n" class="h-20 rounded-lg bg-surface-100 animate-pulse" />
            </div>
            <div v-else-if="insightsLoadState === 'error'" data-testid="ai-insights-error-mobile" class="text-sm text-surface-400">
              Couldn't load your spending analysis.
            </div>
            <div v-else-if="insights.length === 0" data-testid="ai-insights-empty-mobile" class="text-sm text-surface-400">
              Add transactions to unlock spending insights.
            </div>
            <template v-else>
              <div v-for="(insight, i) in insights" :key="i" :data-testid="`ai-insight-mobile-${i}`" class="border border-surface-300 rounded-lg p-3.5">
                <span class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">Insight</span>
                <p class="text-sm font-semibold mt-1" :class="severityColorClass(insight.severity)">{{ insight.title }}</p>
                <p class="text-sm text-surface-600 mt-1 leading-relaxed">{{ insight.body }}</p>
              </div>
              <p class="text-[11px] text-surface-400 mt-1">Based on your budgets and transactions this month.</p>
            </template>
          </div>
        </template>
      </div>

      <!-- insights panel — desktop only; tablet uses the horizontal strip above, mobile uses the Insights tab -->
      <div class="hidden lg:flex w-80 shrink-0 bg-surface-0 border border-surface-300 rounded-xl overflow-y-auto p-5 flex-col gap-4">
        <span class="text-sm font-semibold text-surface-900">Spending Analysis</span>
        <div v-if="insightsLoadState === 'loading'" data-testid="ai-insights-loading" class="flex flex-col gap-3">
          <div v-for="n in 3" :key="n" class="h-20 rounded-lg bg-surface-100 animate-pulse" />
        </div>
        <div v-else-if="insightsLoadState === 'error'" data-testid="ai-insights-error" class="text-sm text-surface-400">
          Couldn't load your spending analysis.
        </div>
        <div v-else-if="insights.length === 0" data-testid="ai-insights-empty" class="text-sm text-surface-400">
          Add transactions to unlock spending insights.
        </div>
        <template v-else>
          <div v-for="(insight, i) in insights" :key="i" :data-testid="`ai-insight-${i}`" class="border border-surface-300 rounded-lg p-3.5">
            <span class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">Insight</span>
            <p class="text-sm font-semibold mt-1" :class="severityColorClass(insight.severity)">{{ insight.title }}</p>
            <p class="text-sm text-surface-600 mt-1 leading-relaxed">{{ insight.body }}</p>
          </div>
          <p class="text-[11px] text-surface-400 mt-1">Based on your budgets and transactions this month.</p>
        </template>
      </div>
    </div>

    <!-- conversation history drawer — tablet/mobile only, mirrors the desktop rail -->
    <Drawer v-model:visible="historyDrawerOpen" position="left" data-testid="ai-history-drawer" class="lg:hidden" :style="{ width: '18rem' }">
      <template #header>
        <span class="text-sm font-semibold text-surface-900">Conversations</span>
      </template>
      <div class="flex flex-col gap-3 -m-2">
        <Button
          data-testid="ai-new-chat-button-mobile"
          label="New chat"
          icon="pi pi-plus"
          severity="secondary"
          outlined
          class="w-full"
          @click="startNewChat(); historyDrawerOpen = false"
        />
        <div v-if="conversations.length === 0" data-testid="ai-conversations-empty-mobile" class="text-center text-sm text-surface-400 py-6">
          No conversations yet
        </div>
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :data-testid="`mobile-ai-conversation-item-${conv.id}`"
          class="group flex items-center gap-1 px-3 py-2.5 rounded-lg cursor-pointer"
          :class="conv.id === activeConversationId ? 'bg-primary-50' : 'hover:bg-surface-50'"
          @click="openConversation(conv.id); historyDrawerOpen = false"
        >
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-surface-900 truncate">{{ conv.title }}</p>
            <p class="text-xs text-surface-400 mt-0.5">{{ formatDate(conv.createdAt) }}</p>
          </div>
          <button
            :data-testid="`mobile-ai-delete-conversation-${conv.id}`"
            class="w-8 h-8 rounded-md flex items-center justify-center text-surface-400 hover:bg-danger-bg hover:text-danger shrink-0 transition-colors duration-fast ease-out-expo"
            @click.stop="onDeleteConversation(conv.id)"
          >
            <i class="pi pi-trash text-xs" />
          </button>
        </div>
      </div>
    </Drawer>
  </AppLayout>
</template>
