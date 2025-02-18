import { ActionGroup } from '@web-actions/api/responseTypes';

export interface ViewState {
  selectedAction: ActionGroup | null;
  response: string | null;
}
