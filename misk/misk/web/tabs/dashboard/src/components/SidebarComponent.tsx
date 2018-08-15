import { Menu, MenuItem } from "@blueprintjs/core"
import { IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import styled from "styled-components" 

interface ISidebarProps {
  adminTabs: IMiskAdminTabs
}

const Sidebar = styled.div`
  position: absolute;
`

const buildMenuItems = (adminTabs: IMiskAdminTabs) => (
  Object.entries(adminTabs).map(([key, tab]) => <MenuItem key={key} href={tab.url_path_prefix} className="" icon={tab.icon} text={tab.name}/>)
)

export const SidebarComponent = (props: ISidebarProps) => (
  <Sidebar>
    <Menu>
      {buildMenuItems(props.adminTabs)}
    </Menu>
  </Sidebar>
)
