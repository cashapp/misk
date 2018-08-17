import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { Route, Switch } from "react-router"
import { LoaderContainer } from "./containers"

const routes = (
  <Switch>
    <Route component={LoaderContainer}/>
  </Switch>
)

export default routes