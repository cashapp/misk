import * as React from "react"
import { Route, Switch } from "react-router"
import { NoMatchComponent } from "../components"
import { NavContainer, TabContainer } from "../containers"

const routes = (
  <div>
    <NavContainer/>
    <TabContainer>
      <Switch>
        <Route path="/_admin/dashboard/" component={NoMatchComponent}/>
      </Switch>
    </TabContainer>
  </div>
)

export default routes