import * as React from "react"
import styled from "styled-components"
import { MiskLink, MiskNavbarHeading } from "../Topbar"

/**
 * <TopbarHomeLink
 *    homeName={this.props.homeName}
 *    homeUrl={this.props.homeUrl}
 *  />
 */

export interface ITopbarHomeLinkProps {
  homeName?: string
  homeUrl?: string
}

const MiskLinkHome = styled(MiskLink)`
  min-width: fit-content;
`

export const TopbarHomeLink = (props: ITopbarHomeLinkProps) => {
  const { homeName, homeUrl } = props
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
