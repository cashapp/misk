import React, { ReactNode } from 'react';
import { Box, Link, VStack } from '@chakra-ui/react';
import { MiskRoute } from '@web-actions/api/responseTypes';
import ReadOnlyEditor from '@web-actions/ui/ReadOnlyViewer';
import { distinct } from '@web-actions/utils/common';
import PropertyView from '@web-actions/ui/PropertyView';
import { ExternalLinkIcon } from '@chakra-ui/icons';

interface Props {
  metadata: MiskRoute | null;
  showRaw: boolean;
}

const MetadataView: React.FC<Props> = ({ metadata, showRaw }) => {
  if (!metadata) {
    return <></>;
  }

  function renderType(type: string): ReactNode {
    const docUrl = metadata?.types[type]?.documentationUrl;
    if (!docUrl) {
      return type;
    }
    return (
      <Link fontFamily="mono" textColor="black" href={docUrl} isExternal>
        {type} <ExternalLinkIcon mx="2px" />
      </Link>
    );
  }

  return (
    <Box height="100%">
      {showRaw ? (
        <ReadOnlyEditor content={() => JSON.stringify(metadata.all, null, 2)} />
      ) : (
        <VStack align="start" p={4} spacing={4} color="white">
          <PropertyView label="Path" value={metadata.path} />
          <PropertyView label="HTTP Method" value={metadata.httpMethod} />
          <PropertyView label="Action Name" value={metadata.actionName} />

          {metadata.requestType && (
            <PropertyView
              label="Request Type"
              value={renderType(metadata.requestType)}
            />
          )}

          {metadata.returnType && (
            <PropertyView
              label="Return Type"
              value={renderType(metadata.returnType)}
            />
          )}

          <PropertyView
            label="Request Media Types"
            value={metadata.requestMediaTypes.toString()}
          />
          <PropertyView
            label="Response Media Types"
            value={metadata.responseMediaTypes.toString()}
          />
          <PropertyView
            label="Allowed Services"
            value={distinct(metadata.allowedServices)}
          />
          <PropertyView
            label="Allowed Capabilities"
            value={distinct(metadata.allowedCapabilities)}
          />
        </VStack>
      )}
    </Box>
  );
};

export default MetadataView;
