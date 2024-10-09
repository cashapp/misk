import Editor from "@misk-console/completion/Editor"
import {
  findIndexByPosition,
  findPosition,
  findPositionByIndex,
  insertString
} from "@misk-console/utils/common"
import PositionWithIndex from "@misk-console/completion/PositionWithIndex"
import CompletionProvider from "@misk-console/completion/CompletionProvider"
import Completion from "@misk-console/completion/Completion"
import Position from "@misk-console/completion/Position"

export interface CompletionResult {
  completion: Completion
  select: () => void
}

export default class FakeEditor implements Editor {
  text: string = ""

  rows: string[] = []
  cursor: PositionWithIndex = { row: 0, column: 0, index: 0 }
  completions?: CompletionProvider

  moveCursorTo(row: number, column: number) {
    let index = 0
    for (let i = 0; i < row; i++) {
      index += this.rows[i].length + 1
    }
    index += column
    this.cursor = { row, column, index }
  }

  indexToPosition(index: number): Position {
    return findPositionByIndex(this.text, index)
  }

  positionToIndex(position: Position): number {
    return findIndexByPosition(this.text, position)
  }

  valueAt(row: number, column: number): string {
    return this.rows[row][column]
  }

  delete(row: number, column: number) {
    const edit = this.rows.map((it, i) => {
      if (i === row) {
        return it.slice(0, column) + it.slice(column + 1)
      } else {
        return it
      }
    })
    this.setTextWithCursor(
      insertString(edit.join("\n"), "|", this.cursor.index)
    )
  }

  setTextWithCursor(text: string) {
    this.cursor = findPosition(text, "|")!
    this.text = text.replace(/\|/g, "")
    this.rows = this.text.split("\n")
  }

  private applyCompletion(completion: Completion) {
    this.setTextWithCursor(
      insertString(
        this.text,
        completion.value.slice(completion.prefix.length) + "|",
        this.cursor.index
      )
    )
    if (completion.onSelected) {
      completion.onSelected(this.cursor)
    }
  }

  textWithCursor(): string {
    return insertString(this.text, "|", this.cursor.index)
  }

  async getCompletions(): Promise<CompletionResult[]> {
    let prefix = ""
    let i = this.cursor.index - 1
    while (true) {
      const c = this.text[i]
      if (c.match(/[a-zA-Z0-9"]/) === null) {
        break
      } else {
        prefix += c
        i--
      }
    }

    const completions = await this.completions!.getCompletions({
      editor: this,
      text: this.text,
      cursor: this.cursor,
      prefix: prefix,
      completerRef: null
    })

    return completions.map(it => ({
      completion: it,
      select: () => this.applyCompletion(it)
    }))
  }
}
