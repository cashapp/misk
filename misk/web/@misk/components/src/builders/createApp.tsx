import { ConnectedRouter } from "connected-react-router"
import { History } from "history"
import * as React from "react"

export const createApp = (routes: JSX.Element) => {
  return ({ history }: { history: History }) => (
    <ConnectedRouter history={history}>{routes}</ConnectedRouter>
  )
}
