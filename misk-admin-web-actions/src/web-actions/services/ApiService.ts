import { parseDocument } from '@web-actions/parsing/CommandParser';
import { ActionGroup } from '@web-actions/api/responseTypes';
import { appEvents, APP_EVENTS } from '@web-actions/events/appEvents';

export interface Header {
  key: string;
  value: string;
}

interface RequestOptions {
  action: ActionGroup;
  path: string;
  requestBody?: string;
  headers?: Array<Header>;
}

export class ApiService {
  public static async submitRequest(request: RequestOptions): Promise<string> {
    const { action, path } = request;
    const requestBody = request.requestBody?.trim() ?? '';
    const additionalHeaders = request.headers ?? [];

    const headers: Record<string, string> = {};

    const returnsJson = action.responseMediaTypes.some((mediaType) =>
      mediaType.startsWith('application/json'),
    );
    if (returnsJson) {
      headers['Accept'] = 'application/json;charset=utf-8';
    }

    let body: string | undefined = undefined;
    if (action.httpMethod !== 'GET') {
      if (requestBody) {
        headers['Content-Type'] = 'application/json';

        const topLevel = parseDocument(requestBody);
        if (topLevel?.firstError() !== null) {
          appEvents.emit(APP_EVENTS.SHOW_ERROR_TOAST);
          return '';
        }
        body = topLevel.render();
      }
    }

    additionalHeaders.forEach((header) => {
      if (header.key && header.value) {
        headers[header.key] = header.value;
      }
    });

    const fetchOptions = {
      method: action.httpMethod,
      headers: headers,
      body: body,
    };

    const response = await fetch(path, fetchOptions);
    let responseText = await response.text();

    try {
      responseText = JSON.stringify(JSON.parse(responseText), null, 2);
    } catch {
      // If the response is not JSON, return it as-is
    }

    return responseText;
  }
}
