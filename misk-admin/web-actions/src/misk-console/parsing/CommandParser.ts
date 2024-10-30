import {
  matchNextNonWhitespace,
  matchNextWhitespace
} from "@misk-console/utils/common"
import AstNode from "@misk-console/parsing/ast/AstNode"
import TopLevel from "@misk-console/parsing/ast/TopLevel"
import Action from "@misk-console/parsing/ast/Action"
import Arr from "@misk-console/parsing/ast/Arr"
import Obj from "@misk-console/parsing/ast/Obj"
import Field from "@misk-console/parsing/ast/Field"
import StrLiteral from "@misk-console/parsing/ast/StrLiteral"
import JsonValue from "@misk-console/parsing/ast/JsonValue"
import Unexpected from "@misk-console/parsing/ast/Unexpected"
import NumLiteral from '@misk-console/parsing/ast/NumLiteral';
import BoolLiteral from '@misk-console/parsing/ast/BoolLiteral';

export function parseDocument(text: string, cursorIndex: number): TopLevel {
  return new CommandParser(text, cursorIndex).parse()
}

export class CommandParser {
  private readonly text: string
  private readonly cursorIndex: number
  private index: number = 0

  private identifierPattern = /^[a-zA-Z0-9-]+/g

  constructor(text: string, cursorIndex?: number) {
    this.text = text
    this.cursorIndex = cursorIndex ?? 0
  }

  parse(): TopLevel {
    return this.parseTopLevel()
  }

  advanceToNextNonWhitespace() {
    this.index += matchNextWhitespace(this.text, this.index) ?? 0
  }

  parseAlphaNum(): string | null {
    this.advanceToNextNonWhitespace()
    const match = this.text.substring(this.index).match(this.identifierPattern)
    if (match) {
      this.index += match[0].length
      return match[0]
    }
    return null
  }

  readNext(value: string): boolean {
    this.advanceToNextNonWhitespace()
    if (this.text.startsWith(value, this.index)) {
      this.index += value.length
      return true
    }
    return false
  }

  parseStr(): StrLiteral | null {
    return this.mark<StrLiteral>(() => {
      if (!this.readNext('"')) {
        return null
      }
      let str = ""
      while (true) {
        const curr = this.text[this.index]
        if (curr === '"') {
          this.index++
          return new StrLiteral(str)
        } else if (curr === "\\" && this.text[this.index + 1] === '"') {
          str += '\\"'
          this.index += 2
          continue
        } else if (curr === undefined) {
          return new StrLiteral(str)
        } else if (curr === "\n") {
          this.index++
          return new StrLiteral(str)
        } else if (curr === "\r" && this.text[this.index + 1] === '\n') {
          return new StrLiteral(str)
        }

        str += curr
        this.index++
      }
    })
  }

  parseNum(): NumLiteral | null {
    return this.mark<NumLiteral>(() => {
      this.advanceToNextNonWhitespace()
      const match = /^[-0-9.]+/.exec(this.text.substring(this.index))
      if (match) {
        this.index += match[0].length
        return new NumLiteral(match[0])
      }
      return null
    })
  }

  parseBool(): BoolLiteral | null {
    return this.mark<BoolLiteral>(() => {
      if (this.readNext("true")) {
        return new BoolLiteral("true")
      } else if (this.readNext("false")) {
        return new BoolLiteral("false")
      }
      return null
    })
  }

  parseValue(): JsonValue | null {
    return this.mark<JsonValue>(() => {
      const str = this.parseStr()
      if (str !== null) {
        return str
      }
      const num = this.parseNum()
      if (num !== null) {
        return num
      }
      const bool = this.parseBool()
      if (bool !== null) {
        return bool
      }
      const obj = this.parseObj()
      if (obj) {
        return obj
      }
      const arr = this.parseArr()
      if (arr) {
        return arr
      }
      return null
    })
  }

  parseField(): Field | null {
    return this.mark<Field>(() => {
      const fieldName = this.parseStr()
      if (!fieldName) {
        return null
      }
      if (!this.readNext(":")) {
        return new Field(fieldName, null, this.readUnexpected())
      }
      const colorPosition = this.index - 1
      return new Field(
        fieldName,
        colorPosition,
        this.parseValue() ?? this.readUnexpected()
      )
    })
  }

  parseArr(): Arr | null {
    return this.mark<Arr>(() => {
      if (!this.readNext("[")) {
        return null
      }

      const arr: JsonValue[] = []
      const unexpected: Unexpected[] = []
      while (true) {
        if (this.readNext("]")) {
          break
        }
        const value = this.parseValue()
        if (value) {
          arr.push(value)
          this.readNext(",")
        } else {
          const u = this.readUnexpected()
          if (u) {
            unexpected.push(u)
          } else {
            break
          }
        }
      }
      return new Arr(arr, unexpected)
    })
  }

  mark<T extends AstNode>(fn: () => T | null): T | null {
    this.advanceToNextNonWhitespace()
    const start = this.index
    const result = fn()
    if (!result) {
      return result
    }
    const end = this.index
    const cI = this.cursorIndex
    result.start = start
    result.end = end
    result.hasCursor = start <= cI && cI <= end
    result.onEvalCursor(cI)
    return result
  }

  readUnexpected(): Unexpected | null {
    return this.mark<Unexpected>(() => {
      const value = matchNextNonWhitespace(this.text, this.index)
      if (value) {
        this.index += value.length
        return new Unexpected(value)
      }
      return null
    })
  }

  parseObj(): Obj | null {
    return this.mark<Obj>(() => {
      if (!this.readNext("{")) {
        return null
      }

      const fields: Field[] = []
      const unexpected: Unexpected[] = []
      while (true) {
        const field = this.parseField()
        if (field) {
          fields.push(field)
        } else {
          break
        }

        if (!this.readNext(",")) {
          break
        }
      }

      while (!this.readNext("}")) {
        const u = this.readUnexpected()
        if (u) {
          unexpected.push(u)
        } else {
          break
        }
      }

      return new Obj(fields, unexpected)
    })
  }

  parseTopLevel(): TopLevel {
    return new TopLevel(this.parseObj() ?? this.readUnexpected())
  }
}
