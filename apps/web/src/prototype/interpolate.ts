/** Replaces {state.key} with the current value; unknown keys render as "—". */
export function interpolate(text: string, state: Record<string, string>): string {
  return text.replace(/\{state\.([a-zA-Z][a-zA-Z0-9_]*)\}/g, (_, key: string) => state[key] ?? '—')
}
