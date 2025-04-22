import {
  MiskRoute,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';
import { MediaTypes } from '@web-actions/api/MediaTypes';
import { parseNull } from '@web-actions/utils/common';

export function buildRoutes(actions: MiskWebActionDefinition[]): MiskRoute[] {
  const routeMap: Record<string, MiskRoute> = {};

  for (const it of actions) {
    const qualifiedName = `${it.packageName}.${it.name}`;
    const groupKey = `${it.httpMethod} ${it.pathPattern} ${qualifiedName}`;
    let route = routeMap[groupKey];

    if (route === undefined) {
      route = {
        actionName: qualifiedName,
        path: it.pathPattern,
        httpMethod: it.httpMethod || '',
        responseMediaTypes: new MediaTypes(),
        requestMediaTypes: new MediaTypes(),
        types: { ...it.returnTypes, ...it.types },
        requestType: it.requestType,
        returnType: it.returnType,
        allowedServices: [],
        allowedCapabilities: [],
        all: [],
      };
      routeMap[groupKey] = route;
    }
    route.requestMediaTypes.push(...it.requestMediaTypes);
    route.responseMediaTypes.push(it.responseMediaType);
    route.allowedServices.push(...it.allowedServices);
    route.allowedCapabilities.push(...it.allowedCapabilities);
    route.all.push({
      ...it,
      requestType: parseNull(it.requestType),
    });
  }

  const routes = Object.values(routeMap);

  for (const group of routes) {
    if (group.httpMethod === 'GET') {
      group.callable = true;
    } else if (group.httpMethod === 'PUT' || group.httpMethod === 'POST') {
      if (
        group.requestMediaTypes.hasJson() ||
        group.requestMediaTypes.hasAny() ||
        group.requestMediaTypes.isUnspecified()
      ) {
        group.callable = true;
      }
    }
  }
  return routes;
}
