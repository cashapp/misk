import {
  ActionGroup,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';
import { Header } from '@web-actions/services/ApiService';

export interface ViewState {
  callables: MiskWebActionDefinition[];
  selectedCallable?: MiskWebActionDefinition;
  path: string;
  selectedAction: ActionGroup | null;
  loading: boolean;
  isHelpOpen: boolean;
  headers: Header[];
}
