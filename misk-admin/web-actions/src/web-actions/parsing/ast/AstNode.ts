import Unexpected from '@web-actions/parsing/ast/Unexpected';

export default class AstNode {
  start?: number;
  end?: number;
  hasCursor: boolean = false;
  unexpected: Unexpected[] = [];

  parent?: AstNode;

  childNodes(): AstNode[] {
    return [];
  }

  onEvalCursor(index: number) {}

  findCursor(): AstNode | null {
    for (const child of this.childNodes()) {
      if (child.hasCursor) {
        return child.findCursor();
      }
    }
    return this.hasCursor ? this : null;
  }

  find(predicate: (a: AstNode) => boolean): AstNode | null {
    if (predicate(this)) {
      return this;
    }
    for (const child of this.childNodes()) {
      const result = child.find(predicate);
      if (result) {
        return result;
      }
    }
    return null;
  }

  render(): string {
    throw new Error('Method not implemented');
  }
}
