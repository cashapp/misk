import { Alignment, Button, Collapse, Icon, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { IMiskAdminTab, IMiskAdminTabCategories } from "@misk/common"
import * as React from "react"
import { Link } from "react-router-dom"
import styled from "styled-components"
import { ResponsiveContainer } from "."

export interface ITopbarProps {
  homeName: string
  homeUrl: string
  links?: IMiskAdminTabCategories
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
`

const MiskNavbarGroup = styled(NavbarGroup)`
  font-size: 13px !important;
  font-weight: 600 !important;
  line-height: 20px;
  position: relative;
  color: #9da2a6;
  &:hover {
    color: #fff;
    text-decoration: none;
  }
  @media (max-width: 860px) {
    padding-left: 50px;
  }
  @media (min-width: 992px) and (max-width: 1075px) {
    padding-left: 50px;
  }
  @media (min-width: 1200px) and (max-width: 1275px) {
    padding-left: 50px;
  }
`

const MiskNavbarHeading = styled(NavbarHeading)`
  font-size: 24px;
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 0px;
  padding-top: 25px;
  padding-bottom: 27px;
  color: #cecece
`

const MiskLink = styled(Link)`
  font-size: 16px;
  font-weight: 500;
  color: #9da2a6;
  text-decoration: none;
  letter-spacing: 1px;
  &:hover {
    color: #fff;
    text-decoration: none;
  }
`

const MiskNavbarLink = styled(MiskLink)`
  text-transform: uppercase;
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
  margin: 60px -20px 0 -20px;
`

const MiskMenu = styled.div`
  min-height: 250px;
  @media (max-width: 768px) {
    padding: 0 20px 20px 20px;
  }
`

const MiskMenuLinks = styled.div`
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding-bottom: 35px;
`

const MiskMenuLink = styled(MiskLink)`
  flex-basis: 300px;
  padding: 5px 0;
  color: #cecece;
`

const MiskMenuCategory = styled.span`
  font-size: 24px;
  color: #9da2a6;
  letter-spacing: 0px;  
  display: block;
`

const MiskMenuDivider = styled.hr`
  border-color: #9da2a6;
  margin: 5px 0 10px 0;
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
          <MiskNavbarLink to={homeUrl}><MiskNavbarHeading>{homeName}</MiskNavbarHeading></MiskNavbarLink>
          <MiskNavbarDivider/>
        </MiskNavbarGroup>
        </ResponsiveContainer>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <ResponsiveContainer>
            <MiskMenu>
              {links ? Object.entries(links).map(([categoryName,categoryLinks]) => this.renderMenuCategory(categoryName,categoryLinks)) : <span>Loading...</span>}
            </MiskMenu>
          </ResponsiveContainer>
        </MiskCollapse>
      </MiskNavbar>
    )
  }

  private renderMenuCategory(name: string, links: IMiskAdminTab[]) {
    return (
      <div>
        <MiskMenuCategory>{name}</MiskMenuCategory>
        <MiskMenuDivider/>
        <MiskMenuLinks>
          {links.map((link: IMiskAdminTab) => <MiskMenuLink key={link.slug} onClick={this.handleClick} to={link.url_path_prefix}>{link.name}</MiskMenuLink>)}
        </MiskMenuLinks>
      </div>
    )

  }

  private handleClick = () => {
    this.setState({...this.state, isOpen: !this.state.isOpen})
  }
}
  