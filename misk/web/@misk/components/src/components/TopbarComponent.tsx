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
import { FlexContainer, ResponsiveContainer } from "../containers"
import { ErrorCalloutComponent } from "./ErrorCalloutComponent"

/**
 * <TopbarComponent homeName={"Service Name"} homeUrl={"/_admin/"} links={props.tabs}/>
 */

export interface ITopbarProps {
  status?: string | Element
  environment?: Environment
  environmentBannerVisible?: Environment[]
  error?: any
  homeName?: string
  homeUrl?: string
  links?: IDashboardTab[]
}

const MiskNavbar = styled(Navbar)`
  background-color: ${color.text} !important;
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
  padding-right: 15px;
  color: ${color.accent};
`

const MiskNavbarHeadingEnvironment = styled(MiskNavbarHeading).attrs({
  environment: (props: any) => props.environment
})`
  color: ${props =>
    (props.environment && environmentToColor(props.environment)) ||
    color.accent} !important;
`

const MiskLink = styled(Link)`
  font-size: 16px;
  font-weight: 500;
  color: ${color.gray};
  text-decoration: none;
  &:hover {
    color: ${color.white};
    text-decoration: none;
  }
`

const MiskNavbarButton = styled(Button)`
  background-color: ${color.text} !important;
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
  background-color: ${color.text};
  display: block;
  margin: 60px -20px 0 -20px;
`

const MiskMenu = styled.div`
  min-height: 250px;
  padding-top: 20px;
  @media (max-width: 768px) {
    padding: 20px;
  }
`

const MiskMenuNavbarItems = styled.div`
  display: inline-block;
`

const MiskMenuLinks = styled(FlexContainer)`
  padding-bottom: 35px;
`

const MiskMenuLink = styled(MiskLink)`
  flex-basis: 300px;
  padding: 5px 0;
  color: ${color.accent};
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

const MiskBanner = styled.span.attrs({
  environment: (props: any) => props.environment
})`
  background-color: ${props =>
    (props.environment && environmentToColor(props.environment)) ||
    color.text} !important;
  color: ${color.accent} !important;
  text-align: center;
  font-weight: 600;
  padding: 5px 0px;
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

export class TopbarComponent extends React.Component<ITopbarProps, {}> {
  public state = {
    isOpen: false,
    width: 0
  }

  updateDimensions = () => {
    this.setState({ ...this.state, width: window.innerWidth })
  }

  componentDidMount = () => {
    window.addEventListener("resize", this.updateDimensions)
    this.setState({ ...this.state, width: window.innerWidth })
  }

  componentWillUnmount = () => {
    window.removeEventListener("resize", this.updateDimensions)
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
              .slice(
                0,
                Math.floor(Math.min(this.state.width - 300, 1800) / 400)
              )
              .map(item => item)}
          </MiskNavbarGroup>
        </ResponsiveContainer>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <MiskMenu>
            <ResponsiveContainer>
              <MiskMenuNavbarItems>
                <FlexContainer>
                  {this.NavbarItems(environment).map(item => item)}
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
    return [this.renderNavbarEnvironment(environment)]
  }

  private renderBanner(
    status?: string | Element,
    environment?: Environment,
    environmentBannerVisible: Environment[] = [
      Environment.DEVELOPMENT,
      Environment.STAGING,
      Environment.TESTING
    ]
  ) {
    if (environmentBannerVisible.includes(environment)) {
      let formattedStatus: any = status
      if (typeof status === "string") {
        if (status.startsWith("<")) {
          formattedStatus = (
            <span dangerouslySetInnerHTML={{ __html: status }} />
          )
        } else if (status.length > 35) {
          formattedStatus = `${status.substring(0, 35)}...`
          console.log(`[ALERT] ${status}`)
        }
      }
      return (
        <MiskBanner environment={environment}>
          <ResponsiveContainer>{formattedStatus}</ResponsiveContainer>
        </MiskBanner>
      )
    } else {
      return <div />
    }
  }

  private renderNavbarHome(homeName: string, homeUrl: string) {
    if (homeName && homeUrl) {
      return (
        <MiskLink to={homeUrl}>
          <MiskNavbarHeading>{homeName}</MiskNavbarHeading>
        </MiskLink>
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
        <MiskNavbarHeadingEnvironment environment={environment}>
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
