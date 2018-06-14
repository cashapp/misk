import { createApp } from 'frint';
import { createStore } from 'frint-store';
const BrowserRouterService = require('frint-router/BrowserRouterService');

import { RootContainer } from '../containers';
import { rootReducer } from '../reducers';
import { COLORS } from '../constants';

export default createApp({
  name: 'MiskModuleHealthcheck',
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
            color: {
              value: COLORS.DEFAULT,
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
