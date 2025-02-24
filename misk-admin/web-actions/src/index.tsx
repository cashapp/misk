import React, { useEffect, useState, useRef, useCallback } from 'react';

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
} from '@chakra-ui/react';
import ReadOnlyEditor from '@web-actions/ui/ReadOnlyViewer';
import 'ace-builds';
import 'ace-builds/webpack-resolver';
import EndpointSelector, {
  EndpointSelectionCallbacks,
} from '@web-actions/ui/EndpointSelection';
import { ViewState } from 'src/viewState';
import { fetchCached } from '@web-actions/network/http';
import { MiskMetadataResponse } from '@web-actions/api/responseTypes';
import { Select } from '@chakra-ui/react';
import { createIcon } from '@chakra-ui/icons';

const endpointSelectionCallbacks: EndpointSelectionCallbacks = [];

function App() {
  const [viewState, setViewState] = useState<ViewState>({
    path: '',
    selectedAction: null,
    response: null,
    callables: [],
  });
  const [loading, setLoading] = useState<boolean>(true);
  const endPointSelectorRef = useRef<EndpointSelector>();
  const requestEditorRef = useRef<RequestEditor>();

  useEffect(() => {
    endpointSelectionCallbacks.push((selectedAction) => {
      const callables = selectedAction.getCallablesByMethod();
      const defaultCallable = callables[0];

      requestEditorRef.current?.setEndpointSelection(defaultCallable);

      setViewState((curr) => ({
        ...curr,
        selectedAction: selectedAction,
        path:
          defaultCallable?.pathPattern ||
          selectedAction.all[0]?.pathPattern ||
          '',
        selectedCallable: defaultCallable,
        callables: callables,
      }));
    });
  }, []);

  useEffect(() => {
    const handleKeyPress = (event: KeyboardEvent) => {
      const isShortcutKey = event.ctrlKey || event.metaKey;
      if (isShortcutKey && event.key === 'k') {
        event.preventDefault();
        endPointSelectorRef.current?.focusSelect();
      } else if (isShortcutKey && event.key === 'Enter') {
        requestEditorRef.current?.submitRequest();
      }
    };
    document.addEventListener('keydown', handleKeyPress);
    return () => {
      document.removeEventListener('keydown', handleKeyPress);
    };
  }, []);

  fetchCached<MiskMetadataResponse>(`/api/web-actions/metadata`).finally(() => {
    setLoading(false);
  });
  const ActionIcon = createIcon({
    displayName: 'ActionIcon',
    viewBox: '0 0 24 24',
    path: <polygon points="5 3 19 12 5 21 5 3" fill="currentColor" />,
  });
  return (
    <Box>
      {loading && (
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
        <EndpointSelector
          ref={endPointSelectorRef as any}
          onDismiss={() => {
            requestEditorRef?.current?.focusEditor();
          }}
          endpointSelectionCallbacks={endpointSelectionCallbacks}
        />
        <HStack spacing={2} flexGrow={1} width="100%">
          <VStack height="100%" flexGrow={1} alignItems="start">
            <Heading color="white" size="sm" fontWeight="semibold">
              Request
            </Heading>
            <HStack flexGrow={1} w="100%">
              {viewState.callables.length > 0 && (
                <Select
                  value={viewState.selectedCallable?.httpMethod}
                  bg="white"
                  width="fit-content"
                  minWidth="fit-content"
                  onChange={(e) => {
                    const selected = viewState.callables.find(
                      (it) => it.httpMethod === e.target.value,
                    );
                    requestEditorRef.current?.setEndpointSelection(selected);
                    setViewState({
                      ...viewState,
                      path: selected?.pathPattern || '',
                      selectedCallable: selected,
                    });
                  }}
                >
                  {viewState.callables.map((callable) => (
                    <option
                      key={callable.httpMethod}
                      value={callable.httpMethod}
                    >
                      {callable.httpMethod}
                    </option>
                  ))}
                </Select>
              )}
              <Input
                value={viewState.path}
                placeholder="Path"
                bg="white"
                onChange={(e) => {
                  requestEditorRef.current?.setPath(e.target.value);
                  setViewState({
                    ...viewState,
                    path: e.target.value,
                  });
                }}
              />
              {viewState.selectedCallable && (
                <IconButton
                  aria-label="Run"
                  colorScheme={'green'}
                  onClick={() => {}}
                >
                  <ActionIcon />
                </IconButton>
              )}
            </HStack>
            <Heading color="white" size="xs" fontWeight="semibold">
              Body
            </Heading>
            <RequestEditor
              ref={requestEditorRef as any}
              endpointSelectionCallbacks={endpointSelectionCallbacks}
              onResponse={(response) => {
                setViewState({ ...viewState, response });
              }}
            />
            <Heading color="white" size="sm" fontWeight="semibold">
              Response
            </Heading>
            <ReadOnlyEditor content={() => viewState.response} />
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
