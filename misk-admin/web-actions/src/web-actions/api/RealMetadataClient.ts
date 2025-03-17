import {
  ActionGroup,
  MiskActions,
  MiskMetadataResponse,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';
import { fetchCached } from '@web-actions/network/http';
import MetadataClient from '@web-actions/api/MetadataClient';

function containsJson(mediaType: string[]) {
  return mediaType.some((it) => it.startsWith('application/json'));
}

export default class RealMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<MiskActions> {
    const response = await fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`,
    );
    const actionMap: Record<string, ActionGroup> = {};

    response.all['web-actions'].metadata.forEach((it) => {
      const qualifiedName = `${it.packageName}.${it.name}`;
      const groupKey = `${it.httpMethod} ${it.pathPattern} ${qualifiedName}`;
      let group = actionMap[groupKey];
      if (group === undefined) {
        group = {
          actionName: qualifiedName,
          path: it.pathPattern,
          httpMethod: it.httpMethod || '',
          callables: {},
          getCallablesByMethod(): MiskWebActionDefinition[] {
            return [
              this.callables['POST'],
              this.callables['PUT'],
              this.callables['GET'],
            ].filter((it) => it !== undefined);
          },
          all: [],
        };
        actionMap[groupKey] = group;
      }

      function maybeAddCallable(
        method: string,
        predicate: (it: MiskWebActionDefinition) => boolean = () => true,
      ) {
        if (
          it.httpMethod === method &&
          group.callables[method] === undefined &&
          predicate(it)
        ) {
          group.callables[method] = it;
        }
      }

      maybeAddCallable('POST', (it) => containsJson(it.requestMediaTypes));
      maybeAddCallable('PUT', (it) => containsJson(it.requestMediaTypes));
      maybeAddCallable('GET');

      group.all.push(it);
    });

    return actionMap;
  }
}
