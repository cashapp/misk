import { Ace } from 'ace-builds';
import CompletionProvider from '@web-actions/completion/CompletionProvider';

import Completion from '@web-actions/completion/Completion';
import AceEditor from '@web-actions/ui/AceEditor';
import {
  ActionGroup,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';

export class ContextAwareCompleter implements Ace.Completer {
  completionProvider = new CompletionProvider();

  identifierRegexps = [/[a-zA-Z0-9"]/];

  onInsert(editor: Ace.Editor, data: Ace.Completion) {
    const completion = data as Completion;
    if (completion.onSelected) {
      completion.onSelected(editor.getCursorPosition());
    }
  }

  public setSelection(selection: ActionGroup | null) {
    this.completionProvider.setSelection(selection);
  }

  markers: number[] = [];

  async generateCompletions(
    editor: Ace.Editor,
    session: Ace.EditSession,
    position: Ace.Point,
    prefix: string,
    callback: Ace.CompleterCallback,
  ) {
    const index = session.doc.positionToIndex(position);

    const completions = await this.completionProvider.getCompletions({
      editor: new AceEditor(editor),
      text: session.getValue(),
      cursor: { index, row: position.row, column: position.column },
      prefix: prefix,
      completerRef: this,
    });

    callback(null, completions);
  }

  getCompletions(
    editor: Ace.Editor,
    session: Ace.EditSession,
    position: Ace.Point,
    prefix: string,
    callback: Ace.CompleterCallback,
  ): void {
    this.generateCompletions(
      editor,
      session,
      position,
      prefix,
      callback,
    ).then();
  }
}
