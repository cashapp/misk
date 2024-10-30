import { MiskFieldDefinition } from '@misk-console/api/responseTypes';
import {
  removePrefix,
  removeSurrounding,
  required,
} from '@misk-console/utils/common';

const primitives = ['string', 'bytestring', 'int', 'long', 'double', 'boolean'];

export default class MiskType {
  readonly type: string;
  private readonly lowerCaseType: string;
  private readonly repeated: boolean;

  constructor(type: string, repeated: boolean) {
    this.type = type;
    this.lowerCaseType = type.toLowerCase();
    this.repeated = repeated;
  }

  isArray(): boolean {
    return this.repeated;
  }

  isPrimitive(): boolean {
    return primitives.includes(this.lowerCaseType);
  }

  isEnum(): boolean {
    return this.lowerCaseType.startsWith('enum<');
  }

  isString(): boolean {
    return this.lowerCaseType.startsWith('string');
  }

  isBoolean(): boolean {
    return this.lowerCaseType === 'boolean';
  }

  isObject(): boolean {
    return !this.isPrimitive() && !this.isEnum();
  }

  getEnumValues(): string[] {
    required(this.isEnum(), 'Not an enum type');
    return removeSurrounding('Enum<', '>', this.type).split(',').slice(1);
  }

  isOneOf(...type: string[]): boolean {
    return type.includes(this.lowerCaseType);
  }

  toRenderedString() {
    const type = this.type;
    const arraySuffix = this.repeated ? '[]' : '';
    if (type.startsWith('Enum<')) {
      return (
        this.removeNamespace(removePrefix('Enum<', type).split(',')[0]) +
        arraySuffix
      );
    } else {
      return this.removeNamespace(type) + arraySuffix;
    }
  }

  isNumber() {
    return ['int', 'long', 'double'].includes(this.lowerCaseType);
  }

  arrayType(): MiskType {
    if (this.repeated) {
      return new MiskType(this.type, false);
    } else {
      throw new Error('Not an array type');
    }
  }

  private removeNamespace(type: string) {
    const parts = type.split('.');
    return parts[parts.length - 1];
  }

  static fromFieldDef(it: MiskFieldDefinition): MiskType {
    return new MiskType(it.type, it.repeated);
  }
}
