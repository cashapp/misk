import { Alignment, Navbar, NavbarGroup } from "@blueprintjs/core"
import { color, Environment, IDashboardTab } from "@misk/common"
import * as React from "react"
import styled from "styled-components"
import { ResponsiveContainer } from "../../containers"
import {
  IDimensionAwareProps,
  processNavbarItems,
  TopbarBanner,
  TopbarHomeLink,
  TopbarMenu,
  TopbarNavItems
} from "../Topbar"

/**
 * <TopbarDimensionAware
 *    height={this.state.height}
 *    width={this.state.width}
 *    environment={this.props.environment}
 *    environmentBannerVisible={this.props.environmentBannerVisible}
 *    environmentTopbarVisible={this.props.environmentBannerVisible}
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
  environmentTopbarVisible?: Environment[]
  error?: any
  homeName?: string
  homeUrl?: string
  navbarItems?: Array<string | Element | JSX.Element>
  links?: IDashboardTab[]
  status?: string | Element | JSX.Element
}

const MiskNavbar = styled(Navbar)`
  background-color: ${color.cadet} !important;
  padding-top: 10px !important;
  padding-bottom: 60px !important;
  position: fixed !important;
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

export class TopbarDimensionAware extends React.Component<
  IDimensionAwareProps & ITopbarProps,
  {}
> {
  public state = {
    isOpen: false
  }

  public render() {
    const {
      environment,
      environmentBannerVisible,
      environmentTopbarVisible,
      error,
      homeName,
      homeUrl,
      links,
      height,
      navbarItems,
      width,
      status
    } = this.props
    const processedNavbarItems = processNavbarItems(
      environment,
      environmentTopbarVisible,
      navbarItems
    )
    return (
      <MiskNavbar>
        <ResponsiveContainer>
          <MiskNavbarGroup align={Alignment.LEFT} className="bp3-dark">
            <TopbarHomeLink homeName={homeName} homeUrl={homeUrl} />
            <TopbarNavItems
              processedNavbarItems={processedNavbarItems}
              height={height}
              width={width}
            />
          </MiskNavbarGroup>
        </ResponsiveContainer>
        <TopbarMenu
          processedNavbarItems={processedNavbarItems}
          error={error}
          links={links}
        />
        <TopbarBanner
          environment={environment}
          environmentBannerVisible={environmentBannerVisible}
          status={status}
        />
      </MiskNavbar>
    )
  }
}
