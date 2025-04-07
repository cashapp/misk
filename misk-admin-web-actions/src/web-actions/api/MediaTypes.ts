export class MediaTypes {
  private readonly types: string[];

  constructor(types: string[] = []) {
    this.types = types;
  }

  push(...types: string[]) {
    this.types.push(...types);
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
}
