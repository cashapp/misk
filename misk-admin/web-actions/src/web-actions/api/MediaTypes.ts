import { distinct } from '@web-actions/utils/common';

export class MediaTypes {
  private types: string[];

  constructor(types: string[] = []) {
    this.types = distinct(types);
  }

  push(...types: string[]) {
    this.types = distinct([...this.types, ...types]);
  }

  static of(types: string[]): MediaTypes {
    return new MediaTypes(types);
  }

  hasJson(): boolean {
    return this.types.some((type) => type.startsWith('application/json'));
  }

  hasAny(): boolean {
    return this.types.some((type) => type.startsWith('*/*'));
  }

  isUnspecified(): boolean {
    return this.types.length === 0;
  }

  toString(): string {
    return this.types.join(', ');
  }
}
