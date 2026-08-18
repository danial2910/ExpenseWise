import { definePreset } from '@primevue/themes'
import Aura from '@primevue/themes/aura'

// Mirrors src/style.css's @theme block exactly — same OKLCH values under the
// same 50–950 shade names — so PrimeVue components (Button, InputText,
// Dialog, DataTable, Toast, ...) and Tailwind utility classes read as one
// system. PrimeVue owns component appearance; Tailwind owns layout only.
//
// This app has exactly one theme (dark aurora, CLAUDE.md "Design system") —
// there is no light/dark toggle. PrimeVue's `colorScheme` config still
// requires values under a `light` or `dark` key; `light` is used here
// because main.ts sets `darkModeSelector: false`, which makes PrimeVue
// always resolve through the `light` branch regardless of the OS/browser
// color scheme. The key name is just PrimeVue's internal label — the
// values themselves are the app's only theme.
const preset = definePreset(Aura, {
  semantic: {
    primary: {
      50: 'oklch(78% 0.14 200 / 14%)',
      100: 'oklch(78% 0.14 200 / 22%)',
      200: 'oklch(80% 0.12 200 / 36%)',
      300: 'oklch(85% 0.11 200)',
      400: 'oklch(80% 0.13 200)',
      500: 'oklch(75% 0.145 200)',
      600: 'oklch(72% 0.15 200)',
      700: 'oklch(62% 0.15 200)',
      800: 'oklch(50% 0.13 200)',
      900: 'oklch(38% 0.1 200)',
      950: 'oklch(24% 0.06 200)',
    },
    colorScheme: {
      light: {
        surface: {
          0: 'oklch(14% 0.045 258)',
          50: 'oklch(100% 0 0 / 5%)',
          100: 'oklch(9% 0.035 258)',
          200: 'oklch(100% 0 0 / 7%)',
          300: 'oklch(100% 0 0 / 14%)',
          400: 'oklch(42% 0.03 258)',
          500: 'oklch(68% 0.035 258)',
          600: 'oklch(76% 0.03 258)',
          700: 'oklch(85% 0.022 258)',
          800: 'oklch(91% 0.016 258)',
          900: 'oklch(97% 0.012 258)',
          950: 'oklch(4% 0.03 258)',
        },
      },
    },
  },
})

export default preset
