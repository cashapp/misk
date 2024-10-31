import React, { useState } from 'react';

import { createRoot } from 'react-dom/client';
import RequestEditor from '@web-actions/ui/RequestEditor';
import { Box, ChakraProvider, HStack, Spinner, VStack } from '@chakra-ui/react';
import ResponseViewer from '@web-actions/ui/ResponseViewer';
import 'ace-builds';
import 'ace-builds/webpack-resolver';
import EndpointSelector, {
  EndpointSelectionCallbacks,
} from '@web-actions/ui/EndpointSelection';
import { ViewState } from 'src/viewState';
import { fetchCached } from '@web-actions/network/http';
import { MiskMetadataResponse } from '@web-actions/api/responseTypes';

const endpointSelectionCallbacks: EndpointSelectionCallbacks = [];

function App() {
  const [viewState, setViewState] = useState<ViewState>({
    selectedAction: null,
    response: null,
  });
  const [loading, setLoading] = useState<boolean>(true);

  fetchCached<MiskMetadataResponse>(`/api/web-actions/metadata`).finally(() => {
    setLoading(false);
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
      <VStack height="100vh" spacing={0} bg="gray.600" alignItems="start">
        <EndpointSelector
          endpointSelectionCallbacks={endpointSelectionCallbacks}
        />
        <HStack bg="gray.200" spacing={2} p={2} flexGrow={1} width="100%">
          <RequestEditor
            endpointSelectionCallbacks={endpointSelectionCallbacks}
            onResponse={(response) => {
              setViewState({ ...viewState, response });
            }}
          />
          <ResponseViewer viewState={viewState} setViewState={setViewState} />
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
