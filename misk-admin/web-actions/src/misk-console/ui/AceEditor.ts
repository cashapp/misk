import Editor from '@misk-console/completion/Editor';
import { Ace, Range } from 'ace-builds';
import Position from '@misk-console/completion/Position';

export default class AceEditor implements Editor {
  private editor: Ace.Editor;

  constructor(editor: Ace.Editor) {
    this.editor = editor;
  }

  indexToPosition(index: number): Position {
    return this.editor.session.doc.indexToPosition(index, 0);
  }

  positionToIndex(position: Position): number {
    return this.editor.session.doc.positionToIndex(position);
  }

  moveCursorTo(row: number, column: number): void {
    this.editor.moveCursorTo(row, column);
  }

  valueAt(row: number, column: number): string {
    return this.editor.session.getTextRange(charRange(row, column));
  }

  delete(row: number, column: number): void {
    this.editor.session.remove(charRange(row, column));
  }
}

function charRange(row: number, column: number): Ace.Range {
  return new Range(row, column, row, column + 1);
}
