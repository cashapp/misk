import * as React from "react"
import { Route, Switch } from "react-router"
import { TabContainer as TabContainerV1 } from "../containers"
import TabContainer from "../rewrite/TabContainer"

const routes = (
  <div>
    <Switch>
      <Route path="/_admin/web-actions/" component={TabContainer} />
      <Route path="/_admin/web-actions-v1/" component={TabContainerV1} />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
