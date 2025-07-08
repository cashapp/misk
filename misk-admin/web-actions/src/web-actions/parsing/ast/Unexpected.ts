import JsonValue from '@web-actions/parsing/ast/JsonValue';
import MiskType from '@web-actions/api/MiskType';
import { MiskObjectTypes } from '@web-actions/api/responseTypes';

export default class Unexpected extends JsonValue {
  value: string;

  constructor(value: string) {
    super();
    this.value = value;
  }

  onEvalCursor(index: number) {
    this.hasCursor = false;
  }

  applyTypes(type: MiskType, types: MiskObjectTypes) {}

  render(): string {
    throw new Error(`Unexpected value ${this}`);
  }
}
