import {
  MiskFieldDefinition,
  MiskWebActionDefinition,
} from '@misk-console/api/responseTypes';
import { parseDocument } from '@misk-console/parsing/CommandParser';
import Obj from '@misk-console/parsing/ast/Obj';
import Field from '@misk-console/parsing/ast/Field';
import MiskType from '@misk-console/api/MiskType';
import StrLiteral from '@misk-console/parsing/ast/StrLiteral';
import PositionWithIndex from '@misk-console/completion/PositionWithIndex';
import Completion from '@misk-console/completion/Completion';
import Editor from '@misk-console/completion/Editor';
import Position from '@misk-console/completion/Position';
import CompletionBuilder from '@misk-console/completion/CompletionBuilder';
import Arr from '@misk-console/parsing/ast/Arr';

interface CompletionArgs {
  editor: Editor;
  text: string;
  cursor: PositionWithIndex;
  prefix: string;
  completerRef: any;
}

export default class CompletionProvider {
  private selection: MiskWebActionDefinition | null = null;

  private deleteTrailing(editor: Editor, curr: Position, values: string) {
    for (const c of values) {
      if (editor.valueAt(curr.row, curr.column) === c) {
        editor.delete(curr.row, curr.column);
      }
    }
  }

  async generateObjectCompletions(
    args: CompletionArgs,
    fields: MiskFieldDefinition[] | undefined,
  ): Promise<Completion[]> {
    if (fields === undefined) {
      return [];
    }
    const { editor, prefix, completerRef } = args;

    const indent = args.cursor.column - args.prefix.length;

    return fields.map<Completion>((it: MiskFieldDefinition) => {
      const type = MiskType.fromFieldDef(it);

      const builder = new CompletionBuilder();
      if (type.isArray()) {
        builder.add('[');
      }
      if (type.isObject()) {
        builder.addLine('{').add('  ').setCursorTarget().addLine().add('}');
      } else if (type.isEnum() || type.isString()) {
        builder.add('"').setCursorTarget().add('"');
      } else {
        builder.setCursorTarget();
      }
      if (type.isArray()) {
        builder.add(']');
      }

      const completion = builder.build(indent);

      return {
        value: `"${it.name}": ${completion.text},`,
        caption: `"${it.name}": ...`,
        meta: type.toRenderedString(),
        completer: completerRef,
        onSelected: (curr) => {
          this.deleteTrailing(editor, curr, '"');

          const currIndex = editor.positionToIndex(curr);
          const moveCursorTo = editor.indexToPosition(
            currIndex - completion.cursorOffset - 1,
          );
          editor.moveCursorTo(moveCursorTo.row, moveCursorTo.column);
        },
        prefix,
      };
    });
  }

  async generateLiteralCompletions(
    args: CompletionArgs,
    type: MiskType | undefined,
    inArray: boolean = false,
  ): Promise<Completion[]> {
    if (type === undefined) {
      return [];
    }

    const trailingComma = inArray ? '' : ',';

    if (type.isArray() && !inArray) {
      return [
        {
          value: '[]' + trailingComma,
          caption: '[]',
          completer: args.completerRef,
          prefix: args.prefix,
          onSelected: (curr) => {
            this.deleteTrailing(args.editor, curr, ',');

            args.editor.moveCursorTo(
              curr.row,
              curr.column - 1 - trailingComma.length,
            );
          },
        },
      ];
    } else if (type.isEnum()) {
      return type.getEnumValues().map<Completion>((it) => ({
        value: `"${it}"` + trailingComma,
        caption: `"${it}"`,
        meta: 'enum value',
        completer: args.completerRef,
        prefix: args.prefix,
        onSelected: (curr: Position) => {
          this.deleteTrailing(args.editor, curr, '",');
        },
      }));
    } else if (type.isBoolean()) {
      return ['true', 'false'].map<Completion>((it) => ({
        value: it + trailingComma,
        caption: it,
        meta: 'bool value',
        completer: args.completerRef,
        prefix: args.prefix,
        onSelected: (curr: Position) => {
          this.deleteTrailing(args.editor, curr, ',');
        },
      }));
    } else if (type.isObject()) {
      return [
        {
          value: '{}' + trailingComma,
          caption: '{}',
          completer: args.completerRef,
          prefix: args.prefix,
          onSelected: (curr) => {
            this.deleteTrailing(args.editor, curr, ',');

            args.editor.moveCursorTo(
              curr.row,
              curr.column - 1 - trailingComma.length,
            );
          },
        },
      ];
    } else if (type.isString()) {
      return [
        {
          value: '""' + trailingComma,
          caption: '""',
          completer: args.completerRef,
          prefix: args.prefix,
          onSelected: (curr) => {
            this.deleteTrailing(args.editor, curr, '",');

            args.editor.moveCursorTo(
              curr.row,
              curr.column - 1 - trailingComma.length,
            );
          },
        },
      ];
    } else {
      return [];
    }
  }

  async getCompletions(args: CompletionArgs): Promise<Completion[]> {
    const topLevel = parseDocument(args.text, args.cursor.index);
    topLevel.applyTypes(this.selection);

    const cursorNode = topLevel.findCursor();

    if (cursorNode instanceof Obj) {
      return this.generateObjectCompletions(args, cursorNode.type?.fields);
    } else if (cursorNode instanceof Field) {
      if (cursorNode.cursorInValuePosition) {
        if (cursorNode.definition) {
          return this.generateLiteralCompletions(
            args,
            MiskType.fromFieldDef(cursorNode.definition),
          );
        } else {
          return [];
        }
      } else {
        return this.generateObjectCompletions(
          args,
          cursorNode.parent?.type?.fields,
        );
      }
    } else if (cursorNode instanceof Arr) {
      return this.generateLiteralCompletions(args, cursorNode.type, true);
    } else if (cursorNode instanceof StrLiteral) {
      return this.generateLiteralCompletions(
        args,
        cursorNode.type,
        cursorNode.parent instanceof Arr,
      );
    }
    return [];
  }

  setSelection(selection: MiskWebActionDefinition | null) {
    this.selection = selection;
  }
}
