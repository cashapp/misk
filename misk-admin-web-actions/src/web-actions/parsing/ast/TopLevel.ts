import AstNode from '@web-actions/parsing/ast/AstNode';
import Unexpected from '@web-actions/parsing/ast/Unexpected';
import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';
import Obj from '@web-actions/parsing/ast/Obj';
import MiskType from '@web-actions/api/MiskType';

export default class TopLevel extends AstNode {
  obj: Obj | Unexpected | null;

  constructor(obj: Obj | Unexpected | null) {
    super();
    this.obj = obj;
    this.hasCursor = true;

    if (this.obj) {
      this.obj.parent = this;
    }
  }

  childNodes(): AstNode[] {
    return this.obj ? [this.obj] : [];
  }

  render(): string {
    return this.obj?.render() ?? '';
  }

  firstError(): Unexpected | null {
    return (
      (this.findAll(
        (node): node is Unexpected => node instanceof Unexpected,
      )[0] as Unexpected) ?? null
    );
  }

  applyTypes(actionDefinition: MiskWebActionDefinition | null) {
    if (this.obj) {
      if (actionDefinition) {
        const type = actionDefinition.types[actionDefinition.requestType];
        if (type) {
          this.obj.applyTypes(
            new MiskType(actionDefinition.requestType, false),
            actionDefinition.types,
          );
        } else {
          console.warn(
            'Cannot find request type for',
            actionDefinition.requestType,
          );
        }
      }
    }
  }
}
