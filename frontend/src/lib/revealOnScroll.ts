import type { Directive } from 'vue'

// `v-reveal` — fades an element in the first time it scrolls into view, then
// stops observing it (a marketing-page scroll effect, not a data binding,
// so a plain mounted-hook directive is simpler here than a composable per
// section). The actual fade/translate transition lives in style.css's
// `.reveal`/`.reveal-visible` classes, which — like every other transition
// in this app — collapses to instant under `prefers-reduced-motion` via the
// one global media query in style.css, so this directive needs no
// reduced-motion check of its own.
const vReveal: Directive<HTMLElement> = {
  mounted(el) {
    el.classList.add('reveal')
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('reveal-visible')
          observer.disconnect()
        }
      },
      { threshold: 0.15 },
    )
    observer.observe(el)
  },
}

export default vReveal
