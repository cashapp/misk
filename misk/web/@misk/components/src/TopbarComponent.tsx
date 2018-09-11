import { Alignment, Button, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { Link } from "react-router-dom"
import styled from "styled-components"
import { ResponsiveContainer } from "."

export interface ITopbarProps {
  homeName: string
  homeUrl: string
  links?: IMiskAdminTabs
  menuButtonShow?: boolean
}

const MiskNavbar = styled(Navbar)`
  background-color: #29333a;
  min-height: 74px;
  margin-bottom: 20px;
  box-sizing: border-box;
  border: 1px solid #14191c;
  padding-top: 10px;
  position: fixed;
  top: 0px;
  
  a {
    color: #9da2a6;
    text-decoration: none;
  }
  a:hover {
    color: #fff;
    text-decoration: none;
  }
`

const MiskNavbarGroup = styled(NavbarGroup)`
  font-size: 13px !important;
  font-weight: 600 !important;
  line-height: 20px;
  padding-left: 40px;
  padding-right: 15px;
  position: relative;
`

const MiskNavbarHeading = styled(NavbarHeading)`
  font-size: 18px;
  letter-spacing: 0px;
  padding-top: 25px;
  padding-bottom: 27px;
`

const MiskNavbarLink = styled(Link)`
  font-size: 13px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 1px;
  padding-top: 27px;
  padding-bottom: 27px;
`

const MiskNavbarDivider = styled(NavbarDivider)`
  border: none
`

const MiskMenuButton = styled(Button)`
  background-color: #29333a !important;
  box-shadow: none !important;
  background-image: none !important;
  top: 15px;
  left: 15px;
  position: absolute;
`

// TODO...create new MiskBinders like abstraction that lives in window for key/value state store to toggle open/close
// TODO...^ once this determined, have MiskMenuButton icon deteremined on that boolean value close/open

export class TopbarComponent extends React.Component<ITopbarProps, {}> {
  public state = {
    isOpen: false
  }

  render() {
    const { homeName, homeUrl, links, menuButtonShow } = this.props
    return(
      <MiskNavbar>
        {menuButtonShow === true ? <MiskMenuButton icon={this.state.isOpen ? IconNames.MENU : IconNames.CROSS} onClick={this.handleClick}/>:<div/>}
        <ResponsiveContainer>
          <MiskNavbarGroup align={Alignment.LEFT} className="bp3-dark">
            <MiskNavbarLink to={homeUrl}>
              <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
            </MiskNavbarLink>
            <MiskNavbarDivider/>
            {links ? this.MiskNavbarLinks(links) : <span>Loading...</span>}
          </MiskNavbarGroup>
        </ResponsiveContainer>
      </MiskNavbar>
    )
  }

  private MiskNavbarLinks = (links: IMiskAdminTabs) => (
    Object.entries(links).map(([key, tab]) => <MiskNavbarLink key={key} to={tab.url_path_prefix}>{tab.name}</MiskNavbarLink>)
  )

  private handleClick = () => {
    this.setState({...this.state, isOpen: !this.state.isOpen})
  }
}
  