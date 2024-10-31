import React from 'react';
import { Ace } from 'ace-builds';
import ace from 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/ext-language_tools';
import { ContextAwareCompleter } from '@web-actions/ui/ContextAwareCompleter';
import { Box, IconButton, Spinner } from '@chakra-ui/react';
import { ArrowForwardIcon } from '@chakra-ui/icons';
import { CommandParser } from '@web-actions/parsing/CommandParser';
import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';
import { EndpointSelectionCallbacks } from '@web-actions/ui/EndpointSelection';
import { randomToken } from '@web-actions/utils/common';

interface State {
  loading: boolean;
}

interface Props {
  endpointSelectionCallbacks: EndpointSelectionCallbacks;
  onResponse: (response: string) => void;
}

export default class RequestEditor extends React.Component<Props, State> {
  private id = randomToken();

  public refEditor: HTMLElement | null = null;
  public editor: Ace.Editor | null = null;

  private readonly completer;
  private selectedAction: MiskWebActionDefinition | null = null;

  constructor(props: Props) {
    super(props);
    this.state = { loading: false };
    this.submitRequest = this.submitRequest.bind(this);

    this.completer = new ContextAwareCompleter();
  }

  componentDidMount() {
    this.editor = ace.edit(this.refEditor, {
      minLines: 10,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
    });
    const editor = this.editor!;

    editor.setTheme('ace/theme/chrome');
    editor.session.setMode('ace/mode/json');
    editor.session.setUseWorker(false);
    editor.session.setUseSoftTabs(true);
    editor.session.setTabSize(2);
    editor.commands.addCommand({
      name: 'executeCommand',
      bindKey: { win: 'Ctrl-Enter', mac: 'Command-Enter' },
      exec: this.submitRequest,
      readOnly: false,
    });

    editor.completers = [this.completer];

    editor.resize();

    this.props.endpointSelectionCallbacks.push((value) => {
      this.completer.setSelection(value);
      this.selectedAction = value;
      editor.clearSelection();
      editor.setValue('{\n  \n}', -1);
      editor.moveCursorTo(1, 2);
      editor.focus();
    });
  }

  public updateRef(item: HTMLElement | null) {
    this.refEditor = item;
  }

  async submitRequest() {
    try {
      const content = this.editor!.getValue();
      const topLevel = new CommandParser(content).parse();

      const selection = this.selectedAction;
      if (selection == null) {
        return;
      }

      const path = selection.pathPattern;

      this.setState({ loading: true });

      const response = await fetch(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: topLevel?.render(),
      });

      let responseText = await response.text();
      try {
        responseText = JSON.stringify(JSON.parse(responseText), null, 2);
      } catch {
        // ignore
      }

      this.props.onResponse(responseText);
    } finally {
      this.setState({ loading: false });
    }
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
          ref={(it) => this.updateRef(it)}
          id={'request-editor'}
        />
      </Box>
    );
  }
}
