import { definePreset } from '@primevue/themes'
import Aura from '@primevue/themes/aura'

// Mirrors src/style.css's @theme block exactly — same values under the same
// 50–950 shade names — so PrimeVue components (Button, InputText, Dialog,
// DataTable, Toast, ...) and Tailwind utility classes read as one system.
// PrimeVue owns component appearance; Tailwind owns layout only. Changing a
// value here without changing style.css (or vice versa) is a bug.
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
      50: 'oklch(85% 0.12 195 / 10%)',
      100: 'oklch(85% 0.12 195 / 16%)',
      200: 'oklch(85% 0.12 195 / 30%)',
      300: '#a5f3fc',
      400: '#67e8f9',
      500: '#22d3ee',
      600: '#06b6d4',
      700: '#0891b2',
      800: '#0e7490',
      900: '#155e75',
      950: '#083344',
    },
    colorScheme: {
      light: {
        surface: {
          0: '#0a0e18',
          50: 'oklch(100% 0 0 / 5%)',
          100: '#030711',
          200: 'oklch(100% 0 0 / 8%)',
          300: 'oklch(100% 0 0 / 10%)',
          400: 'oklch(100% 0 0 / 30%)',
          500: 'oklch(100% 0 0 / 45%)',
          600: 'oklch(100% 0 0 / 65%)',
          700: 'oklch(100% 0 0 / 80%)',
          800: 'oklch(100% 0 0 / 90%)',
          900: '#ffffff',
          950: '#010308',
        },
      },
    },
  },
  components: {
    /*
     * Primary button. The reference never fills a CTA with flat bright
     * cyan — every call to action there is a dark pill carrying a cyan
     * hairline and a soft cyan glow, so the accent reads as emitted light
     * rather than painted surface. Encoded here (not per-view) so every
     * PrimeVue Button in the app inherits it. The glow itself is applied
     * in style.css, since PrimeVue exposes no shadow token for the root.
     */
    button: {
      colorScheme: {
        light: {
          root: {
            primary: {
              background: '{surface.0}',
              hoverBackground: '{surface.200}',
              activeBackground: '{surface.200}',
              borderColor: '{primary.200}',
              hoverBorderColor: '{primary.300}',
              activeBorderColor: '{primary.300}',
              color: '{surface.900}',
              hoverColor: '{surface.900}',
              activeColor: '{surface.900}',
            },
          },
        },
      },
    },
  },
})

export default preset
