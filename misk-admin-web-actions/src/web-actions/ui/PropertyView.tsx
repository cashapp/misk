import React, { ReactNode } from 'react';
import { Box, Heading } from '@chakra-ui/react';

interface PropertyProps {
  label: string;
  value: string | string[] | ReactNode;
}

const PropertyView: React.FC<PropertyProps> = ({ label, value }) => {
  let valueNode: ReactNode;
  if (typeof value === 'string') {
    valueNode = (
      <Box fontFamily="mono" textColor="black">
        {value}
      </Box>
    );
  } else if (Array.isArray(value)) {
    valueNode = (
      <Box fontFamily="mono" textColor="black">
        {value.join(', ')}
      </Box>
    );
  } else {
    valueNode = value;
  }
  return (
    <Box>
      <Heading size="xs" color="black">
        {label}
      </Heading>
      {valueNode}
    </Box>
  );
};

export default PropertyView;
