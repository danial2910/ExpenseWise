import { reactive } from 'vue'

// Chart.js draws its default tooltip straight onto the <canvas>, which can
// only ever show a plain string — never a <MoneyDisplay>. CLAUDE.md requires
// every currency value to render through that component, so every chart in
// this app instead disables the canvas tooltip and uses Chart.js's
// `external` tooltip hook to drive this small reactive state object; the
// chart component then renders its OWN floating div from `tooltipState`,
// with real <MoneyDisplay> instances for the numbers.
export interface TooltipRow {
  label: string
  color: string
  amount: number
}

export interface TooltipState {
  visible: boolean
  x: number
  y: number
  title: string
  rows: TooltipRow[]
}

// Chart.js's own external-tooltip callback context isn't usefully typed
// without pulling in its full type surface for one small handler — `any`
// here is intentional, not a shortcut.
export function useExternalTooltip() {
  const tooltipState = reactive<TooltipState>({ visible: false, x: 0, y: 0, title: '', rows: [] })

  function externalTooltipHandler(context: any) {
    const { chart, tooltip } = context
    if (!tooltip || tooltip.opacity === 0) {
      tooltipState.visible = false
      return
    }

    const canvasRect = chart.canvas.getBoundingClientRect()
    tooltipState.visible = true
    tooltipState.x = canvasRect.left + tooltip.caretX
    tooltipState.y = canvasRect.top + tooltip.caretY
    tooltipState.title = tooltip.title?.[0] ?? ''
    tooltipState.rows = (tooltip.dataPoints ?? []).map((point: any) => {
      const backgroundColor = point.dataset?.backgroundColor
      const color = Array.isArray(backgroundColor) ? backgroundColor[point.dataIndex] : (point.dataset?.borderColor ?? backgroundColor)
      const raw = typeof point.raw === 'number' ? point.raw : Number(point.raw)
      return { label: point.dataset?.label ?? point.label, color, amount: raw }
    })
  }

  return { tooltipState, externalTooltipHandler }
}
