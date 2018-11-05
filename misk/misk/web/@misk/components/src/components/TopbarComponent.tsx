import {
  Alignment,
  Button,
  Collapse,
  Icon,
  Navbar,
  NavbarDivider,
  NavbarGroup,
  NavbarHeading
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { IDashboardTab } from "@misk/common"
import * as React from "react"
import { Link } from "react-router-dom"
import styled from "styled-components"
import { ResponsiveContainer } from "../containers"

/**
 * <TopbarComponent homeName={"Service Name"} homeUrl={"/_admin/"} links={props.tabs}/>
 */

export interface ITopbarProps {
  homeName?: string
  homeUrl?: string
  links?: IDashboardTab[]
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
  @media (max-width: 870px) {
    padding-left: 60px;
  }
  @media (min-width: 992px) and (max-width: 1085px) {
    padding-left: 60px;
  }
  @media (min-width: 1200px) and (max-width: 1285px) {
    padding-left: 60px;
  }
`

const MiskNavbarHeading = styled(NavbarHeading)`
  font-size: 24px;
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 0px;
  padding-top: 25px;
  padding-bottom: 27px;
  color: #cecece;
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
  border: none;
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
    const { homeName, homeUrl, links } = this.props
    return (
      <MiskNavbar>
        <MiskMenuButton onClick={this.handleClick}>
          <MiskMenuIcon
            iconSize={32}
            icon={isOpen ? IconNames.CROSS : IconNames.MENU}
          />
        </MiskMenuButton>
        <ResponsiveContainer>
          <MiskNavbarGroup align={Alignment.LEFT} className="bp3-dark">
            {this.renderMenuLink(homeName, homeUrl)}
            <MiskNavbarDivider />
          </MiskNavbarGroup>
        </ResponsiveContainer>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <ResponsiveContainer>
            <MiskMenu>
              {links ? (
                this.renderMenuCategories(links)
              ) : (
                <span>Loading...</span>
              )}
            </MiskMenu>
          </ResponsiveContainer>
        </MiskCollapse>
      </MiskNavbar>
    )
  }

  private renderMenuLink(homeName: string, homeUrl: string) {
    if (homeName && homeUrl) {
      return (
        <MiskNavbarLink to={homeUrl}>
          <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
        </MiskNavbarLink>
      )
    } else if (homeName) {
      return <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
    } else {
      return <MiskNavbarHeading>Misk</MiskNavbarHeading>
    }
  }

  private renderMenuCategories(links: IDashboardTab[]) {
    const categories: Array<[string, IDashboardTab[]]> = Object.entries(
      this.groupBy(links, "category")
    )
    return categories.map(([categoryName, categoryLinks]) =>
      this.renderMenuCategory(categoryName, categoryLinks)
    )
  }

  private renderMenuCategory(
    categoryName: string,
    categoryLinks: IDashboardTab[]
  ) {
    const sortedCategoryLinks = this.sortBy(categoryLinks, "name").filter(
      (link: IDashboardTab) => link.category !== ""
    )
    return (
      <div>
        <MiskMenuCategory>{categoryName}</MiskMenuCategory>
        <MiskMenuDivider />
        <MiskMenuLinks>
          {sortedCategoryLinks.map((link: IDashboardTab) => (
            <MiskMenuLink
              key={link.slug}
              onClick={this.handleClick}
              to={link.url_path_prefix}
            >
              {link.name}
            </MiskMenuLink>
          ))}
        </MiskMenuLinks>
      </div>
    )
  }

  private handleClick = () => {
    this.setState({ ...this.state, isOpen: !this.state.isOpen })
  }

  private groupBy = (items: any, key: any) =>
    items.reduce(
      (result: any, item: any) => ({
        ...result,
        [item[key]]: [...(result[item[key]] || []), item]
      }),
      {}
    )

  private sortBy = (items: any, key: any) =>
    items.sort((item1: any, item2: any) => {
      if (item1[key] < item2[key]) {
        return -1
      } else if (item1[key] > item2[key]) {
        return 1
      } else {
        return 0
      }
    })
}
