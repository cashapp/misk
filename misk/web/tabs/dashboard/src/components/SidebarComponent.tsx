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
  Object.entries(adminTabs).map((tab) => <MenuItem key={tab[0]} href={tab[1].url_path_prefix} className="" icon={tab[1].icon} text={tab[1].name}/>)
)

export const SidebarComponent = (props: ISidebarProps) => (
  <Sidebar>
    <Menu>
      {buildMenuItems(props.adminTabs)}
    </Menu>
  </Sidebar>
)
