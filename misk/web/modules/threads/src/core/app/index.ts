import { createApp } from 'frint';
import { createStore } from 'frint-store';
const BrowserRouterService = require('frint-router/BrowserRouterService');

import { RootContainer } from '../containers';
import rootReducer from '../reducers';

export default createApp({
  name: 'ThreadsApp',
  providers: [
    {
      name: 'component',
      useValue: RootContainer,
    },
    {
      name: 'store',
      useFactory: function ({ app }) {
        const Store = createStore({
          initialState: {
            counter: {
              value: 0,
            },
          },
          reducer: rootReducer,
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
