import CompletionProvider from '@web-actions/completion/CompletionProvider';
import FakeEditor from '@web-actions/completion/__test__/FakeEditor';
import { MyActionGroup } from '@web-actions/completion/__test__/FakeMetadataClient';
import { givenEditor } from '@web-actions/completion/__test__/CompletionTester';
import { ActionGroup } from '@web-actions/api/responseTypes';

export function providerWithAction(action: ActionGroup): CompletionProvider {
  const p = new CompletionProvider();
  p.setSelection(action);
  return p;
}

test('completion from object body', async () => {
  const editor = new FakeEditor();
  editor.completions = providerWithAction(MyActionGroup);

  editor.setTextWithCursor(`
    {
      |
    }`);

  await editor.getCompletions().then((it) => it[0].select());
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`);
});

test('completion from object body within field name', async () => {
  const editor = new FakeEditor();
  editor.completions = providerWithAction(MyActionGroup);
  editor.setTextWithCursor(`
    {
      "|"
    }`);

  await editor.getCompletions().then((it) => it[0].select());
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`);
});

test('completion from object body within field value', async () => {
  const editor = new FakeEditor();
  editor.completions = providerWithAction(MyActionGroup);
  editor.setTextWithCursor(`
    {
      "text": |
    }`);

  await editor.getCompletions().then((it) => it[0].select());
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`);
});

test('completion from literal in quotes', async () => {
  const editor = new FakeEditor();
  editor.completions = providerWithAction(MyActionGroup);
  editor.setTextWithCursor(`
    {
      "text": "|",
    }`);

  await editor.getCompletions().then((it) => it[0].select());
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`);
});

test('completion from enum', async () => {
  const startingWith = [
    `
    {
      "enum": |
    }`,
  ];

  for (const start of startingWith) {
    await givenEditor(start).selectingCompletion(0).shouldResultIn(`
    {
      "enum": "A",|
    }`);
  }
});

test('completion for object', async () => {
  await givenEditor(`
    {
      |
    }`).selectingCompletionMatching((c) => c.value.includes('object'))
    .shouldResultIn(`
    {
      "object": {
        |
      },
    }`);
});

test('completion for array', async () => {
  await givenEditor(`
    {
      "s-array": ["|"],
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": ["|"],
    }`);

  await givenEditor(`
    {
      |
    }`).selectingCompletionMatching((c) => c.value.includes('s-array'))
    .shouldResultIn(`
    {
      "s-array": ["|"],
    }`);

  await givenEditor(`
    {
      "s-array": |,
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": [|],
    }`);

  await givenEditor(`
    {
      "s-array": ["", |],
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": ["", "|"],
    }`);
});
