import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { Route, Switch } from "react-router"
import { TabContainer } from "../containers"

const routes = (
  <div>
    <Switch>
      <Route path="/_admin/config" component={TabContainer}/>
      <Route path="/_tab/config" component={TabContainer}/>
      <Route component={NoMatchComponent}/>
    </Switch>
  </div>
)

export default routes