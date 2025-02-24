import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';

export const MyAction: MiskWebActionDefinition = {
  name: 'MyAction',
  packageName: 'xyz.block',
  requestType: 'MyActionRequest',
  pathPattern: '/api/v1/my-action',
  requestMediaTypes: ['application/x-protobuf'],
  types: {
    MyActionRequest: {
      fields: [
        {
          name: 'text',
          type: 'String',
          repeated: false,
          annotations: [],
        },
        {
          name: 'enum',
          type: 'Enum<Type,A,B,C>',
          repeated: false,
          annotations: [],
        },
        {
          name: 'object',
          type: 'my-object-type',
          repeated: false,
          annotations: [],
        },
        {
          name: 's-array',
          type: 'String',
          repeated: true,
          annotations: [],
        },
      ],
    },
  },
};
