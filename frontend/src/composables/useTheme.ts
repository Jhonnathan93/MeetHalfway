/**
 * Theme composable. Dark mode is the default and only MVP theme
 * (Requirement 10.1): this ensures the document root carries
 * `data-theme="dark"`. It is written as a composable so a future light theme
 * can be added without touching component styles.
 */

export type Theme = 'dark'

const DEFAULT_THEME: Theme = 'dark'

/** Applies the default (dark) theme to the document root, if a DOM is present. */
export function applyDefaultTheme(): void {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('data-theme', DEFAULT_THEME)
  }
}

export function useTheme(): { theme: Theme; apply: () => void } {
  return { theme: DEFAULT_THEME, apply: applyDefaultTheme }
}
