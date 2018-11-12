import * as React from "react"
import { Route, Switch } from "react-router"
import { LoaderContainer } from "./containers"

const routes = (
  <span>
    <Switch>
      <Route component={LoaderContainer} />
    </Switch>
  </span>
)

export default routes
