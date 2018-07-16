import { Alignment, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import * as React from "react"

export interface ITopbarProps {
  name: string
}

export class TopbarComponent extends React.Component<ITopbarProps, {}> {
  constructor(props: ITopbarProps) {
    super(props)
  }

  render() {
    return (
      <Navbar>
        <NavbarGroup align={Alignment.LEFT}>
          <NavbarHeading>{this.props.name}</NavbarHeading>
          <NavbarDivider/>
        </NavbarGroup>
      </Navbar>
    )
  }
}
