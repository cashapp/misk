import { MiskWebActionDefinition } from '@misk-console/api/responseTypes';

export interface ViewState {
  selectedAction: MiskWebActionDefinition | null;
  response: string | null;
}
