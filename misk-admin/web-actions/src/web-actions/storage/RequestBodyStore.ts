import { MiskRoute } from '@web-actions/api/responseTypes';

const KEY_PREFIX = 'web-actions.lastBody.v2';
const LEGACY_KEY_PREFIXES = ['web-actions.lastBody.v1'];
const MAX_BODY_LENGTH = 100 * 1024; // 100 KiB
const TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

interface SavedBody {
  savedAt: number;
  body: string;
}

function storageKeyFor(route: MiskRoute): string {
  return `${KEY_PREFIX}::${route.httpMethod} ${route.path} ${route.actionName}`;
}

function removeStaleEntries() {
  try {
    const staleKeys: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key === null) {
        continue;
      }
      if (LEGACY_KEY_PREFIXES.some((prefix) => key.startsWith(prefix))) {
        staleKeys.push(key);
      } else if (key.startsWith(KEY_PREFIX) && parseEntry(key) === null) {
        staleKeys.push(key);
      }
    }
    staleKeys.forEach((key) => localStorage.removeItem(key));
  } catch {
    // localStorage can be unavailable; skipping cleanup is fine.
  }
}

function parseEntry(key: string): SavedBody | null {
  const raw = localStorage.getItem(key);
  if (raw === null) {
    return null;
  }
  try {
    const entry = JSON.parse(raw) as SavedBody;
    if (
      typeof entry.savedAt !== 'number' ||
      typeof entry.body !== 'string' ||
      Date.now() - entry.savedAt > TTL_MS
    ) {
      return null;
    }
    return entry;
  } catch {
    return null;
  }
}

export function getSavedRequestBody(route: MiskRoute): string | null {
  try {
    const key = storageKeyFor(route);
    const entry = parseEntry(key);
    if (entry === null) {
      localStorage.removeItem(key);
      return null;
    }
    return entry.body;
  } catch {
    return null;
  }
}

export function saveRequestBody(route: MiskRoute, body: string) {
  removeStaleEntries();
  if (body.trim() === '') {
    removeSavedRequestBody(route);
    return;
  }
  if (body.length > MAX_BODY_LENGTH) {
    return;
  }
  const entry: SavedBody = { savedAt: Date.now(), body: body };
  try {
    localStorage.setItem(storageKeyFor(route), JSON.stringify(entry));
  } catch {
    // localStorage can be unavailable or full; losing the saved body is fine.
  }
}

export function removeSavedRequestBody(route: MiskRoute) {
  try {
    localStorage.removeItem(storageKeyFor(route));
  } catch {
    // Ignore; see saveRequestBody.
  }
}
