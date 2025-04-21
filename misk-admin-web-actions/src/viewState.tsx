import { MiskRoute } from '@web-actions/api/responseTypes';

export interface Header {
  key: string;
  value: string;
}

export interface ViewState {
  path: string;
  selectedAction: MiskRoute | null;
  loading: boolean;
  isHelpOpen: boolean;
  headers: Header[];
  showRawMetadata: boolean;
  isCollapsed: boolean;
}
