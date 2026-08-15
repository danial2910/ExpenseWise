/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Injected by vite.config.ts's `define` from package.json's own version field.
declare const __APP_VERSION__: string
