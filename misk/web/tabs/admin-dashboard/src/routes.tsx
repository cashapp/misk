import * as React from "react"
import { Route, Switch } from "react-router"
import { AdminDashboardContainer } from "./containers"

const routes = (
  <span>
    <Switch>
      <Route component={AdminDashboardContainer} />
    </Switch>
  </span>
)

export default routes
