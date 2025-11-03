import MiskType from '@web-actions/api/MiskType';
import JsonValue from '@web-actions/parsing/ast/JsonValue';

export default class NumLiteral extends JsonValue {
  value?: string;
  type?: MiskType;

  constructor(value: string) {
    super();
    this.value = value;
  }

  applyTypes(type: MiskType) {
    this.type = type;
  }

  render(): string {
    return this.value!;
  }
}
