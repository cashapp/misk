import { useCallback, useState } from 'react';
import { ActionGroup } from '@web-actions/api/responseTypes';
import { ApiService, Header } from '@web-actions/services/ApiService';

export interface SubmitRequestState {
  submit: () => Promise<void>;
  submitting: boolean;
  response: string | null;
}

export function useSubmitRequest(
  selectedCallable: ActionGroup | null,
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
        action: selectedCallable,
        path: path,
        requestBody: getRequestBody(),
        headers: headers,
      });

      setResponse(response);
    } finally {
      setLoading(false);
    }
  }, [selectedCallable, path, headers, getRequestBody]);

  return { submit, submitting: loading, response };
}
