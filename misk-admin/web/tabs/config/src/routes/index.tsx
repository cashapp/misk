import * as React from "react"
import { Route, Switch } from "react-router"
import { TabContainer } from "../containers"

const routes = (
  <div>
    <Switch>
      <Route path="/_admin/config/" component={TabContainer} />
      <Route
        path="/api/dashboard/tab/misk-web/config/"
        component={TabContainer}
      />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
