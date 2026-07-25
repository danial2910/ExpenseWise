import colors from 'tailwindcss/colors'

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // Matches the imported ExpenseWise auth/dashboard designs — indigo primary,
      // slate neutrals. Kept as Tailwind's own default palette values (just
      // aliased) rather than invented hex codes.
      colors: {
        primary: colors.indigo,
        surface: colors.slate,
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
