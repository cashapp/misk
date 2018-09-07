import { Alignment, Navbar, NavbarDivider, NavbarGroup, NavbarHeading } from "@blueprintjs/core"
import { IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { Link } from "react-router-dom"
import styled from "styled-components"
import { ResponsiveContainer } from "."

export interface ITopbarProps {
  name: string
  home: string
  links: IMiskAdminTabs
}

const MiskNavbar = styled(Navbar)`
  background-color: #29333a !important;
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
  padding-left: 15px;
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

const MiskNavbarLinks = (links: IMiskAdminTabs) => (
  Object.entries(links).map(([key, tab]) => <MiskNavbarLink key={key} to={tab.url_path_prefix}>{tab.name}</MiskNavbarLink>)
)

export const NavTopbarComponent = (props: ITopbarProps) => (
  <MiskNavbar className="bp3-dark">
    <ResponsiveContainer>
      <MiskNavbarGroup align={Alignment.LEFT}>
        <MiskNavbarLink to={props.home}>
          <MiskNavbarHeading>{props.name}</MiskNavbarHeading>
        </MiskNavbarLink>
        <MiskNavbarDivider/>
        {MiskNavbarLinks(props.links)}
      </MiskNavbarGroup>
    </ResponsiveContainer>
  </MiskNavbar>
)