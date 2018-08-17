import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { Route, Switch } from "react-router"
import { NavContainer, TabContainer } from "../containers"

const routes = (
  <div>
    <NavContainer adminTabs={{}}/>
    {/* <TabContainer/> */}
      {/* <Switch>
        <Route component={NoMatchComponent}/>
      </Switch> */}
    {/* </TabContainer> */}
  </div>
)

export default routes