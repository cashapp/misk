import { Alignment, Button, Collapse, Icon, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
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
  z-index: 1010;
  
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
  padding-left: 15px;
  padding-right: 15px;
  position: relative;
`

const MiskNavbarHeading = styled(NavbarHeading)`
  font-size: 24px;
  letter-spacing: 0px;
  padding-top: 25px;
  padding-bottom: 27px;
`

const MiskNavbarLink = styled(Link)`
  font-size: 16px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 1px;
  padding-top: 30px;
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
  z-index: 1020;
`

const MiskMenuIcon = styled(Icon)`
  color: #9da2a6 !important;

  &:hover {
    color: #fff;
  }
`

const MiskCollapse = styled(Collapse)`
  color: #fff;
  background-color: #29333a;
  display: block;
  margin: 0 -20px;
  margin-top: 63px;
  z-index: 1000;
`

const MiskMenu = styled.div`
  min-height: 250px;
  padding: 30px;
  z-index: 1001;
`

export class TopbarComponent extends React.Component<ITopbarProps, {}> {
  public state = {
    isOpen: false
  }

  public render() {
    const { isOpen } = this.state
    const { homeName, homeUrl, links, menuButtonShow } = this.props
    return(
      <MiskNavbar>
        {menuButtonShow === true ? <MiskMenuButton onClick={this.handleClick}><MiskMenuIcon iconSize={32} icon={isOpen ? IconNames.CROSS : IconNames.MENU}/></MiskMenuButton>:<div/>}
        <ResponsiveContainer>
        <MiskNavbarGroup align={Alignment.LEFT} className="bp3-dark">
          <MiskNavbarDivider/>
          <MiskNavbarDivider/>
          <MiskNavbarLink to={homeUrl}>
            <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
          </MiskNavbarLink>
        </MiskNavbarGroup>
        </ResponsiveContainer>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <ResponsiveContainer>
            <MiskMenu>
              {links ? Object.entries(links).map(([,link]) => <MiskNavbarLink onClick={this.handleClick} to={link.url_path_prefix} key={link.slug}>{link.name}</MiskNavbarLink>) : <span>Loading...</span>}
            </MiskMenu>
          </ResponsiveContainer>
        </MiskCollapse>
      </MiskNavbar>
    )
  }

  private handleClick = () => {
    this.setState({...this.state, isOpen: !this.state.isOpen})
  }
}
  