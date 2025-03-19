import React from 'react';
import {
  Modal,
  ModalOverlay,
  ModalContent,
  ModalHeader,
  ModalBody,
  ModalCloseButton,
  Heading,
  Text,
  UnorderedList,
  OrderedList,
  ListItem,
  Code,
  VStack,
  useColorModeValue,
  Button,
  HStack,
} from '@chakra-ui/react';
import { clearCache } from '@web-actions/storage/cache';

interface HelpPanelProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function HelpPanel({
  isOpen,
  onClose,
}: HelpPanelProps): JSX.Element {
  const bgColor = useColorModeValue('white', 'gray.800');

  const handleClearCache = async () => {
    await clearCache();
    localStorage.clear();
    window.location.reload();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} size="xl" scrollBehavior="inside">
      <ModalOverlay backdropFilter="blur(2px)" />
      <ModalContent bg={bgColor} maxW="600px">
        <ModalHeader>
          <HStack justify="space-between">
            <Text>Web Actions Help</Text>
          </HStack>
        </ModalHeader>
        <ModalCloseButton />

        <ModalBody pb={6}>
          <VStack align="stretch" spacing={6}>
            <section>
              <Heading as="h3" size="md" mb={2}>
                Overview
              </Heading>
              <Text>
                Web Actions allows you edit and submit requests to Misk Action
                endpoints.
              </Text>
            </section>

            <section>
              <Heading as="h3" size="md" mb={2}>
                Usage
              </Heading>
              <OrderedList spacing={1} pl={4}>
                <ListItem>Trigger the action selector with ⌘K</ListItem>
                <ListItem>Type to filter options, and select with ⏎</ListItem>
                <ListItem>
                  Use the Request Editor to modify request. ⌃Space triggers
                  auto-completions.
                </ListItem>
                <ListItem>Submit request with ⌘⏎</ListItem>
                <ListItem>Repeat</ListItem>
              </OrderedList>
            </section>

            <section>
              <Heading as="h3" size="md" mb={2}>
                Keyboard Shortcuts
              </Heading>
              <UnorderedList spacing={1} pl={4}>
                <ListItem>⌘K - Open web action selector</ListItem>
                <ListItem>⌘⏎ - Submit request</ListItem>
                <ListItem>
                  ⌃Space - Trigger auto-completion in request editor
                </ListItem>
                <ListItem>⌘/ - Toggle help panel</ListItem>
                <ListItem>
                  Esc - Dismiss action selector / completion dialogs
                </ListItem>
              </UnorderedList>
            </section>
            <Button
              size="sm"
              colorScheme="red"
              variant="ghost"
              onClick={handleClearCache}
              width="fit-content"
              alignSelf="end"
            >
              Clear Cache
            </Button>
          </VStack>
        </ModalBody>
      </ModalContent>
    </Modal>
  );
}
