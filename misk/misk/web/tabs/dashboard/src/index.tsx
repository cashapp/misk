import { connectRouter, routerMiddleware } from "connected-react-router"
import { createBrowserHistory } from "history"
import * as React from "react"
import * as ReactDOM from "react-dom"
import { AppContainer } from "react-hot-loader"
import { Provider } from "react-redux"
import { applyMiddleware, compose, createStore } from "redux"
import App from "./App"
import rootReducer from "./reducers"

export * from "./containers"
export * from "./components"

export interface IAppState {
  count: number,
  router: {
    location: {
      pathname: string,
      search: string,
      hash: string
    },
    action: string
  }
}

const history = createBrowserHistory()

const composeEnhancer: typeof compose = (window as any).__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose
const store = createStore(
  connectRouter(history)(rootReducer),
  composeEnhancer(
    applyMiddleware(
      routerMiddleware(history),
    ),
  ),
)

const render = () => {
  ReactDOM.render(
    <AppContainer>
      <Provider store={store}>
        <App history={history} />
      </Provider>
    </AppContainer>,
    document.getElementById("dashboard")
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