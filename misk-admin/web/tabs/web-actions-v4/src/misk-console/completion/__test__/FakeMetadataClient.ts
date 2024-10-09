import MetadataClient from "@misk-console/api/MetadataClient"
import { MiskWebActionDefinition } from "@misk-console/api/responseTypes"

export default class FakeMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<Record<string, MiskWebActionDefinition>> {
    return Promise.resolve({
      MyAction: {
        name: "MyAction",
        requestType: "MyActionRequest",
        pathPattern: "/api/v1/my-action",
        types: {
          MyActionRequest: {
            fields: [
              {
                name: "text",
                type: "String",
                repeated: false,
                annotations: []
              },
              {
                name: "enum",
                type: "Enum<Type,A,B,C>",
                repeated: false,
                annotations: []
              },
              {
                name: "object",
                type: "my-object-type",
                repeated: false,
                annotations: []
              },
              {
                name: "s-array",
                type: "String",
                repeated: true,
                annotations: []
              }
            ],
            "my-object-type": {
              fields: []
            }
          }
        }
      }
    })
  }
}

export class FakeObjectMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<Record<string, MiskWebActionDefinition>> {
    return Promise.resolve({
      MyAction: {
        name: "MyAction",
        requestType: "MyActionRequest",
        pathPattern: "/api/v1/my-action",
        types: {
          MyActionRequest: {
            fields: [
              {
                name: "object",
                type: "my-object-type",
                repeated: false,
                annotations: []
              },
              {
                name: "s-array",
                type: "String",
                repeated: true,
                annotations: []
              }
            ]
          },
          "my-object-type": {
            fields: []
          }
        }
      }
    })
  }
}
