// Maps a category's stored `icon` string to a PrimeIcons class. PrimeIcons
// is already a project dependency (main.ts), so icons are reused from there
// rather than hand-drawn — some of the icon keys seeded in
// V1__baseline.sql (film, laptop, trending-up, ellipsis, utensils) predate
// PrimeIcons naming and are aliased to the closest available icon below.
const ICON_ALIASES: Record<string, string> = {
  utensils: 'pi-apple',
  film: 'pi-video',
  laptop: 'pi-desktop',
  'trending-up': 'pi-chart-line',
  ellipsis: 'pi-ellipsis-h',
}

// Curated picker options for custom categories — the icon string saved is
// the key itself (e.g. "car"), so no alias lookup is needed for these.
export const ICON_PICKER_OPTIONS = [
  'apple',
  'car',
  'receipt',
  'video',
  'shopping-bag',
  'heart',
  'book',
  'briefcase',
  'desktop',
  'chart-line',
  'gift',
  'home',
  'wallet',
  'tag',
] as const

const DEFAULT_ICON_CLASS = 'pi-tag'

export function categoryIconClass(icon: string | null): string {
  if (!icon) {
    return DEFAULT_ICON_CLASS
  }
  return ICON_ALIASES[icon] ?? `pi-${icon}`
}
