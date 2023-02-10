import * as React from "react"
import { Route, Switch } from "react-router"
import { TabContainer } from "src/containers"

const routes = (
  <div>
    <Switch>
      <Route path="/_admin/web-actions-v3/" component={TabContainer} />
      {/* Do not include a Route without a path or it will display during on all tabs: */}
      {/* <Route component={TabContainer} /> */}
    </Switch>
  </div>
)

export default routes
