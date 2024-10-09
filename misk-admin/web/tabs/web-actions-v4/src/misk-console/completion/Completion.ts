import Position from "@misk-console/completion/Position"

export default interface Completion {
  value: string
  caption: string
  meta?: string
  onSelected?: (curr: Position) => void
  prefix: string

  /**
   * This is undocumented but completer must point back to the CompletionProvider, otherwise
   * the selection callback will not be called.
   */
  completer: any
}
