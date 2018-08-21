import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { Route, Switch } from "react-router"
import { DashboardContainer, TabContainer } from "../containers"

const routes = (
  <div>
    {/* <DashboardContainer/> */}
    <Switch>
      <Route path="/_admin/config" component={TabContainer}/>
      <Route path="/_tab/config" component={TabContainer}/>
      <Route component={NoMatchComponent}/>
    </Switch>
  </div>
)

export default routes