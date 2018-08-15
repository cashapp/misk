import { IMiskAdminTabs } from "@misk/common"
import { connectRouter, routerMiddleware } from "connected-react-router"
import { createBrowserHistory } from "history"
import * as React from "react"
import * as ReactDOM from "react-dom"
import { AppContainer } from "react-hot-loader"
import { Provider } from "react-redux"
import { applyMiddleware, compose, createStore } from "redux"
import createSagaMiddleware from "redux-saga"
import App from "./App"
import { ILoaderState } from "./containers/LoaderContainer"
import rootReducer from "./reducers"
import rootSaga from "./sagas"

export interface IAppState {
  adminTabs: IMiskAdminTabs
  loader: ILoaderState
  router: IRouterState
}

export interface IRouterState {
  location: {
    pathname: string
    search: string
    hash: string
  }
  action: string
}

const history = createBrowserHistory()
const sagaMiddleware = createSagaMiddleware()

const composeEnhancer: typeof compose = (window as any).__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose
const store = createStore(
  connectRouter(history)(rootReducer),
  composeEnhancer(
    applyMiddleware(
      sagaMiddleware,
      routerMiddleware(history),
    ),
  ),
)

/**
 * Starts the rootSaga which forks off instances of all sagas used to receive and process actions as they are dispatched (./sagas/index.ts)
 */
sagaMiddleware.run(rootSaga)

const render = () => {
  ReactDOM.render(
    <AppContainer>
      <Provider store={store}>
        <App history={history} />
      </Provider>
    </AppContainer>,
    document.getElementById("loader")
  )
}

render()

// Hot reloading
if (module.hot) {
  // Reload components
  module.hot.accept("./App", () => {
    render()
  })

  // Reload reducers
  module.hot.accept("./reducers", () => {
    store.replaceReducer(connectRouter(history)(rootReducer))
  })
}