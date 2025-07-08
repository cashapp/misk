import React from 'react';
import { Ace } from 'ace-builds';
import ace from 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/ext-language_tools';
import { ContextAwareCompleter } from '@web-actions/ui/ContextAwareCompleter';
import { Box, IconButton, Spinner } from '@chakra-ui/react';
import { CopyIcon } from '@chakra-ui/icons';
import { parseDocument } from '@web-actions/parsing/CommandParser';
import { MiskFieldDefinition, MiskRoute } from '@web-actions/api/responseTypes';
import { appEvents, APP_EVENTS } from '@web-actions/events/appEvents';

interface Props {
  display: string;
  isCallable: boolean;
  loading: boolean;
}

interface State {
  isDisabled: boolean;
}

export default class RequestEditor extends React.Component<Props, State> {
  public refEditor: HTMLElement | null = null;
  public editor: Ace.Editor | null = null;
  private errorMarkers: number[] = [];

  private readonly completer;

  constructor(props: Props) {
    super(props);
    this.state = { isDisabled: false };

    this.copyToClipboard = this.copyToClipboard.bind(this);
    this.handleChange = this.handleChange.bind(this);
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

    editor.session.on('change', this.handleChange);
    editor.resize();
  }

  componentWillUnmount() {
    if (this.editor) {
      this.editor.session.off('change', this.handleChange);
    }
  }

  private handleChange() {
    const editor = this.editor;
    if (!editor) return;

    const session = editor.session;

    this.errorMarkers.forEach((markerId) => {
      session.removeMarker(markerId);
    });
    this.errorMarkers = [];

    const ast = parseDocument(editor.getValue());
    const unexpectedNodes = ast?.firstError();
    if (unexpectedNodes !== null) {
      const node = unexpectedNodes;
      if (node.start !== undefined && node.end !== undefined) {
        const startPoint = session.doc.indexToPosition(node.start);
        const endPoint = session.doc.indexToPosition(node.end);
        const markerId = session.addMarker(
          new ace.Range(
            startPoint.row,
            startPoint.column,
            endPoint.row,
            endPoint.column,
          ),
          'error-marker',
          'text',
        );
        if (markerId !== undefined) {
          this.errorMarkers.push(markerId);
        }
      }
    }
  }

  private generateDefaultValue(type: string, field: MiskFieldDefinition): any {
    if (field.repeated) {
      return [];
    }

    switch (type.toLowerCase()) {
      case 'string':
        return '';
      case 'int':
      case 'long':
      case 'float':
      case 'double':
        return 0;
      case 'boolean':
        return false;
      default:
        return '';
    }
  }

  private generateRequestBody(action: MiskRoute): string {
    if (!action.requestMediaTypes.hasJson()) {
      return '';
    }

    if (!action.requestType || !action.types) {
      return '{\n  \n}';
    }

    const requestType = action.types[action.requestType];
    if (!requestType) {
      return '{\n  \n}';
    }

    const body: Record<string, any> = {};

    requestType.fields.forEach((field) => {
      const fieldType = field.type;
      if (action.types[fieldType]) {
        if (field.repeated) {
          body[field.name] = [];
        } else {
          const nestedBody: Record<string, any> = {};
          action.types[fieldType].fields.forEach((nestedField) => {
            nestedBody[nestedField.name] = this.generateDefaultValue(
              nestedField.type,
              nestedField,
            );
          });
          body[field.name] = nestedBody;
        }
      } else {
        body[field.name] = this.generateDefaultValue(fieldType, field);
      }
    });

    const jsonString = JSON.stringify(body, null, 2);
    return jsonString.replace(/\n}$/, ',\n  \n}');
  }

  public setEndpointSelection(action: MiskRoute | undefined) {
    this.completer.setSelection(action ?? null);
    this.editor?.clearSelection();

    if (action === undefined) {
      return;
    }

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
      const requestBody = this.generateRequestBody(action!);
      this.editor?.setValue(requestBody, -1);
      const lines = this.editor?.session.getLength();
      if (lines) {
        this.editor?.gotoLine(lines - 1, 2, false);
      }
      this.editor?.focus();
    }
  }

  public focusEditor() {
    this.editor?.focus();
  }

  public updateRef(item: HTMLElement | null) {
    this.refEditor = item;
  }

  async copyToClipboard() {
    try {
      const editor = this.editor;
      if (!editor) return;

      const ast = parseDocument(editor.getValue());
      if (ast?.firstError() !== null) {
        appEvents.emit(APP_EVENTS.SHOW_ERROR_TOAST);
        return;
      }

      const normalizedJson = ast?.render();
      await navigator.clipboard.writeText(normalizedJson);
    } catch (err) {
      if (window.isSecureContext) {
        console.error('Failed to copy with error:', err);
      } else {
        console.error(
          'Copy-to-clipboard functionality only works for https secure contexts, error:',
          err,
        );
      }
    }
  }

  public render() {
    return (
      <Box
        display={this.props.display}
        position="relative"
        width="100%"
        height="100%"
      >
        {(this.props.loading ||
          this.state.isDisabled ||
          !this.props.isCallable) && (
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
            {this.props.loading ? (
              <Spinner size="xl" color="white" thickness="5px" />
            ) : this.state.isDisabled ? (
              <Box color="white" fontSize="lg" textAlign="center" padding="4">
                Request body not supported for GET requests.
              </Box>
            ) : (
              !this.props.isCallable && (
                <Box color="white" fontSize="lg" textAlign="center" padding="4">
                  This endpoint can&apos;t be called from the browser.
                  <br />
                  To be called from the browser, endpoints must support JSON and
                  use method GET, POST, PUT, PATCH, or DELETE.
                  <br />
                  Check Endpoint Details for more.
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
