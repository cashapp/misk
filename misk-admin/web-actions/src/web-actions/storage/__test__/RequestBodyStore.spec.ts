import {
  getSavedRequestBody,
  removeSavedRequestBody,
  saveRequestBody,
} from '@web-actions/storage/RequestBodyStore';
import { MiskRoute } from '@web-actions/api/responseTypes';

function fakeRoute(
  httpMethod: string,
  path: string,
  actionName: string = 'com.squareup.test.TestAction',
): MiskRoute {
  return { httpMethod, path, actionName } as MiskRoute;
}

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

describe('RequestBodyStore', () => {
  const store = new Map<string, string>();

  beforeAll(() => {
    Object.defineProperty(globalThis, 'localStorage', {
      value: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
        key: (index: number) => Array.from(store.keys())[index] ?? null,
        get length() {
          return store.size;
        },
      },
      configurable: true,
    });
  });

  beforeEach(() => {
    store.clear();
    jest.restoreAllMocks();
  });

  it('returns the saved body for the same route', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, '{"a": 1}');
    expect(getSavedRequestBody(route)).toBe('{"a": 1}');
  });

  it('keeps bodies separate by method, path, and action name', () => {
    const jsonAction = fakeRoute('POST', '/things', 'com.squareup.test.A');
    const otherAction = fakeRoute('POST', '/things', 'com.squareup.test.B');
    saveRequestBody(jsonAction, '{"a": 1}');
    saveRequestBody(otherAction, '{"b": 2}');
    saveRequestBody(fakeRoute('PUT', '/things'), '{"c": 3}');
    expect(getSavedRequestBody(jsonAction)).toBe('{"a": 1}');
    expect(getSavedRequestBody(otherAction)).toBe('{"b": 2}');
    expect(getSavedRequestBody(fakeRoute('PUT', '/things'))).toBe('{"c": 3}');
    expect(getSavedRequestBody(fakeRoute('POST', '/other'))).toBeNull();
  });

  it('expires bodies after seven days', () => {
    const route = fakeRoute('POST', '/things');
    const savedAt = Date.now();
    jest.spyOn(Date, 'now').mockReturnValue(savedAt);
    saveRequestBody(route, '{"a": 1}');

    jest.spyOn(Date, 'now').mockReturnValue(savedAt + SEVEN_DAYS_MS - 1);
    expect(getSavedRequestBody(route)).toBe('{"a": 1}');

    jest.spyOn(Date, 'now').mockReturnValue(savedAt + SEVEN_DAYS_MS + 1);
    expect(getSavedRequestBody(route)).toBeNull();
    expect(store.size).toBe(0);
  });

  it('removes legacy and expired entries on save', () => {
    store.set('web-actions.lastBody.v1::POST /things', '{"a": 1}');
    const expired = fakeRoute('POST', '/expired');
    const savedAt = Date.now();
    jest.spyOn(Date, 'now').mockReturnValue(savedAt);
    saveRequestBody(expired, '{"old": true}');

    jest.spyOn(Date, 'now').mockReturnValue(savedAt + SEVEN_DAYS_MS + 1);
    saveRequestBody(fakeRoute('POST', '/things'), '{"new": true}');

    expect(store.has('web-actions.lastBody.v1::POST /things')).toBe(false);
    expect(getSavedRequestBody(expired)).toBeNull();
    expect(getSavedRequestBody(fakeRoute('POST', '/things'))).toBe(
      '{"new": true}',
    );
    expect(store.size).toBe(1);
  });

  it('returns null for a corrupt entry', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, '{"a": 1}');
    const key = Array.from(store.keys())[0];
    store.set(key, 'not json');
    expect(getSavedRequestBody(route)).toBeNull();
  });

  it('removes the saved body when an empty body is saved', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, '{"a": 1}');
    saveRequestBody(route, '  ');
    expect(getSavedRequestBody(route)).toBeNull();
  });

  it('does not save bodies larger than 100 KB', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, 'x'.repeat(100 * 1024 + 1));
    expect(getSavedRequestBody(route)).toBeNull();
  });

  it('removes the saved body', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, '{"a": 1}');
    removeSavedRequestBody(route);
    expect(getSavedRequestBody(route)).toBeNull();
  });
});
