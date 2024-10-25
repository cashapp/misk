import CompletionProvider from "@misk-console/completion/CompletionProvider"
import FakeEditor from "@misk-console/completion/__test__/FakeEditor"
import FakeMetadataClient, {
  FakeObjectMetadataClient
} from "@misk-console/completion/__test__/FakeMetadataClient"
import { givenEditor } from "@misk-console/completion/__test__/CompletionTester"

test("completion from object body", async () => {
  const editor = new FakeEditor()
  editor.completions = new CompletionProvider(new FakeMetadataClient())
  editor.setTextWithCursor(`
    MyAction {
      |
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    MyAction {
      "text": "|",
    }`)
})

test("completion from object body within field name", async () => {
  const editor = new FakeEditor()
  editor.completions = new CompletionProvider(new FakeMetadataClient())
  editor.setTextWithCursor(`
    MyAction {
      "|"
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    MyAction {
      "text": "|",
    }`)
})

test("completion from object body within field value", async () => {
  const editor = new FakeEditor()
  editor.completions = new CompletionProvider(new FakeMetadataClient())
  editor.setTextWithCursor(`
    MyAction {
      "text": |
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    MyAction {
      "text": "|",
    }`)
})

test("completion from literal in quotes", async () => {
  const editor = new FakeEditor()
  editor.completions = new CompletionProvider(new FakeMetadataClient())
  editor.setTextWithCursor(`
    MyAction {
      "text": "|",
    }`)

  await editor.getCompletions().then(it => it[0].select())
  expect(editor.textWithCursor()).toBe(`
    MyAction {
      "text": "|",
    }`)
})

test("completion from enum", async () => {
  const startingWith = [
    // `
    // MyAction {
    //   "enum": "|",
    // }`, `
    // MyAction {
    //   "enum": |,
    // }`,
    `
    MyAction {
      "enum": |
    }`
  ]

  for (const start of startingWith) {
    await givenEditor(start).selectingCompletion(0).shouldResultIn(`
    MyAction {
      "enum": "A",|
    }`)
  }
})

test("completion for object", async () => {
  await givenEditor(`
    MyAction {
      |
    }`).selectingCompletionMatching(c => c.value.includes("object"))
    .shouldResultIn(`
    MyAction {
      "object": {
        |
      },
    }`)
})

test("completion for array", async () => {
  await givenEditor(`
    MyAction {
      "s-array": ["|"],
    }`).selectingCompletion(0).shouldResultIn(`
    MyAction {
      "s-array": ["|"],
    }`)

  await givenEditor(`
    MyAction {
      |
    }`).selectingCompletionMatching(c => c.value.includes("s-array"))
    .shouldResultIn(`
    MyAction {
      "s-array": ["|"],
    }`)

  await givenEditor(`
    MyAction {
      "s-array": |,
    }`).selectingCompletion(0).shouldResultIn(`
    MyAction {
      "s-array": [|],
    }`)

  await givenEditor(`
    MyAction {
      "s-array": ["", |],
    }`).selectingCompletion(0).shouldResultIn(`
    MyAction {
      "s-array": ["", "|"],
    }`)
})
