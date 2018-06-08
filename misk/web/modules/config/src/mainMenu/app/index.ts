import { createApp } from 'frint';
import { createStore } from 'frint-store';
const BrowserRouterService = require('frint-router/BrowserRouterService'); //require has to be used until it has typings

import { MenuComponent } from '../components';

export default createApp({
  name: 'ConfigApp Main Menu',
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
