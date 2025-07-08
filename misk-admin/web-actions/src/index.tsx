import React, { useEffect, useState, useRef } from 'react';

import { createRoot } from 'react-dom/client';
import RequestEditor from '@web-actions/ui/RequestEditor';
import {
  Box,
  ChakraProvider,
  HStack,
  Spinner,
  VStack,
  Button,
  useToast,
} from '@chakra-ui/react';
import HelpPanel from '@web-actions/ui/HelpPanel';
import 'ace-builds';
import 'ace-builds/webpack-resolver';
import EndpointSelector from '@web-actions/ui/EndpointSelection';
import { ViewState } from 'src/viewState';
import { fetchCached } from '@web-actions/network/http';
import {
  MiskRoute,
  MiskMetadataResponse,
} from '@web-actions/api/responseTypes';
import { useSubmitRequest } from '@web-actions/hooks/useSubmitRequest';
import { useKeyboardShortcuts } from '@web-actions/hooks/useKeyboardShortcuts';
import { useAppEvent } from '@web-actions/hooks/useAppEvent';
import { APP_EVENTS } from '@web-actions/events/appEvents';
import MetadataView from '@web-actions/ui/MetadataView';
import RequestResponseView from '@web-actions/ui/RequestResponseView';
import CollapsibleSplitView from '@web-actions/ui/CollapsibleSplitView';

function App() {
  const [viewState, setViewState] = useState<ViewState>({
    selectedAction: null,
    path: '',
    headers: [],
    isHelpOpen: localStorage.getItem('hasSeenHelp') !== 'true',
    loading: true,
    showRawMetadata: false,
    isCollapsed: true,
  });
  const endPointSelectorRef = useRef<EndpointSelector>(null);
  const requestEditorRef = useRef<RequestEditor>(null);
  const toast = useToast();

  const {
    submit: handleSubmitRequest,
    submitting: submitRequestSubmitting,
    response: submitRequestResponse,
  } = useSubmitRequest(
    viewState.selectedAction ?? null,
    viewState.path,
    viewState.headers,
    () => requestEditorRef.current?.editor?.getValue() ?? '',
  );

  useAppEvent(APP_EVENTS.ENDPOINT_SELECTED, (selectedAction: MiskRoute) => {
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
        <CollapsibleSplitView
          leftContent={
            <RequestResponseView
              viewState={viewState}
              setViewState={setViewState}
              handleSubmitRequest={handleSubmitRequest}
              submitting={submitRequestSubmitting}
              response={submitRequestResponse}
              requestEditorRef={requestEditorRef}
              onToggleCollapse={() =>
                setViewState((prev) => ({
                  ...prev,
                  isCollapsed: !prev.isCollapsed,
                }))
              }
            />
          }
          rightContent={
            <MetadataView
              metadata={viewState.selectedAction}
              showRaw={viewState.showRawMetadata}
            />
          }
          isCollapsed={viewState.isCollapsed}
          setIsCollapsed={(value) =>
            setViewState((prev) => ({ ...prev, isCollapsed: value }))
          }
          onToggleRaw={() =>
            setViewState((prev) => ({
              ...prev,
              showRawMetadata: !prev.showRawMetadata,
            }))
          }
        />
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
