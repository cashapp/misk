import React from "react"
import { Ace } from "ace-builds"
import ace from "ace-builds/src-noconflict/ace"
import "ace-builds/src-noconflict/ext-language_tools"
import { ContextAwareCompleter } from "@misk-console/ui/ContextAwareCompleter"
import { Box, IconButton, Spinner } from "@chakra-ui/react"
import { ArrowForwardIcon } from "@chakra-ui/icons"
import { CommandParser } from "@misk-console/parsing/CommandParser"
import { fetchCached } from "@misk-console/network/http"
import { MiskMetadataResponse } from "@misk-console/api/responseTypes"
import { associateBy } from "@misk-console/utils/common"

interface State {
  loading: boolean
}

interface Props {
  onResponse: (response: any) => void
}

export default class RequestEditor extends React.Component<Props, State> {
  public refEditor: HTMLElement | null = null
  public editor: Ace.Editor | null = null

  constructor(props: Props) {
    super(props)
    this.state = { loading: false }
    this.submitRequest = this.submitRequest.bind(this)
  }

  componentDidMount() {
    this.editor = ace.edit(this.refEditor!!, {
      minLines: 10,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true
    })
    const editor = this.editor!

    editor.setTheme("ace/theme/chrome")
    editor.session.setMode("ace/mode/json")
    editor.session.setUseWorker(false)
    editor.session.setUseSoftTabs(true)
    editor.session.setTabSize(2)
    editor.commands.addCommand({
      name: "executeCommand",
      bindKey: { win: "Ctrl-Enter", mac: "Command-Enter" },
      exec: this.submitRequest,
      readOnly: false
    })

    editor.completers = [new ContextAwareCompleter()]

    editor.resize()
    editor.focus()

    this.prefetchMetadata().then()
  }

  async prefetchMetadata() {
    this.setState({ loading: true })
    try {
      const actions = await fetchCached<MiskMetadataResponse>(
        `/api/web-actions/metadata`
      )
    } finally {
      this.setState({ loading: false })
    }
  }

  public updateRef(item: HTMLElement | null) {
    this.refEditor = item
  }

  async submitRequest() {
    const content = this.editor!.getValue()
    const topLevel = new CommandParser(content).parse()
    const actions = await fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`
    )
      .then(it => it.all["web-actions"].metadata)
      .then(it => associateBy(it, it => it.name))

    const path = actions[topLevel.action!.name!].pathPattern

    this.setState({ loading: true })

    const response = await fetch(path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: topLevel.action?.body?.render()
    })

    this.setState({ loading: false })

    this.props.onResponse(await response.json())
  }

  public render() {
    return (
      <Box position="relative" width="100%" height="100%">
        {this.state.loading && (
          <Box
            position="absolute"
            top="0"
            left="0"
            width="100%"
            height="100%"
            backgroundColor="rgba(0, 0, 0, 0.5)"
            display="flex"
            justifyContent="center"
            alignItems="center"
            zIndex="overlay"
          >
            <Spinner size="xl" color="white" thickness="5px" />
          </Box>
        )}
        <IconButton
          aria-label="Run"
          icon={<ArrowForwardIcon />}
          zIndex="100"
          position="absolute"
          top="2"
          right="2"
          backgroundColor="green.200"
          onClick={this.submitRequest}
        />
        <Box
          width="100%"
          height="100%"
          ref={it => this.updateRef(it)}
          id={"request-editor"}
        />
      </Box>
    )
  }
}
