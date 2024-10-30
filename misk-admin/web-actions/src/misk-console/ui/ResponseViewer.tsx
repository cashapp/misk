import React, { Dispatch, SetStateAction } from 'react';
import { Ace } from "ace-builds"
import ace from "ace-builds/src-noconflict/ace"
import "ace-builds/src-noconflict/ext-language_tools"
import { Box } from "@chakra-ui/react"
import { ViewState } from 'src/viewState';

interface Props {
  viewState: ViewState,
  setViewState: Dispatch<SetStateAction<ViewState>>;
}

export default class ResponseViewer extends React.Component<Props> {
  public refEditor: HTMLElement | null = null
  public editor: Ace.Editor | null = null

  componentDidMount() {
    this.editor = ace.edit(this.refEditor!!, {
      theme: "ace/theme/textmate",
      mode: "ace/mode/json",
      minLines: 10,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
      highlightActiveLine: false,
      highlightGutterLine: false
    })
    const editor = this.editor!
    editor.setReadOnly(true)
    editor.resize()
  }

  public updateRef(item: HTMLDivElement | null) {
    this.refEditor = item
  }

  public render() {
    this.editor?.setValue(this.props.viewState.response || '', -1)

    return (
      <Box
        id={"response-viewer"}
        width="100%"
        height="100%"
        ref={it => this.updateRef(it)}
      />
    )
  }
}
