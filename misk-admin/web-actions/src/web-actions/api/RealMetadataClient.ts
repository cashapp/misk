import {
  ActionGroup,
  MiskActions,
  MiskMetadataResponse,
} from '@web-actions/api/responseTypes';
import { fetchCached } from '@web-actions/network/http';
import MetadataClient from '@web-actions/api/MetadataClient';

export default class RealMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<MiskActions> {
    const response = await fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`,
    );
    const actionMap: Record<string, ActionGroup> = {};
    response.all['web-actions'].metadata.forEach((it) => {
      let group = actionMap[it.name];
      if (group === undefined) {
        group = { name: it.name, all: [] };
        actionMap[it.name] = group;
      }

      if (
        it.requestMediaTypes.some((mediaType) =>
          mediaType.startsWith('application/json'),
        )
      ) {
        group.defaultCallable = it;
      }

      group.all.push(it);
    });

    return actionMap;
  }
}
