import {
  ActionGroup,
  MiskActions,
  MiskMetadataResponse,
  MiskWebActionDefinition,
} from '@web-actions/api/responseTypes';
import { fetchCached } from '@web-actions/network/http';
import MetadataClient from '@web-actions/api/MetadataClient';

function containsJsonOrAny(mediaType: string[]) {
  return (
    mediaType.length == 0 ||
    mediaType.some((it) => it.startsWith('application/json')) ||
    mediaType.some((it) => it.startsWith('*/*'))
  );
}

export default class RealMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<MiskActions> {
    const response = await fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`,
    );
    const actionMap: Record<string, ActionGroup> = {};

    response.all['web-actions'].metadata.forEach((it) => {
      it.requestType = it.requestType === 'null' ? null : it.requestType;

      const qualifiedName = `${it.packageName}.${it.name}`;
      const groupKey = `${it.httpMethod} ${it.pathPattern} ${qualifiedName}`;
      let group = actionMap[groupKey];

      if (group === undefined) {
        group = {
          actionName: qualifiedName,
          path: it.pathPattern,
          httpMethod: it.httpMethod || '',
          callables: [],
          all: [],
          responseMediaTypes: [],
          requestMediaTypes: [],
          canCall: false,
          types: it.types,
          requestType: it.requestType,
        };
        actionMap[groupKey] = group;
      }

      if (it.httpMethod === 'GET' || containsJsonOrAny(it.requestMediaTypes)) {
        group.canCall = true;
        group.callables.push(it);
      }

      group.requestMediaTypes.push(...it.requestMediaTypes);
      group.responseMediaTypes.push(it.responseMediaType);

      group.all.push(it);
    });

    return actionMap;
  }
}
