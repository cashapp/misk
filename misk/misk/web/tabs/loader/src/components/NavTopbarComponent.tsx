import { Alignment, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import * as React from "react"

export interface ITopbarProps {
  name: string
}

export const NavTopbarComponent = (props: ITopbarProps) => (
  <Navbar>
    <NavbarGroup align={Alignment.LEFT}>
      <NavbarHeading>{props.name}</NavbarHeading>
      <NavbarDivider/>
    </NavbarGroup>
  </Navbar>
)