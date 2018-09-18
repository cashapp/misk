import { Menu, MenuItem } from "@blueprintjs/core"
import { IMiskAdminTab } from "@misk/common"
import * as React from "react"
import styled from "styled-components" 

interface ISidebarProps {
  adminTabs: IMiskAdminTab[]
}

const Sidebar = styled.div`
  position: absolute;
`

const buildMenuItems = (adminTabs: IMiskAdminTab[]) => (
  adminTabs.map((tab) => <MenuItem key={tab.slug} href={tab.url_path_prefix} className="" text={`${tab.category} :: ${tab.name}`}/>)
)

export const SidebarComponent = (props: ISidebarProps) => (
  <Sidebar>
    <Menu>
      {buildMenuItems(props.adminTabs)}
    </Menu>
  </Sidebar>
)
