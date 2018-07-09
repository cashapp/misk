import * as React from "react"
import { Helmet } from "react-helmet"



export class DashboardContainer extends React.Component {
  render() {
    return (
      <Helmet>
        <script async src="/_admin/dashboard/tab_dashboard.js" type="text/javascript" />        
      </Helmet>
    )
  }
}
