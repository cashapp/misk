import { createBrowserHistory } from "history"
import React from "react"
import { Provider } from "react-redux"
import { render } from "@testing-library/react"
import { createStore } from "redux"
import { rootReducer } from "../src/ducks"

// this is a handy function that I normally make available for all my tests
// that deal with connected components.
// you can provide initialState or the entire store that the ui is rendered with
export const renderWithRedux = (
  ui: React.ReactElement<
    any,
    | string
    | ((
      props: any
    ) => React.ReactElement<
      any,
      string | (new (props: any) => React.Component<any, any, any>)
    >)
    | (new (props: any) => React.Component<any, any, any>)
  >,
  {
    initialState,
    store = createStore(rootReducer(createBrowserHistory()), initialState)
  }: any = {}
) => {
  return {
    ...render(<Provider store={store}>{ui}</Provider>),
    // adding `store` to the returned utilities to allow us
    // to reference it in our tests (just try to avoid using
    // this to test implementation details).
    store
  }
}
