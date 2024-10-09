import React, { useState } from "react"

import { createRoot } from "react-dom/client"
import RequestEditor from "@misk-console/ui/RequestEditor"
import { Image, Box, ChakraProvider, HStack, VStack } from "@chakra-ui/react"
import ResponseViewer from "@misk-console/ui/ResponseViewer"
import "ace-builds"
import "ace-builds/webpack-resolver"

function App() {
  const [response, setResponse] = useState<any | null>(null)
  return (
    <VStack height="100vh" spacing={0} bg="gray.600" alignItems="start">
      <Box p={4}>
        <Image src="/m.svg" alt="Logo" boxSize="25px" />
      </Box>
      <HStack bg="blue.200" spacing={2} p={2} flexGrow={1} width="100%">
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
