import { createApp } from 'frint';
const BrowserRouterService = require('frint-router/BrowserRouterService');

import { RootContainer } from '../containers';

export default createApp({
  name: 'MiskMain',
  providers: [
    {
      name: 'component',
      useValue: RootContainer,
    }, {
      name: 'router',
      useFactory: function () {
        return new BrowserRouterService();
      },
      cascade: true,
    }
  ],
});
