import * as React from "react"
import { Route, Switch } from "react-router"
import { NoMatchComponent } from "../components"
import { DashboardContainer, TabContainer } from "../containers"

const routes = (
  <div>
    <DashboardContainer/>
    <Switch>
      <Route path="/_admin/config/" component={TabContainer}/>
    </Switch>
  </div>
)

export default routes

// const routes =  [{
//   exact: true,
//   path: "/_admin",
// }, {
//   exact: true,
//   path: "/_admin/:moduleID",
// }]