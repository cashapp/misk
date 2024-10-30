import { MiskWebActionDefinition } from '@misk-console/api/responseTypes';
import React from 'react';

export interface ViewState {
  selectedAction: MiskWebActionDefinition | null;
  response: string | null;
}

