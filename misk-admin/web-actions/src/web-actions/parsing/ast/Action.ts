import AstNode from '@web-actions/parsing/ast/AstNode';

import Obj from '@web-actions/parsing/ast/Obj';
import Unexpected from '@web-actions/parsing/ast/Unexpected';
import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';
import MiskType from '@web-actions/api/MiskType';

export default class Action extends AstNode {
  name: string;
  body: Obj | null;
  actionDefinition?: MiskWebActionDefinition;

  constructor(name: string, body?: Obj | Unexpected | null) {
    super();
    this.name = name;
    if (body instanceof Obj) {
      this.body = body ?? null;
      if (body) {
        this.body.parent = this;
      }
    } else if (body instanceof Unexpected) {
      this.unexpected = [body];
      this.body = null;
    } else {
      this.body = null;
    }
  }

  childNodes(): AstNode[] {
    return this.body ? [this.body] : [];
  }

  applyTypes(actionDefinition: MiskWebActionDefinition) {
    this.actionDefinition = actionDefinition;
    if (this.body) {
      const type = actionDefinition.types[actionDefinition.requestType];
      if (type) {
        this.body.applyTypes(
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
