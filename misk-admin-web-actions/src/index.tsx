import React, { useEffect, useState, useRef } from 'react';

import { createRoot } from 'react-dom/client';
import RequestEditor from '@web-actions/ui/RequestEditor';
import {
  Box,
  ChakraProvider,
  HStack,
  Spinner,
  VStack,
  Heading,
  Input,
  IconButton,
  Button,
  useToast,
} from '@chakra-ui/react';
import { DeleteIcon, AddIcon } from '@chakra-ui/icons';
import HelpPanel from '@web-actions/ui/HelpPanel';
import ReadOnlyEditor from '@web-actions/ui/ReadOnlyViewer';
import 'ace-builds';
import 'ace-builds/webpack-resolver';
import EndpointSelector from '@web-actions/ui/EndpointSelection';
import { ViewState } from 'src/viewState';
import { fetchCached } from '@web-actions/network/http';
import {
  ActionGroup,
  MiskMetadataResponse,
} from '@web-actions/api/responseTypes';
import { createIcon } from '@chakra-ui/icons';
import { useSubmitRequest } from '@web-actions/hooks/useSubmitRequest';
import { useKeyboardShortcuts } from '@web-actions/hooks/useKeyboardShortcuts';
import { useAppEvent } from '@web-actions/hooks/useAppEvent';
import { APP_EVENTS } from '@web-actions/events/appEvents';

function App() {
  const [viewState, setViewState] = useState<ViewState>(() => ({
    path: '',
    selectedAction: null,
    callables: [],
    loading: true,
    isHelpOpen: localStorage.getItem('hasSeenHelp') !== 'true',
    headers: [],
  }));
  const endPointSelectorRef = useRef<EndpointSelector>(null);
  const requestEditorRef = useRef<RequestEditor>(null);
  const toast = useToast();

  const {
    submit: handleSubmitRequest,
    submitting,
    response,
  } = useSubmitRequest(
    viewState.selectedAction ?? null,
    viewState.path,
    viewState.headers,
    () => requestEditorRef.current?.editor?.getValue() ?? '',
  );

  useAppEvent(APP_EVENTS.ENDPOINT_SELECTED, (selectedAction: ActionGroup) => {
    requestEditorRef.current?.setEndpointSelection(selectedAction);

    setViewState((curr) => ({
      ...curr,
      selectedAction: selectedAction,
      path: selectedAction.path,
    }));
  });

  useKeyboardShortcuts();

  useAppEvent(APP_EVENTS.TOGGLE_HELP, () =>
    setViewState((prev) => ({ ...prev, isHelpOpen: !prev.isHelpOpen })),
  );
  useAppEvent(APP_EVENTS.SUBMIT_REQUEST, handleSubmitRequest);
  useAppEvent(APP_EVENTS.FOCUS_ENDPOINT_SELECTOR, () => {
    endPointSelectorRef.current?.focusSelect();
  });

  useAppEvent(APP_EVENTS.SHOW_ERROR_TOAST, () => {
    toast({
      title: 'Error',
      description: 'There are syntax or field name errors in the JSON Request',
      status: 'error',
      duration: 5000,
      isClosable: true,
    });
  });

  useEffect(() => {
    fetchCached<MiskMetadataResponse>(`/api/web-actions/metadata`).finally(
      () => {
        setViewState((prev) => ({ ...prev, loading: false }));
      },
    );
  }, [setViewState]);

  const ActionIcon = createIcon({
    displayName: 'ActionIcon',
    viewBox: '0 0 24 24',
    path: <polygon points="5 3 19 12 5 21 5 3" fill="currentColor" />,
  });

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

  return (
    <Box>
      {viewState.loading && (
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
      <VStack
        height="100vh"
        padding={2}
        spacing={2}
        bg="gray.600"
        alignItems="start"
      >
        <HStack width="100%" justifyContent="space-between">
          <EndpointSelector
            ref={endPointSelectorRef as any}
            onDismiss={() => {
              requestEditorRef?.current?.focusEditor();
            }}
          />
          <Button
            colorScheme="blue"
            size="sm"
            onClick={() =>
              setViewState((prev) => ({ ...prev, isHelpOpen: true }))
            }
          >
            Help (⌘/)
          </Button>
        </HStack>
        <HelpPanel
          isOpen={viewState.isHelpOpen}
          onClose={() => {
            setViewState((prev) => ({ ...prev, isHelpOpen: false }));
            if (!localStorage.getItem('hasSeenHelp')) {
              setTimeout(() => {
                endPointSelectorRef.current?.focusSelect();
              }, 500);
            }
            localStorage.setItem('hasSeenHelp', 'true');
          }}
        />
        <HStack spacing={2} flexGrow={1} width="100%">
          <VStack height="100%" flexGrow={1} alignItems="start">
            <Heading color="white" size="sm" fontWeight="semibold">
              Request
            </Heading>
            <HStack flexGrow={1} w="100%">
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
              {viewState.selectedAction?.canCall === true && (
                <IconButton
                  aria-label="Run"
                  colorScheme={'green'}
                  onClick={handleSubmitRequest}
                >
                  <ActionIcon />
                </IconButton>
              )}
            </HStack>
            <Heading color="white" size="xs" fontWeight="semibold">
              Headers
            </Heading>
            <Box width="100%">
              <VStack spacing={2} align="stretch">
                {viewState.headers.map((header, index) => (
                  <HStack key={index} spacing={2}>
                    <Input
                      placeholder="Header Key"
                      value={header.key}
                      onChange={(e) =>
                        updateHeader(index, 'key', e.target.value)
                      }
                      bg="white"
                    />
                    <Input
                      placeholder="Header Value"
                      value={header.value}
                      onChange={(e) =>
                        updateHeader(index, 'value', e.target.value)
                      }
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
            <Heading color="white" size="xs" fontWeight="semibold">
              Body
            </Heading>
            <RequestEditor ref={requestEditorRef as any} loading={submitting} />
            <Heading color="white" size="sm" fontWeight="semibold">
              Response
            </Heading>
            <ReadOnlyEditor content={() => response} />
          </VStack>
          <VStack height="100%" flexGrow={1} alignItems="start">
            <Heading color="white" size="sm" fontWeight="semibold">
              Endpoint Metadata
            </Heading>
            <ReadOnlyEditor
              content={() => {
                if (viewState.selectedAction === null) {
                  return null;
                }
                return JSON.stringify(viewState.selectedAction.all, null, 2);
              }}
            />
          </VStack>
        </HStack>
      </VStack>
    </Box>
  );
}

const container = document.getElementById('root');

createRoot(container!).render(
  <ChakraProvider>
    <App />
  </ChakraProvider>,
);
