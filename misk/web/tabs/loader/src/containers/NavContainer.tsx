import * as React from "react"
import { SidebarComponent, TopbarComponent } from "../components";
import menuItems from "../utils/menu";

export class NavContainer extends React.Component {
  render() {
    return (
      <div>
        <TopbarComponent name="Misk Admin"/>
        <SidebarComponent menuItems={menuItems}/>
      </div>
    )
  }
}
