import React, { Dispatch, SetStateAction } from 'react';
import { Ace } from 'ace-builds';
import ace from 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/ext-language_tools';
import { Box, IconButton } from '@chakra-ui/react';
import { CopyIcon } from '@chakra-ui/icons';

interface Props {
  content: () => string | null;
  display: string;
}

export default class ReadOnlyEditor extends React.Component<Props> {
  public refEditor: HTMLElement | null = null;
  public editor: Ace.Editor | null = null;

  constructor(props: Props) {
    super(props);
    this.copyToClipboard = this.copyToClipboard.bind(this);
  }

  componentDidMount() {
    this.editor = ace.edit(this.refEditor, {
      theme: 'ace/theme/textmate',
      mode: 'ace/mode/json',
      minLines: 10,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
      highlightActiveLine: false,
      highlightGutterLine: false,
    });
    const editor = this.editor!;

    editor.commands.removeCommand('showSettingsMenu');
    editor.commands.removeCommand('gotoline');

    editor.setReadOnly(true);

    this.editor?.setValue(this.props.content() || '', -1);
  }

  componentDidUpdate() {
    this.editor?.setValue(this.props.content() || '', -1);
  }

  public updateRef(item: HTMLDivElement | null) {
    this.refEditor = item;
  }

  async copyToClipboard() {
    try {
      const content = this.editor!.getValue();
      await navigator.clipboard.writeText(content);
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
        <IconButton
          aria-label="Copy"
          icon={<CopyIcon />}
          zIndex="100"
          position="absolute"
          colorScheme={'blackAlpha'}
          top="4"
          right="4"
          onClick={this.copyToClipboard}
        />
        <Box
          id={'response-viewer'}
          width="100%"
          height="100%"
          ref={(it) => this.updateRef(it)}
        />
      </Box>
    );
  }
}
