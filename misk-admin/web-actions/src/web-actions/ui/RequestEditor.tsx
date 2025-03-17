import React from 'react';
import { Ace } from 'ace-builds';
import ace from 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/ext-language_tools';
import { ContextAwareCompleter } from '@web-actions/ui/ContextAwareCompleter';
import { Box, IconButton, Spinner } from '@chakra-ui/react';
import { CopyIcon } from '@chakra-ui/icons';
import { CommandParser } from '@web-actions/parsing/CommandParser';
import {
  MiskWebActionDefinition,
  MiskFieldDefinition,
} from '@web-actions/api/responseTypes';
import { triggerCompletionDialog } from '@web-actions/ui/AceEditor';

interface Props {
  loading: boolean;
}

interface State {
  isDisabled: boolean;
}

export default class RequestEditor extends React.Component<Props, State> {
  public refEditor: HTMLElement | null = null;
  public editor: Ace.Editor | null = null;

  private readonly completer;

  constructor(props: Props) {
    super(props);
    this.state = { isDisabled: false };

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

  private generateRequestBody(action: MiskWebActionDefinition): string {
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

  public setEndpointSelection(action: MiskWebActionDefinition | undefined) {
    this.completer.setSelection(action ?? null);
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
      const requestBody = this.generateRequestBody(action!);
      this.editor?.setValue(requestBody, -1);
      const lines = this.editor?.session.getLength();
      if (lines) {
        this.editor?.gotoLine(lines - 1, 2, false);
      }
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
        {(this.props.loading || this.state.isDisabled) && (
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
