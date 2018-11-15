import {
  Alignment,
  Button,
  Collapse,
  Icon,
  Navbar,
  NavbarGroup
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { color, Environment, IDashboardTab } from "@misk/common"
import { groupBy, sortBy } from "lodash"
import * as React from "react"
import { Link } from "react-router-dom"
import styled from "styled-components"
import { IDimensionAwareProps } from "../components"
import { FlexContainer, ResponsiveContainer } from "../containers"
import { ErrorCalloutComponent } from "./ErrorCalloutComponent"
import { TextHTMLOrElementComponent } from "./TextHTMLOrElementComponent"

/**
 * <TopbarComponentDimensionAware
 *    height={this.state.height}
 *    width={this.state.width}
 *    environment={this.props.environment}
 *    environmentBannerVisible={this.props.environmentBannerVisible}
 *    error={this.props.error}
 *    homeName={this.props.homeName}
 *    homeUrl={this.props.homeUrl}
 *    links={this.props.links}
 *    navbarItems={this.props.navbarItems}
 *    status={this.props.status}
 *  />
 */

export interface ITopbarProps {
  environment?: Environment
  environmentBannerVisible?: Environment[]
  error?: any
  homeName?: string
  homeUrl?: string
  navbarItems?: Array<string | Element | JSX.Element>
  links?: IDashboardTab[]
  status?: string | Element | JSX.Element
}

const MiskNavbar = styled(Navbar)`
  background-color: ${color.cadet} !important;
  min-height: 74px;
  margin-bottom: 20px;
  box-sizing: border-box;
  border: 1px solid #14191c;
  padding-top: 10px !important;
  position: fixed !important;
  top: 0px;
  z-index: 1010 !important;
`

const MiskNavbarGroup = styled(NavbarGroup)`
  font-size: 13px !important;
  font-weight: 600 !important;
  position: relative;
  padding-top: 25px;
  padding-bottom: 27px;
  color: ${color.gray};
  &:hover {
    color: ${color.white};
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

const MiskNavbarHeading = styled.span`
  font-size: 24px !important;
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 0px;
  margin-right: 30px;
  color: ${color.platinum};
  min-width: fit-content;

  a {
    color: ${color.platinum} !important;
    letter-spacing: 1px;
    text-decoration: none;
    &:hover {
      color: ${color.white} !important;
      text-decoration: none;
    }
  }
`

const MiskNavbarHeadingEnvironment = styled(MiskNavbarHeading)`
  color: ${props => props.color} !important;
  min-width: 0;
`

const MiskLink = styled(Link)`
  color: ${color.gray};
  text-decoration: none;
  &:hover {
    color: ${color.white};
    text-decoration: none;
  }
`

const MiskLinkHome = styled(MiskLink)`
  min-width: fit-content;
`

const MiskNavbarButton = styled(Button)`
  background-color: ${color.cadet} !important;
  box-shadow: none !important;
  background-image: none !important;
  top: 15px;
  left: 15px;
  position: absolute;
  z-index: 1020;
`

const MiskNavbarIcon = styled(Icon)`
  color: ${color.gray} !important;
  &:hover {
    color: ${color.white};
  }
`

const MiskCollapse = styled(Collapse)`
  color: ${color.white};
  background-color: ${color.cadet};
  display: block;
  margin: 60px -20px 0 -20px;
`

const MiskMenu = styled.div`
  min-height: 250px;
  padding: 50px 0px;
  @media (max-width: 768px) {
    padding: 50px 20px;
  }
  overflow-y: scroll;
  max-height: 100vh;
`

const MiskMenuNavbarItems = styled.div`
  display: inline-block;
`

const MiskMenuLinks = styled(FlexContainer)`
  padding-bottom: 35px;
`

const MiskMenuLink = styled(MiskLink)`
  font-size: 16px;
  flex-basis: 300px;
  padding: 5px 0;
  color: ${color.platinum};
`

const MiskMenuCategory = styled.span`
  font-size: 24px;
  color: ${color.gray};
  letter-spacing: 0px;
  display: block;
`

const MiskMenuDivider = styled.hr`
  border-color: ${color.gray};
  margin: 5px 0 10px 0;
`

const MiskBanner = styled.span`
  background-color: ${props => props.color} !important;
  color: ${color.accent} !important;
  text-align: center;
  font-weight: 600;
  padding: 5px 10px;
  position: fixed !important;
  width: 100%;
  top: 70px;
  left: 0px;
  z-index: 1010 !important;

  a {
    font-weight: 300;
    color: ${color.accent};
    text-decoration: underline;
    letter-spacing: 1px;
    &:hover {
      color: ${color.white};
      text-decoration: underline;
    }
  }
`

const environmentToColor = (environment: Environment) => {
  switch (environment) {
    case Environment.DEVELOPMENT:
      return color.blue
    case Environment.TESTING:
      return color.indigo
    case Environment.STAGING:
      return color.green
    case Environment.PRODUCTION:
      return color.red
  }
}

export class TopbarComponentDimensionAware extends React.Component<
  IDimensionAwareProps & ITopbarProps,
  {}
> {
  public state = {
    isOpen: false
  }

  public render() {
    const { isOpen } = this.state
    const {
      environment,
      environmentBannerVisible,
      error,
      homeName,
      homeUrl,
      links,
      width,
      status
    } = this.props
    return (
      <MiskNavbar>
        <MiskNavbarButton onClick={this.handleClick}>
          <MiskNavbarIcon
            iconSize={32}
            icon={isOpen ? IconNames.CROSS : IconNames.MENU}
          />
        </MiskNavbarButton>
        <ResponsiveContainer>
          <MiskNavbarGroup align={Alignment.LEFT} className="bp3-dark">
            {this.renderNavbarHome(homeName, homeUrl)}
            {this.NavbarItems(environment)
              .slice(0, Math.floor(Math.min(width - 300, 1800) / 400))
              .map(item => item)}
          </MiskNavbarGroup>
        </ResponsiveContainer>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <MiskMenu>
            <ResponsiveContainer>
              <MiskMenuNavbarItems>
                <FlexContainer>
                  {this.NavbarItems(environment).map(item => (
                    <span key={item.key} onClick={this.handleClick}>
                      {item}
                    </span>
                  ))}
                </FlexContainer>
              </MiskMenuNavbarItems>
              {links ? (
                this.renderMenuCategories(links)
              ) : (
                <ErrorCalloutComponent error={error} />
              )}
            </ResponsiveContainer>
          </MiskMenu>
        </MiskCollapse>
        {this.renderBanner(status, environment, environmentBannerVisible)}
      </MiskNavbar>
    )
  }

  private NavbarItems(environment?: Environment) {
    console.log(this.props.navbarItems)
    return [this.renderNavbarEnvironment(environment)].concat(
      this.props.navbarItems
        ? this.props.navbarItems.map(item => (
            <MiskNavbarHeading>
              <TextHTMLOrElementComponent content={item} />
            </MiskNavbarHeading>
          ))
        : []
    )
  }

  private renderBanner(
    status?: string | Element | JSX.Element,
    environment?: Environment,
    environmentBannerVisible: Environment[] = [
      Environment.DEVELOPMENT,
      Environment.STAGING,
      Environment.TESTING
    ]
  ) {
    if (environmentBannerVisible.includes(environment)) {
      if (typeof status === "string" && !status.startsWith("<")) {
        console.log(`[STATUS] ${status}`)
      }
      return (
        <MiskBanner color={environmentToColor(environment)}>
          <ResponsiveContainer>
            <FlexContainer>
              <TextHTMLOrElementComponent content={status} />
            </FlexContainer>
          </ResponsiveContainer>
        </MiskBanner>
      )
    } else {
      return <div />
    }
  }

  private renderNavbarHome(homeName: string, homeUrl: string) {
    if (homeName && homeUrl) {
      return (
        <MiskLinkHome to={homeUrl}>
          <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
        </MiskLinkHome>
      )
    } else if (homeName) {
      return <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
    } else {
      return <MiskNavbarHeading>Misk</MiskNavbarHeading>
    }
  }

  private renderNavbarEnvironment(
    environment?: Environment,
    environmentBannerVisible: Environment[] = [
      Environment.DEVELOPMENT,
      Environment.STAGING,
      Environment.TESTING
    ]
  ) {
    if (environmentBannerVisible.includes(environment)) {
      return (
        <MiskNavbarHeadingEnvironment color={environmentToColor(environment)}>
          {environment}
        </MiskNavbarHeadingEnvironment>
      )
    } else {
      return <div />
    }
  }

  private renderMenuCategories(links: IDashboardTab[]) {
    const categories: Array<[string, IDashboardTab[]]> = Object.entries(
      groupBy(links, "category")
    )
    return categories.map(([categoryName, categoryLinks]) =>
      this.renderMenuCategory(categoryName, categoryLinks)
    )
  }

  private renderMenuCategory(
    categoryName: string,
    categoryLinks: IDashboardTab[]
  ) {
    const sortedCategoryLinks = sortBy(categoryLinks, "name").filter(
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
}
