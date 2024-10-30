import AstNode from '@misk-console/parsing/ast/AstNode';

import JsonValue from '@misk-console/parsing/ast/JsonValue';
import Unexpected from '@misk-console/parsing/ast/Unexpected';
import MiskType from '@misk-console/api/MiskType';
import { MiskObjectTypes } from '@misk-console/api/responseTypes';

export default class Arr extends JsonValue {
  values: JsonValue[];
  type?: MiskType;

  constructor(values: JsonValue[], unexpected: Unexpected[]) {
    super();
    this.values = values;
    this.unexpected = unexpected;

    for (const value of values) {
      value.parent = this;
    }
  }

  childNodes(): AstNode[] {
    return this.values;
  }

  render(): string {
    return `[${this.values.map((it) => it.render()).join(', ')}]`;
  }

  applyTypes(type: MiskType, types: MiskObjectTypes) {
    this.type = type;
    for (const value of this.values) {
      value.applyTypes(type.arrayType(), types);
    }
  }
}
