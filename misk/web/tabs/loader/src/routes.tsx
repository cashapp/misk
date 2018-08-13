import * as React from "react"
import { Route, Switch } from "react-router"
import LoaderContainer from "./LoaderContainer"
import { NoMatchComponent } from "./NoMatchComponent"

const routes = (
  <Switch>
    <Route exact path="/_admin/" component={LoaderContainer}/>
    <Route component={NoMatchComponent}/>
  </Switch>
)

export default routes