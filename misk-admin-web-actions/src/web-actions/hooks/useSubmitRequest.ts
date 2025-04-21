import { useCallback, useState } from 'react';
import { MiskRoute } from '@web-actions/api/responseTypes';
import {
  ApiService,
  JsonValidationError,
} from '@web-actions/services/ApiService';
import { APP_EVENTS, appEvents } from '@web-actions/events/appEvents';
import { Header } from 'src/viewState';

export interface SubmitRequestState {
  submit: () => Promise<void>;
  submitting: boolean;
  response: string | null;
}

export function useSubmitRequest(
  selectedCallable: MiskRoute | null,
  path: string,
  headers: Header[],
  getRequestBody: () => string,
): SubmitRequestState {
  const [loading, setLoading] = useState<boolean>(false);
  const [response, setResponse] = useState<string | null>(null);

  const submit = useCallback(async () => {
    if (!selectedCallable) {
      return;
    }

    setLoading(true);
    try {
      const response = await ApiService.submitRequest({
        route: selectedCallable,
        path: path,
        requestBody: getRequestBody(),
        headers: headers,
      });

      setResponse(response);
    } catch (e) {
      if (e instanceof JsonValidationError) {
        appEvents.emit(APP_EVENTS.SHOW_ERROR_TOAST);
      }
    } finally {
      setLoading(false);
    }
  }, [selectedCallable, path, headers, getRequestBody]);

  return { submit, submitting: loading, response };
}
