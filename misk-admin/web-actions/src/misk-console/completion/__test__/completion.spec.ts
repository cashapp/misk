import CompletionProvider from "@misk-console/completion/CompletionProvider"
import FakeEditor from "@misk-console/completion/__test__/FakeEditor"
import {
  MyAction,
} from '@misk-console/completion/__test__/FakeMetadataClient';
import { givenEditor } from "@misk-console/completion/__test__/CompletionTester"
import { MiskWebActionDefinition } from '@misk-console/api/responseTypes';

export function providerWithAction(action: MiskWebActionDefinition): CompletionProvider {
  const p =  new CompletionProvider()
  p.setSelection(action)
  return p;
}

test("completion from object body", async () => {
  const editor = new FakeEditor()
  editor.completions = providerWithAction(MyAction)

  editor.setTextWithCursor(`
    {
      |
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`)
})

test("completion from object body within field name", async () => {
  const editor = new FakeEditor()
  editor.completions = providerWithAction(MyAction)
  editor.setTextWithCursor(`
    {
      "|"
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`)
})

test("completion from object body within field value", async () => {
  const editor = new FakeEditor()
  editor.completions = providerWithAction(MyAction)
  editor.setTextWithCursor(`
    {
      "text": |
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`)
})

test("completion from literal in quotes", async () => {
  const editor = new FakeEditor()
  editor.completions = providerWithAction(MyAction)
  editor.setTextWithCursor(`
    {
      "text": "|",
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    {
      "text": "|",
    }`)
})

test("completion from enum", async () => {
  const startingWith = [
    // `
    // {
    //   "enum": "|",
    // }`, `
    // {
    //   "enum": |,
    // }`,
    `
    {
      "enum": |
    }`
  ]

  for (const start of startingWith) {
    await givenEditor(start).selectingCompletion(0).shouldResultIn(`
    {
      "enum": "A",|
    }`)
  }
})

test("completion for object", async () => {
  await givenEditor(`
    {
      |
    }`).selectingCompletionMatching(c => c.value.includes("object"))
    .shouldResultIn(`
    {
      "object": {
        |
      },
    }`)
})

test("completion for array", async () => {
  await givenEditor(`
    {
      "s-array": ["|"],
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": ["|"],
    }`)

  await givenEditor(`
    {
      |
    }`).selectingCompletionMatching(c => c.value.includes("s-array"))
    .shouldResultIn(`
    {
      "s-array": ["|"],
    }`)

  await givenEditor(`
    {
      "s-array": |,
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": [|],
    }`)

  await givenEditor(`
    {
      "s-array": ["", |],
    }`).selectingCompletion(0).shouldResultIn(`
    {
      "s-array": ["", "|"],
    }`)
})
