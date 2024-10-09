import React, { useState } from "react"

import { createRoot } from "react-dom/client"
import RequestEditor from "@misk-console/ui/RequestEditor"
import { ChakraProvider, HStack, VStack } from "@chakra-ui/react"
import ResponseViewer from "@misk-console/ui/ResponseViewer"
import "ace-builds"
import "ace-builds/webpack-resolver"

function App() {
  const [response, setResponse] = useState<string | null>(null)
  return (
    <VStack height="100vh" spacing={0} bg="gray.600" alignItems="start">
      <HStack bg="gray.200" spacing={2} p={2} flexGrow={1} width="100%">
        <RequestEditor onResponse={setResponse} />
        <ResponseViewer toRender={response} />
      </HStack>
    </VStack>
  )
}

const container = document.getElementById("root")

createRoot(container!).render(
  <ChakraProvider>
    <App />
  </ChakraProvider>
)
