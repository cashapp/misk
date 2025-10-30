import React from 'react';
import {
  Box,
  Button,
  Heading,
  HStack,
  IconButton,
  Input,
  VStack,
  Tooltip,
} from '@chakra-ui/react';
import { AddIcon, DeleteIcon, InfoOutlineIcon } from '@chakra-ui/icons';
import { ViewState } from 'src/viewState';
import RequestEditor from '@web-actions/ui/RequestEditor';
import ReadOnlyEditor from '@web-actions/ui/ReadOnlyViewer';
import { ActionIcon } from '@web-actions/ui/ActionIcon';

interface RequestResponseProps {
  viewState: ViewState;
  setViewState: React.Dispatch<React.SetStateAction<ViewState>>;
  handleSubmitRequest: () => void;
  submitting: boolean;
  response: string | null;
  requestEditorRef: React.RefObject<any>;
  onToggleCollapse: () => void;
}

const RequestResponseView: React.FC<RequestResponseProps> = ({
  viewState,
  setViewState,
  handleSubmitRequest,
  submitting,
  response,
  requestEditorRef,
  onToggleCollapse,
}) => {
  const updateHeader = (
    index: number,
    field: 'key' | 'value',
    value: string,
  ) => {
    setViewState((prev) => ({
      ...prev,
      headers: prev.headers.map((header, i) =>
        i === index ? { ...header, [field]: value } : header,
      ),
    }));
  };

  const addHeader = () => {
    setViewState((prev) => ({
      ...prev,
      headers: [...prev.headers, { key: '', value: '' }],
    }));
  };

  const removeHeader = (index: number) => {
    setViewState((prev) => ({
      ...prev,
      headers: prev.headers.filter((_, i) => i !== index),
    }));
  };

  const displayIfEndpointNull = viewState.selectedAction === null && 'none';

  return (
    <VStack height="100%" alignItems="start">
      {viewState.selectedAction !== null && (
        <Heading color="white" size="sm" fontWeight="semibold">
          Request
        </Heading>
      )}
      <HStack flexGrow={1} w="100%">
        {viewState.selectedAction !== null && (
          <Input
            value={viewState.path}
            placeholder="Path"
            bg="white"
            onChange={(e) => {
              setViewState({
                ...viewState,
                path: e.target.value,
              });
            }}
          />
        )}
        <HStack>
          {viewState.selectedAction?.callable === true && (
            <IconButton
              aria-label="Run"
              colorScheme={'green'}
              onClick={handleSubmitRequest}
            >
              <ActionIcon />
            </IconButton>
          )}
          {viewState.selectedAction !== null && (
            <Tooltip label={'Endpoint Details'} placement="top">
              <IconButton
                aria-label="Expand panel"
                icon={<InfoOutlineIcon />}
                onClick={onToggleCollapse}
              />
            </Tooltip>
          )}
        </HStack>
      </HStack>
      <Heading
        display={displayIfEndpointNull}
        color="white"
        size="xs"
        fontWeight="semibold"
      >
        Headers
      </Heading>
      <Box display={displayIfEndpointNull} width="100%">
        <VStack spacing={2} align="stretch">
          {viewState.headers.map((header, index) => (
            <HStack key={index} spacing={2}>
              <Input
                placeholder="Header Key"
                value={header.key}
                onChange={(e) => updateHeader(index, 'key', e.target.value)}
                bg="white"
              />
              <Input
                placeholder="Header Value"
                value={header.value}
                onChange={(e) => updateHeader(index, 'value', e.target.value)}
                bg="white"
              />
              <IconButton
                aria-label="Remove header"
                icon={<DeleteIcon />}
                onClick={() => removeHeader(index)}
                colorScheme="red"
              />
            </HStack>
          ))}
          <Button
            leftIcon={<AddIcon />}
            onClick={addHeader}
            colorScheme="blue"
            size="sm"
          >
            Add Header
          </Button>
        </VStack>
      </Box>
      <Heading
        display={displayIfEndpointNull}
        color="white"
        size="xs"
        fontWeight="semibold"
      >
        Body
      </Heading>
      <RequestEditor
        display={displayIfEndpointNull}
        ref={requestEditorRef as any}
        loading={submitting}
        isCallable={viewState.selectedAction?.callable || false}
      />
      <Heading
        display={displayIfEndpointNull}
        color="white"
        size="sm"
        fontWeight="semibold"
      >
        Response
      </Heading>
      <ReadOnlyEditor
        display={displayIfEndpointNull}
        content={() => response}
      />
      {viewState.selectedAction === null && (
        <VStack
          marginTop="25%"
          width="100%"
          height="100%"
          display="flex"
          alignItems="center"
          justifyContent="center"
        >
          <Heading color="white" size="xl" fontWeight="bold" textAlign="center">
            Web Actions
          </Heading>
          <Heading color="white" size="sm" fontWeight="semibold">
            Select an endpoint to begin
          </Heading>
        </VStack>
      )}
    </VStack>
  );
};

export default RequestResponseView;
