import { parseDocument } from '@web-actions/parsing/CommandParser';
import { MiskRoute } from '@web-actions/api/responseTypes';
import { Header } from 'src/viewState';

interface RequestOptions {
  route: MiskRoute;
  path: string;
  requestBody?: string;
  headers?: Header[];
}

type Payload = { type: 'EMPTY' } | { type: 'JSON'; jsonBody: string };

export class JsonValidationError extends Error {}

async function call(path: string, options?: RequestInit): Promise<string> {
  const response = await fetch(path, options);
  let responseText = await response.text();
  try {
    responseText = JSON.stringify(JSON.parse(responseText), null, 2);
  } catch {
    // If the response is not JSON, return it as-is
  }
  return responseText;
}

function validatePayload(requestBody: string): Payload {
  if (requestBody === '') {
    return { type: 'EMPTY' };
  }

  const topLevel = parseDocument(requestBody);
  if (topLevel.firstError() !== null) {
    throw new JsonValidationError();
  }
  return { type: 'JSON', jsonBody: topLevel.render() };
}

export class ApiService {
  public static async submitRequest(request: RequestOptions): Promise<string> {
    const { route, path } = request;
    const requestBody = request.requestBody?.trim() ?? '';
    const providedHeaders: Record<string, string> = Object.fromEntries(
      request.headers?.map((it) => [it.key, it.value]) || [],
    );

    const acceptHeader: Record<string, string> =
      route.responseMediaTypes.hasJson()
        ? { Accept: 'application/json;charset=utf-8' }
        : {};

    if (route.httpMethod === 'GET') {
      return call(path, {
        method: 'GET',
        headers: {
          ...acceptHeader,
          ...providedHeaders,
        },
      });
    }

    const result = validatePayload(requestBody);
    if (result.type === 'JSON') {
      return call(path, {
        method: route.httpMethod,
        headers: {
          'Content-Type': 'application/json',
          ...acceptHeader,
          ...providedHeaders,
        },
        body: result.jsonBody,
      });
    } else if (result.type === 'EMPTY') {
      return call(path, {
        method: route.httpMethod,
        headers: {
          ...acceptHeader,
          ...providedHeaders,
        },
      });
    } else {
      throw new Error('Unexpected response type for route.');
    }
  }
}
