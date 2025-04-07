import PositionWithIndex from '@web-actions/completion/PositionWithIndex';
import Position from '@web-actions/completion/Position';

export function matchNextWhitespace(
  str: string,
  startPos: number,
): number | null {
  const match = str.substring(startPos).match(/^\s+/);
  if (match) {
    return match[0].length;
  }
  return null;
}

export function matchNextNonWhitespace(
  str: string,
  startPos: number,
): string | null {
  const match = str.substring(startPos).match(/^\S+/);
  if (match) {
    return match[0];
  }
  return null;
}

export function associateBy<T, K extends PropertyKey>(
  array: T[],
  keySelector: (item: T) => K,
): Record<K, T> {
  return array.reduce(
    (acc, curr) => {
      const key = keySelector(curr);
      acc[key] = curr;
      return acc;
    },
    {} as Record<K, T>,
  );
}

export function findPosition(
  text: string,
  toFind: string,
): PositionWithIndex | null {
  let index = 0;
  let row = 0;
  let column = 0;
  while (index < text.length) {
    if (text.startsWith(toFind, index)) {
      return { row, column, index };
    } else if (text[index] === '\n') {
      row++;
      column = 0;
    } else {
      column++;
    }
    index++;
  }
  return null;
}

export function findPositionByIndex(text: string, index: number): Position {
  let row = 0;
  let column = 0;
  let currentIndex = 0;
  while (currentIndex < index) {
    if (text[currentIndex] === '\n') {
      row++;
      column = 0;
    } else {
      column++;
    }
    currentIndex++;
  }
  return { row, column };
}

export function findIndexByPosition(text: string, position: Position): number {
  let index = 0;
  const parts = text.split('\n');
  for (let i = 0; i < position.row; i++) {
    index += parts[i].length + 1;
  }
  index += position.column;
  return index;
}

export function insertString(
  original: string,
  insert: string,
  index: number,
): string {
  if (index < 0 || index > original.length) {
    throw new Error('Index out of bounds');
  }
  return original.slice(0, index) + insert + original.slice(index);
}

export function removePrefix(prefix: string, value: string) {
  return value.slice(prefix.length);
}

export function removeSurrounding(
  prefix: string,
  suffix: string,
  value: string,
) {
  return value.slice(prefix.length, value.length - suffix.length);
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function expectFromRecord<K extends string | number, V>(
  record: Record<K, V>,
  key: K,
) {
  const found = record[key];
  if (!found) {
    throw new Error(`Expected key ${key} in ${Object.keys(record)}`);
  }
  return found;
}

export function required(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

export function randomToken(): string {
  const length = 8;
  const array = new Uint8Array(length);
  window.crypto.getRandomValues(array);
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, length);
}

export function parseNull(s: string | null): string | null {
  return s === 'null' ? null : s;
}
