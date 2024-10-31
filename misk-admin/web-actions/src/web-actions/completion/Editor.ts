import Position from '@web-actions/completion/Position';

export default interface Editor {
  moveCursorTo(row: number, column: number): void;
  valueAt(row: number, column: number): string;
  delete(row: number, column: number): void;
  indexToPosition(index: number): Position;
  positionToIndex(position: Position): number;
}
