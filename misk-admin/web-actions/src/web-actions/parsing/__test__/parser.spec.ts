import { CommandParser } from '@web-actions/parsing/CommandParser';
import StrLiteral from '@web-actions/parsing/ast/StrLiteral';
import Obj from '@web-actions/parsing/ast/Obj';
import Arr from '@web-actions/parsing/ast/Arr';
import Unexpected from '@web-actions/parsing/ast/Unexpected';

function parser(text: string): CommandParser {
  return new CommandParser(text);
}

test('numbers', () => {
  expect(parser('0.9').parseNum()?.value).toEqual('0.9');
  expect(parser('1000').parseNum()?.value).toEqual('1000');
  expect(parser('-1').parseNum()?.value).toEqual('-1');
});

test('strings', () => {
  expect(parser('"hello"').parseStr()?.value).toEqual('hello');
  expect(parser('"hello\\"world"').parseStr()?.value).toEqual('hello\\"world');
  expect(parser('"foo bar"').parseStr()?.value).toEqual('foo bar');
});

test('nested objects', () => {
  const result = parser(`{
    "foo": {
      "bar": "baz"
    }
  } 
  `).parseObj()!;

  expect(result.fields[0].name?.value).toBe('foo');
  expect(result.fields[0].value?.as<Obj>()?.fields[0].name?.value).toBe('bar');
  expect(
    result.fields[0].value?.as<Obj>()?.fields[0].value?.as<StrLiteral>()?.value,
  ).toBe('baz');
});

test('object error handling', () => {
  const result = parser(`{ "bar": "baz" ! }`).parseObj();

  expect(result?.fields.length).toEqual(1);
  expect(result?.unexpected.length).toEqual(1);
  expect(result?.unexpected[0].value).toEqual('!');
});

test('array', () => {
  const result = parser(`["a", {}, [],]`).parseArr();

  expect(result?.values.length).toEqual(3);
  expect(result?.values[0].as<StrLiteral>()?.value).toEqual('a');
  expect(result?.values[1].as<Obj>()?.fields).toEqual([]);
  expect(result?.values[2].as<Arr>()?.values).toEqual([]);
});

test('array error', () => {
  const result = parser(`["a" ! ]`).parseArr();

  expect(result?.values.length).toEqual(2);
  expect(result?.values[0].as<StrLiteral>()?.value).toEqual('a');
  expect(result?.values[1].as<Unexpected>()?.value).toEqual('!');
});
