import JsonValue from 'src/web-actions/parsing/ast/JsonValue';
import MiskType from 'src/web-actions/api/MiskType';
import { MiskObjectTypes } from 'src/web-actions/api/responseTypes';

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
