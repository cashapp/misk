import { Alignment, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import * as React from "react"
import { Link } from "react-router-dom";

export interface ITopbarProps {
  name: string
  home: string
}

export const NavTopbarComponent = (props: ITopbarProps) => (
  <Navbar>
    <NavbarGroup align={Alignment.LEFT}>
      <Link to={props.home}>
        <NavbarHeading>{props.name}</NavbarHeading>
      </Link>
      <NavbarDivider/>
    </NavbarGroup>
  </Navbar>
)