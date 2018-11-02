import * as React from "react"
import { Route, Switch } from "react-router"
import { LoaderContainer } from "./containers"

const routes = (
  <div>
    <Switch>
      <Route component={LoaderContainer} />
    </Switch>
  </div>
)

export default routes
