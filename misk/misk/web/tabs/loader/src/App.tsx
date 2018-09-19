import { ConnectedRouter } from "connected-react-router"
import { History } from "history"
import * as React from "react"
import routes from "./routes"

interface IAppProps {
  history: History
}

const App = ({ history }: IAppProps) => {
  return (
    <ConnectedRouter history={history}>
      {routes}
    </ConnectedRouter>
  )
}

export default App