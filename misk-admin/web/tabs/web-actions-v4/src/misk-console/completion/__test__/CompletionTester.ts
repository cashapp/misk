import FakeEditor from "@misk-console/completion/__test__/FakeEditor"
import CompletionProvider from "@misk-console/completion/CompletionProvider"
import FakeMetadataClient, {
  FakeObjectMetadataClient
} from "@misk-console/completion/__test__/FakeMetadataClient"
import Completion from "@misk-console/completion/Completion"

class CompletionTester {
  private editor: FakeEditor
  private action?: () => Promise<void>

  constructor(editor: FakeEditor) {
    this.editor = editor
  }

  selectingCompletion(index: number): CompletionTester {
    this.action = async () => {
      const completions = await this.editor.getCompletions()
      const completion = completions[index]
      if (!completion) {
        throw new Error(
          `No completion available at ${index} given ${completions}`
        )
      }
      completion.select()
    }
    return this
  }

  selectingCompletionMatching(
    predicate: (c: Completion) => boolean
  ): CompletionTester {
    this.action = async () => {
      const completions = await this.editor.getCompletions()
      const completion = completions.find(c => predicate(c.completion))
      if (!completion) {
        throw new Error(`No completion available given ${completions}`)
      }
      completion.select()
    }
    return this
  }

  async shouldResultIn(text: string) {
    await this.action!()
    expect(this.editor.textWithCursor()).toBe(text)
  }
}

export function givenEditor(text: string): CompletionTester {
  const editor = new FakeEditor()
  editor.completions = new CompletionProvider(new FakeMetadataClient())
  editor.setTextWithCursor(text)
  return new CompletionTester(editor)
}
