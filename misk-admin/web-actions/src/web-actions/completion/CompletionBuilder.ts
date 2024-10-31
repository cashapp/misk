import Position from '@web-actions/completion/Position';

interface RenderedCompletion {
  text: string;
  cursorOffset: number;
}

export default class CompletionBuilder {
  lines: string[] = [''];
  cursorTarget?: Position;

  private currentRow(): number {
    return this.lines.length - 1;
  }

  addLine(value?: string): CompletionBuilder {
    if (value) {
      this.add(value);
    }
    this.lines.push('');
    return this;
  }

  add(value: string): CompletionBuilder {
    this.lines[this.currentRow()] += value;
    return this;
  }

  setCursorTarget(): CompletionBuilder {
    this.cursorTarget = {
      row: this.currentRow(),
      column: this.lines[this.currentRow()].length,
    };
    return this;
  }

  build(indent: number): RenderedCompletion {
    const indentString = ' '.repeat(indent);
    const cursor = this.cursorTarget!;

    let cursorIndex = 0;

    let rendered = '';
    for (let i = 0; i < this.lines.length; i++) {
      const line = this.lines[i];
      if (i === 0) {
        rendered += line;
      } else {
        rendered += `\n${indentString}${line}`;
      }

      if (i === cursor.row) {
        cursorIndex = rendered.length - (line.length - cursor.column);
      }
    }
    return {
      text: rendered,
      cursorOffset: rendered.length - cursorIndex,
    };
  }
}
