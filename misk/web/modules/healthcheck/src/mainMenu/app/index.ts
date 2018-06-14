import { createApp } from 'frint';
import { createStore } from 'frint-store';
const BrowserRouterService = require('frint-router/BrowserRouterService');

import { MenuComponent } from '../components';

export default createApp({
  name: 'MiskMenuHealthcheck',
  providers: [
    {
      name: 'component',
      useValue: MenuComponent,
    },
    {
      name: 'store',
      useFactory: function ({ app }) {
        const Store = createStore({
          initialState: {
          },
        });
        return new Store();
      },
      deps: ['app'],
    }, {
      name: 'router',
      useFactory: function () {
        return new BrowserRouterService();
      },
      cascade: true,
    }
  ],
});
