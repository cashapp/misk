import { createApp } from 'frint';
const BrowserRouterService = require('frint-router/BrowserRouterService'); //require has to be used until it has typings

import { RootContainer } from '../containers';

export default createApp({
  name: 'MiskApp',
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
