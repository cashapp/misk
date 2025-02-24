import {
  ActionGroup,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';

export interface ViewState {
  callables: MiskWebActionDefinition[];
  selectedCallable?: MiskWebActionDefinition;
  path: string;
  selectedAction: ActionGroup | null;
  response: string | null;
}
