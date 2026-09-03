import { MiskRoute } from '@web-actions/api/responseTypes';

const KEY_PREFIX = 'web-actions.lastBody.v1';
const MAX_BODY_LENGTH = 100 * 1024;

function keyFor(route: MiskRoute): string {
  return `${KEY_PREFIX}::${route.httpMethod} ${route.path}`;
}

export function getSavedRequestBody(route: MiskRoute): string | null {
  try {
    return localStorage.getItem(keyFor(route));
  } catch {
    return null;
  }
}

export function saveRequestBody(route: MiskRoute, body: string) {
  if (body.trim() === '') {
    removeSavedRequestBody(route);
    return;
  }
  if (body.length > MAX_BODY_LENGTH) {
    return;
  }
  try {
    localStorage.setItem(keyFor(route), body);
  } catch {
    // localStorage can be unavailable or full; losing the saved body is fine.
  }
}

export function removeSavedRequestBody(route: MiskRoute) {
  try {
    localStorage.removeItem(keyFor(route));
  } catch {
    // Ignore; see saveRequestBody.
  }
}
