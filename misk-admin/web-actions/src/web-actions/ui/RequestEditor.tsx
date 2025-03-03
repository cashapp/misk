import React from 'react';
import { Ace } from 'ace-builds';
import ace from 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/ext-language_tools';
import { ContextAwareCompleter } from '@web-actions/ui/ContextAwareCompleter';
import { Box, IconButton, Spinner } from '@chakra-ui/react';
import { CopyIcon } from '@chakra-ui/icons';
import { CommandParser } from '@web-actions/parsing/CommandParser';
import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';
import { EndpointSelectionCallbacks } from '@web-actions/ui/EndpointSelection';
import { triggerCompletionDialog } from '@web-actions/ui/AceEditor';

interface State {
  loading: boolean;
  isDisabled: boolean;
}

interface Props {
  endpointSelectionCallbacks: EndpointSelectionCallbacks;
  onResponse: (response: string) => void;
}

export default class RequestEditor extends React.Component<Props, State> {
  public refEditor: HTMLElement | null = null;
  public editor: Ace.Editor | null = null;

  private readonly completer;
  private selectedAction: MiskWebActionDefinition | null = null;
  private path: string = '';

  constructor(props: Props) {
    super(props);
    this.state = { loading: false, isDisabled: false };
    this.submitRequest = this.submitRequest.bind(this);
    this.copyToClipboard = this.copyToClipboard.bind(this);

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
    editor.completers = [this.completer];

    editor.commands.removeCommand('showSettingsMenu');
    editor.commands.removeCommand('gotoline');

    editor.resize();
  }

  public setPath(path: string | undefined) {
    this.path = path ?? '';
  }

  public setEndpointSelection(action: MiskWebActionDefinition | undefined) {
    this.completer.setSelection(action ?? null);
    this.selectedAction = action ?? null;
    this.path = action?.pathPattern ?? '';
    this.editor?.clearSelection();

    if (action?.httpMethod === 'GET') {
      this.setState({
        ...this.state,
        isDisabled: true,
      });
      this.editor?.setReadOnly(true);
      this.editor?.setValue('', -1);
    } else {
      this.setState({
        ...this.state,
        isDisabled: false,
      });

      this.editor?.setReadOnly(false);
      this.editor?.setValue('{\n  \n}', -1);
      this.editor?.moveCursorTo(1, 2);
      this.editor?.focus();

      triggerCompletionDialog(this.editor);
    }
  }

  public focusEditor() {
    this.editor?.focus();
  }

  public updateRef(item: HTMLElement | null) {
    this.refEditor = item;
  }

  async submitRequest() {
    try {
      const selection = this.selectedAction;
      if (selection == null) {
        return;
      }

      this.setState({ loading: true });

      let response: Response;
      if (selection.httpMethod === 'GET') {
        response = await fetch(this.path, { method: 'GET' });
      } else {
        const content = this.editor!.getValue();
        const topLevel = new CommandParser(content).parse();

        this.setState({ loading: true });
        response = await fetch(this.path, {
          method: selection.httpMethod,
          headers: {
            'Content-Type': 'application/json',
          },
          body: topLevel?.render(),
        });
      }

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

  async copyToClipboard() {
    try {
      const content = this.editor!.getValue();
      const normalizedJson = new CommandParser(content).parse()?.render();
      await navigator.clipboard.writeText(normalizedJson);
    } catch (err) {
      console.error('Failed to copy with error:', err);
    }
  }

  public render() {
    return (
      <Box position="relative" width="100%" height="100%">
        {(this.state.loading || this.state.isDisabled) && (
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
            zIndex="100"
          >
            {this.state.loading ? (
              <Spinner size="xl" color="white" thickness="5px" />
            ) : (
              this.state.isDisabled && (
                <Box color="white" fontSize="lg" textAlign="center" padding="4">
                  Request body not supported for GET requests
                </Box>
              )
            )}
          </Box>
        )}

        <IconButton
          aria-label="Copy"
          zIndex="100"
          position="absolute"
          top="4"
          right="4"
          colorScheme={'blackAlpha'}
          onClick={this.copyToClipboard}
        >
          <CopyIcon />
        </IconButton>
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
