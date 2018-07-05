import * as React from "react"
import { Route, Switch } from "react-router"
import { NavContainer, TabContainer } from "../containers";

const routes = (
  <div>
    <NavContainer/>
    <Switch>
      <Route component={TabContainer} />
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