import AstNode from "@misk-console/parsing/ast/AstNode"
import Action from "@misk-console/parsing/ast/Action"
import Unexpected from "@misk-console/parsing/ast/Unexpected"
import { MiskActions } from "@misk-console/api/responseTypes"

export default class TopLevel extends AstNode {
  action: Action | null

  constructor(action: Action | Unexpected | null) {
    super()
    if (action instanceof Action) {
      this.action = action
    } else if (action instanceof Unexpected) {
      this.unexpected = [action]
      this.action = null
    } else {
      this.action = null
    }
    this.hasCursor = true

    if (this.action) {
      this.action.parent = this
    }
  }

  childNodes(): AstNode[] {
    return this.action ? [this.action] : []
  }

  applyTypes(metadata: MiskActions) {
    if (this.action) {
      const actionDefinition = metadata[this.action.name]
      if (actionDefinition) {
        this.action.applyTypes(actionDefinition)
      }
    }
  }
}
