import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';

export interface ViewState {
  selectedAction: MiskWebActionDefinition | null;
  response: string | null;
}
