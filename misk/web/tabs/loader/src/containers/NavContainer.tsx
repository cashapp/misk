import * as React from "react"
import { SidebarComponent, TopbarComponent } from "../components";

export class NavContainer extends React.Component {
  render() {
    return (
      <div>
        <TopbarComponent name="Misk Admin"/>
        {/* <SidebarComponent menuItems={}/> */}
      </div>
    )
  }
}
