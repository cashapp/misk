import { MiskRoute } from '@web-actions/api/responseTypes';
import { buildSlugIndex, writeSlugToUrl } from '@web-actions/utils/deepLink';

function route(
  actionName: string,
  httpMethod: string,
  path: string,
): MiskRoute {
  return { actionName, httpMethod, path } as MiskRoute;
}

describe('buildSlugIndex', () => {
  it('uses the simple class name when unique', () => {
    const myAction = route('com.squareup.MyAction', 'GET', '/my-action');
    const otherAction = route('com.squareup.OtherAction', 'POST', '/other');
    const { routeBySlug, slugByRoute } = buildSlugIndex([
      myAction,
      otherAction,
    ]);

    expect(routeBySlug.get('MyAction')).toBe(myAction);
    expect(routeBySlug.get('OtherAction')).toBe(otherAction);
    expect(slugByRoute.get(myAction)).toBe('MyAction');
    expect(slugByRoute.get(otherAction)).toBe('OtherAction');
  });

  it('appends the http method when class names collide', () => {
    const getAction = route('com.squareup.MyAction', 'GET', '/my-action');
    const postAction = route('com.other.MyAction', 'POST', '/my-action');
    const { routeBySlug, slugByRoute } = buildSlugIndex([
      getAction,
      postAction,
    ]);

    expect(routeBySlug.get('MyAction')).toBeUndefined();
    expect(routeBySlug.get('MyAction:GET')).toBe(getAction);
    expect(routeBySlug.get('MyAction:POST')).toBe(postAction);
    expect(slugByRoute.get(getAction)).toBe('MyAction:GET');
    expect(slugByRoute.get(postAction)).toBe('MyAction:POST');
  });

  it('appends the path when class names and methods collide', () => {
    const oldAction = route('com.squareup.MyAction', 'GET', '/old');
    const newAction = route('com.other.MyAction', 'GET', '/new');
    const { routeBySlug, slugByRoute } = buildSlugIndex([oldAction, newAction]);

    expect(routeBySlug.get('MyAction')).toBeUndefined();
    expect(routeBySlug.get('MyAction:GET')).toBeUndefined();
    expect(routeBySlug.get('MyAction:GET:/old')).toBe(oldAction);
    expect(routeBySlug.get('MyAction:GET:/new')).toBe(newAction);
    expect(slugByRoute.get(oldAction)).toBe('MyAction:GET:/old');
    expect(slugByRoute.get(newAction)).toBe('MyAction:GET:/new');
  });

  it('only disambiguates the colliding group', () => {
    const getAction = route('com.squareup.MyAction', 'GET', '/my-action');
    const postAction = route('com.other.MyAction', 'POST', '/my-action');
    const uniqueAction = route('com.squareup.UniqueAction', 'GET', '/unique');
    const { slugByRoute } = buildSlugIndex([
      getAction,
      postAction,
      uniqueAction,
    ]);

    expect(slugByRoute.get(uniqueAction)).toBe('UniqueAction');
    expect(slugByRoute.get(getAction)).toBe('MyAction:GET');
  });

  it('assigns no slug when routes are ambiguous even with the path', () => {
    const jsonAction = route('com.squareup.MyAction', 'POST', '/my-action');
    const protoAction = route('com.other.MyAction', 'POST', '/my-action');
    const { routeBySlug, slugByRoute } = buildSlugIndex([
      jsonAction,
      protoAction,
    ]);

    expect(routeBySlug.size).toBe(0);
    expect(slugByRoute.get(jsonAction)).toBeUndefined();
    expect(slugByRoute.get(protoAction)).toBeUndefined();
  });
});

describe('writeSlugToUrl', () => {
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('preserves the existing history state', () => {
    const replaceState = jest.fn();
    const existingState = { turbo: 'restoration-identifier' };
    const win: any = {
      history: { state: existingState, replaceState },
      location: { href: 'http://localhost/_admin/web-actions/', hash: '' },
    };
    win.top = win;
    (globalThis as Record<string, unknown>).window = win;

    writeSlugToUrl('MyAction:GET');

    expect(replaceState).toHaveBeenCalledWith(
      existingState,
      '',
      '#MyAction:GET',
    );
  });
});
