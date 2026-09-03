import {
  getSavedRequestBody,
  removeSavedRequestBody,
  saveRequestBody,
} from '@web-actions/storage/RequestBodyStore';
import { MiskRoute } from '@web-actions/api/responseTypes';

function fakeRoute(httpMethod: string, path: string): MiskRoute {
  return { httpMethod, path } as MiskRoute;
}

describe('RequestBodyStore', () => {
  const store = new Map<string, string>();

  beforeAll(() => {
    Object.defineProperty(globalThis, 'localStorage', {
      value: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
      },
      configurable: true,
    });
  });

  beforeEach(() => {
    store.clear();
  });

  it('returns the saved body for the same method and path', () => {
    const route = fakeRoute('POST', '/things');
    saveRequestBody(route, '{"a": 1}');
    expect(getSavedRequestBody(route)).toBe('{"a": 1}');
  });

  it('keeps bodies separate by method and path', () => {
    saveRequestBody(fakeRoute('POST', '/things'), '{"a": 1}');
    saveRequestBody(fakeRoute('PUT', '/things'), '{"b": 2}');
    expect(getSavedRequestBody(fakeRoute('POST', '/things'))).toBe('{"a": 1}');
    expect(getSavedRequestBody(fakeRoute('PUT', '/things'))).toBe('{"b": 2}');
    expect(getSavedRequestBody(fakeRoute('POST', '/other'))).toBeNull();
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
