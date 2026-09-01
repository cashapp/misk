import { MiskRoute } from '@web-actions/api/responseTypes';

export interface SlugIndex {
  routeBySlug: Map<string, MiskRoute>;
  slugByRoute: Map<MiskRoute, string>;
}

function simpleName(route: MiskRoute): string {
  const parts = route.actionName.split('.');
  return parts[parts.length - 1];
}

/**
 * Assigns each route a unique, human-readable slug for use in the URL hash.
 *
 * The slug is the action's simple class name (e.g. `MyAction`). If multiple
 * routes share a class name, the HTTP method is appended (e.g. `MyAction:GET`).
 * If routes still collide, the path is appended (e.g. `MyAction:GET:/my/path`).
 */
export function buildSlugIndex(routes: MiskRoute[]): SlugIndex {
  const byName = new Map<string, MiskRoute[]>();
  for (const route of routes) {
    const name = simpleName(route);
    byName.set(name, [...(byName.get(name) ?? []), route]);
  }

  const routeBySlug = new Map<string, MiskRoute>();
  const slugByRoute = new Map<MiskRoute, string>();
  const assign = (slug: string, route: MiskRoute) => {
    routeBySlug.set(slug, route);
    slugByRoute.set(route, slug);
  };

  for (const [name, group] of byName) {
    if (group.length === 1) {
      assign(name, group[0]);
      continue;
    }
    const byMethod = new Map<string, MiskRoute[]>();
    for (const route of group) {
      const key = `${name}:${route.httpMethod}`;
      byMethod.set(key, [...(byMethod.get(key) ?? []), route]);
    }
    for (const [methodSlug, methodGroup] of byMethod) {
      if (methodGroup.length === 1) {
        assign(methodSlug, methodGroup[0]);
        continue;
      }
      const byPath = new Map<string, MiskRoute[]>();
      for (const route of methodGroup) {
        const key = `${methodSlug}:${route.path}`;
        byPath.set(key, [...(byPath.get(key) ?? []), route]);
      }
      for (const [pathSlug, pathGroup] of byPath) {
        // Routes that are still ambiguous (e.g. same action bound twice with
        // different media types) get no slug: a link that could select the
        // wrong action is worse than no link.
        if (pathGroup.length === 1) {
          assign(pathSlug, pathGroup[0]);
        }
      }
    }
  }

  return { routeBySlug, slugByRoute };
}

/**
 * The app is rendered inside a same-origin iframe on the admin dashboard, so
 * the URL shown in the browser belongs to the top window, not to this frame.
 * Falls back to the current window if the top window is cross-origin (e.g.
 * when embedded somewhere unexpected).
 */
function addressBarWindow(): Window {
  try {
    // Accessing location on a cross-origin window throws.
    void window.top!.location.href;
    return window.top!;
  } catch {
    return window;
  }
}

export function readSlugFromUrl(): string | null {
  const hash = addressBarWindow().location.hash;
  if (!hash || hash === '#') {
    return null;
  }
  try {
    return decodeURIComponent(hash.substring(1));
  } catch {
    return null;
  }
}

export function writeSlugToUrl(slug: string): void {
  const target = addressBarWindow();
  // `:` and `/` are valid in a URL fragment; keep them readable.
  const encoded = encodeURIComponent(slug)
    .replace(/%3A/gi, ':')
    .replace(/%2F/gi, '/');
  try {
    // Preserve the existing history state: the dashboard uses Turbo, which
    // stores a restoration identifier there. Dropping it breaks Back/Forward.
    target.history.replaceState(target.history.state, '', `#${encoded}`);
  } catch {
    // Deep linking is best-effort; never break selection over it.
  }
}
