import { CommandParser } from '@web-actions/parsing/CommandParser';
import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';

export interface Header {
  key: string;
  value: string;
}

interface RequestOptions {
  action: MiskWebActionDefinition;
  path: string;
  requestBody?: string;
  headers?: Array<Header>;
}

export class ApiService {
  public static async submitRequest({
    action,
    path,
    requestBody,
    headers: additionalHeaders = [],
  }: RequestOptions): Promise<string> {
    const headers: Record<string, string> = {};

    if (action.httpMethod !== 'GET') {
      headers['Content-Type'] = 'application/json';
    }

    additionalHeaders.forEach((header) => {
      if (header.key && header.value) {
        headers[header.key] = header.value;
      }
    });

    const fetchOptions = {
      method: action.httpMethod,
      headers: headers,
      body:
        action.httpMethod !== 'GET'
          ? new CommandParser(requestBody ?? '').parse()?.render()
          : undefined,
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
