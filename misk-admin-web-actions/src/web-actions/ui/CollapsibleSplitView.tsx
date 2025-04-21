import React, { useState } from 'react';
import {
  Box,
  IconButton,
  Flex,
  Tooltip,
  Text,
  VStack,
  HStack,
} from '@chakra-ui/react';
import { CloseIcon } from '@chakra-ui/icons';
import { DeveloperIcon } from '@web-actions/ui/DeveloperIcon';

interface CollapsibleSplitViewProps {
  leftContent: React.ReactNode;
  rightContent: React.ReactNode;
  isCollapsed: boolean;
  setIsCollapsed: (value: boolean) => void;
  onToggleRaw: () => void;
}

const CollapsibleSplitView: React.FC<CollapsibleSplitViewProps> = ({
  leftContent,
  rightContent,
  isCollapsed,
  setIsCollapsed,
  onToggleRaw,
}) => {
  const collapsedWidth = '0px';

  const toggleCollapse = () => {
    setIsCollapsed(!isCollapsed);
  };

  return (
    <Flex width="100%" height="100%" position="relative">
      <Box flex={1} height="100%" overflow="auto">
        {leftContent}
      </Box>
      <Box
        width={isCollapsed ? collapsedWidth : '50%'}
        height="100%"
        overflow="auto"
        transition="width 0.2s ease-in-out, opacity 0.2s ease-in-out"
        ml={isCollapsed ? 0 : 2}
        opacity={isCollapsed ? 0 : 1}
        pointerEvents={isCollapsed ? 'none' : 'auto'}
      >
        {isCollapsed ? (
          <VStack
            height="100%"
            width="100%"
            spacing={0}
            backgroundColor="white"
            borderRadius="10px"
            alignItems="flex-end"
          >
            <HStack p={2} width="100%" justifyContent="center">
              {/* Expand button removed */}
            </HStack>
          </VStack>
        ) : (
          <VStack
            height="100%"
            width="100%"
            spacing={0}
            backgroundColor="white"
            borderRadius="10px"
          >
            <HStack p={2} width="100%" justifyContent="space-between">
              <HStack>
                <Text fontWeight="medium">{'Endpoint Details'}</Text>
                <IconButton
                  aria-label="Toggle json metadata view"
                  icon={<DeveloperIcon />}
                  onClick={() => {
                    onToggleRaw();
                  }}
                  size="sm"
                  colorScheme="blue"
                />
              </HStack>
              <IconButton
                aria-label="Collapse panel"
                icon={<CloseIcon />}
                onClick={toggleCollapse}
                size="sm"
              />
            </HStack>
            <Box flex={1} width="100%" overflow="auto">
              {rightContent}
            </Box>
          </VStack>
        )}
      </Box>
    </Flex>
  );
};

export default CollapsibleSplitView;
