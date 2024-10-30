import AstNode from '@misk-console/parsing/ast/AstNode';
import Unexpected from '@misk-console/parsing/ast/Unexpected';
import { MiskWebActionDefinition } from '@misk-console/api/responseTypes';
import Obj from '@misk-console/parsing/ast/Obj';
import MiskType from '@misk-console/api/MiskType';

export default class TopLevel extends AstNode {
  obj: Obj | null;

  constructor(obj: Obj | Unexpected | null) {
    super();
    if (obj instanceof Obj) {
      this.obj = obj;
    } else if (obj instanceof Unexpected) {
      this.unexpected = [obj];
      this.obj = null;
    } else {
      this.obj = null;
    }
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
