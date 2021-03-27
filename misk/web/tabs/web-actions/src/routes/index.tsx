import * as React from "react"
import { Route, Switch } from "react-router"
import { TabContainer as OldTabContainer } from "../containers"
import TabContainer from "../rewrite/TabContainer"

const routes = (
  <div>
    <Switch>
      <Route path="/_admin/web-actions/" component={TabContainer} />
      <Route path="/_admin/web-actions-old/" component={OldTabContainer} />
      {/* Do not include a Route without a path or it will display during on all tabs */}
    </Switch>
  </div>
)

export default routes
