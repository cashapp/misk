import { Menu, MenuItem } from "@blueprintjs/core"
import * as React from "react"
import styled from "styled-components" 
import { IMenuItem } from "../utils/menu"

interface ISidebarProps {
  menuItems: IMenuItem[]
}

const Sidebar = styled.div`
  position: absolute;
`

export class SidebarComponent extends React.Component<ISidebarProps, {}> {
  private renderedMenuItems: any

  constructor(props: ISidebarProps) {
    super(props)
    this.renderedMenuItems = this.props.menuItems.map(
      ({ text, icon="document", className="pt-minimal", url } : any) => (
          <MenuItem key={url} href={url} className={className} icon={icon} text={text}/>
        )
      )
  }

  render() {
    return (
      <Sidebar>
        <Menu>
          {this.renderedMenuItems}
        </Menu>
      </Sidebar>
    )
  }
}
