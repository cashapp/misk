import * as React from "react"
import { Route, Switch } from "react-router"
import Loader from "./Loader"

const routes = (
  <div>
    <Switch>
      <Route component={Loader}/>
    </Switch>
  </div>
)

export default routes