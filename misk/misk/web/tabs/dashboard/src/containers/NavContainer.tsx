import { IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { SidebarComponent, TopbarComponent } from "../components"

interface INavProps {
  adminTabs?: IMiskAdminTabs
}

const NavContainer = (props: INavProps) => (
  <div>
    <TopbarComponent name="Misk Admin"/>
    <SidebarComponent adminTabs={props.adminTabs}/>
  </div>
)

export { NavContainer }