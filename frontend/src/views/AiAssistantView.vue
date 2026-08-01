<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import Button from 'primevue/button'
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

const chatTitle = computed(() => {
  if (activeConversationId.value === null) return 'New conversation'
  return conversations.value.find((c) => c.id === activeConversationId.value)?.title ?? 'Conversation'
})

function formatDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString('en-MY', { day: 'numeric', month: 'short' })
}

function severityColorClass(severity: InsightSeverity) {
  if (severity === 'CRITICAL') return 'text-red-600'
  if (severity === 'WARNING') return 'text-amber-600'
  return 'text-green-700'
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
    <div class="flex gap-6" style="height: calc(100vh - 160px)">
      <!-- conversation rail -->
      <div class="w-64 shrink-0 bg-white border border-surface-200 rounded-lg flex flex-col overflow-hidden">
        <div class="p-4 border-b border-surface-100">
          <Button
            data-testid="ai-new-chat-button"
            label="New chat"
            icon="pi pi-plus"
            severity="secondary"
            outlined
            class="w-full"
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
            class="group flex items-center gap-1 px-3 py-2.5 rounded-lg mb-1 cursor-pointer"
            :class="conv.id === activeConversationId ? 'bg-primary-50' : 'hover:bg-surface-50'"
            @click="openConversation(conv.id)"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-surface-900 truncate">{{ conv.title }}</p>
              <p class="text-xs text-surface-400 mt-0.5">{{ formatDate(conv.createdAt) }}</p>
            </div>
            <button
              :data-testid="`ai-delete-conversation-${conv.id}`"
              class="w-6 h-6 rounded-md flex items-center justify-center text-surface-400 opacity-0 group-hover:opacity-100 hover:bg-red-50 hover:text-red-600 shrink-0"
              @click.stop="onDeleteConversation(conv.id)"
            >
              <i class="pi pi-trash text-xs" />
            </button>
          </div>
        </div>
      </div>

      <!-- chat -->
      <div class="flex-1 bg-white border border-surface-200 rounded-lg flex flex-col min-w-0">
        <div class="h-16 shrink-0 flex items-center px-6 border-b border-surface-200">
          <span class="text-base font-semibold text-surface-900">{{ chatTitle }}</span>
        </div>

        <div v-if="isError" data-testid="ai-error-banner" class="mx-6 mt-4 px-4 py-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2.5">
          <i class="pi pi-exclamation-triangle text-amber-600" />
          <span class="text-sm text-amber-800">
            AI assistant is temporarily unavailable. Your data and the rest of the app are unaffected — try again shortly.
          </span>
        </div>

        <div v-if="loadState === 'loading'" data-testid="ai-chat-loading" class="flex-1 flex items-center justify-center">
          <i class="pi pi-spin pi-spinner text-surface-300 text-2xl" />
        </div>
        <div v-else-if="loadState === 'error'" data-testid="ai-chat-load-error" class="flex-1 flex items-center justify-center">
          <span class="text-sm text-surface-500">Couldn't load this conversation.</span>
        </div>
        <div v-else ref="messageScrollRef" class="flex-1 overflow-y-auto p-6 flex flex-col gap-4">
          <div v-if="messages.length === 0" data-testid="ai-empty-state" class="flex-1 flex flex-col items-center justify-center gap-4 text-center">
            <p class="text-base font-semibold text-surface-900">Ask about your spending</p>
            <p class="text-sm text-surface-500 max-w-sm">
              The assistant answers using your transaction and budget data. Try one of these:
            </p>
            <div class="flex flex-col gap-2 w-full max-w-sm">
              <button
                v-for="(prompt, i) in SUGGESTED_PROMPTS"
                :key="i"
                :data-testid="`ai-suggested-prompt-${i}`"
                class="text-left px-3.5 py-3 border border-surface-200 rounded-lg text-sm text-surface-700 hover:bg-surface-50"
                @click="sendMessage(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <template v-else>
            <div
              v-for="message in messages"
              :key="message.id"
              class="flex"
              :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
            >
              <div class="max-w-[75%] flex flex-col gap-1">
                <span v-if="message.role === 'assistant'" class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">
                  Assistant
                </span>
                <div
                  :data-testid="`ai-message-${message.id}`"
                  class="px-4 py-3 rounded-lg text-sm leading-relaxed whitespace-pre-wrap"
                  :class="message.role === 'user' ? 'bg-primary-50 text-surface-900' : 'bg-surface-100 text-surface-900'"
                >
                  {{ message.content }}
                </div>
              </div>
            </div>

            <div v-if="sending" data-testid="ai-loading-indicator" class="flex justify-start">
              <div class="max-w-[75%] flex flex-col gap-1.5">
                <span class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">Assistant</span>
                <div class="px-4 py-3 rounded-lg bg-surface-100 flex items-center gap-2.5">
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

        <div class="p-4 border-t border-surface-200 flex flex-col gap-2">
          <div class="flex items-center gap-2.5">
            <input
              v-model="inputText"
              data-testid="ai-message-input"
              type="text"
              placeholder="Ask about your spending, budgets, or savings…"
              class="flex-1 min-h-[44px] px-3.5 border border-surface-200 rounded-lg text-sm text-surface-900"
              :disabled="sending"
              @keydown.enter="onSubmit"
            />
            <button
              data-testid="ai-send-button"
              class="w-11 h-11 rounded-lg bg-primary-600 flex items-center justify-center shrink-0 disabled:opacity-50"
              :disabled="sending || !inputText.trim()"
              @click="onSubmit"
            >
              <i class="pi pi-send text-white text-sm" />
            </button>
          </div>
          <p data-testid="ai-disclaimer" class="text-xs text-surface-400">
            General information only — not professional financial advice.
          </p>
        </div>
      </div>

      <!-- insights -->
      <div class="w-80 shrink-0 bg-white border border-surface-200 rounded-lg overflow-y-auto p-5 flex flex-col gap-4">
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
          <div v-for="(insight, i) in insights" :key="i" :data-testid="`ai-insight-${i}`" class="border border-surface-200 rounded-lg p-3.5">
            <span class="text-[11px] font-semibold text-surface-400 uppercase tracking-wide">Insight</span>
            <p class="text-sm font-semibold mt-1" :class="severityColorClass(insight.severity)">{{ insight.title }}</p>
            <p class="text-sm text-surface-600 mt-1 leading-relaxed">{{ insight.body }}</p>
          </div>
          <p class="text-[11px] text-surface-400 mt-1">Based on your budgets and transactions this month.</p>
        </template>
      </div>
    </div>
  </AppLayout>
</template>
