import { Ace } from "ace-builds"
import CompletionProvider from "@misk-console/completion/CompletionProvider"

import Completion from "@misk-console/completion/Completion"
import RealMetadataClient from "@misk-console/api/RealMetadataClient"
import AceEditor from "@misk-console/ui/AceEditor"
import { MiskWebActionDefinition } from '@misk-console/api/responseTypes';

export class ContextAwareCompleter implements Ace.Completer {
  completionProvider = new CompletionProvider(new RealMetadataClient())

  identifierRegexps = [/[a-zA-Z0-9"]/]

  onInsert(editor: Ace.Editor, data: Ace.Completion) {
    const completion = data as Completion
    if (completion.onSelected) {
      completion.onSelected(editor.getCursorPosition())
    }
  }

  public setSelection(selection: MiskWebActionDefinition | null) {

    this.completionProvider.setSelection(selection)
  }

  markers: number[] = []

  async generateCompletions(
    editor: Ace.Editor,
    session: Ace.EditSession,
    position: Ace.Point,
    prefix: string,
    callback: Ace.CompleterCallback
  ) {
    const index = session.doc.positionToIndex(position)

    const completions = await this.completionProvider.getCompletions({
      editor: new AceEditor(editor),
      text: session.getValue(),
      cursor: { index, row: position.row, column: position.column },
      prefix: prefix,
      completerRef: this
    })

    callback(null, completions)
  }

  getCompletions(
    editor: Ace.Editor,
    session: Ace.EditSession,
    position: Ace.Point,
    prefix: string,
    callback: Ace.CompleterCallback
  ): void {
    this.generateCompletions(editor, session, position, prefix, callback).then()
  }
}
