import { color, Environment } from "@misk/common"
import * as React from "react"
import styled from "styled-components"
import { TextHTMLOrElementComponent } from "../../components"
import { FlexContainer, ResponsiveContainer } from "../../containers"
import { environmentToColor } from "../../utilities"

/**
 * <Banner
 *    environment={this.props.environment}
 *    environmentBannerVisible={this.props.environmentBannerVisible}
 *    status={this.props.status}
 *  />
 */

export interface IBannerProps {
  environment?: Environment
  environmentBannerVisible?: Environment[]
  status?: string | Element | JSX.Element
}

const MiskNavbarBanner = styled.span`
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

export class Banner extends React.Component<IBannerProps, {}> {
  public render() {
    const { environment, environmentBannerVisible, status } = this.props
    if (
      environmentBannerVisible &&
      environmentBannerVisible.includes(environment)
    ) {
      return (
        <MiskNavbarBanner color={environmentToColor(environment)}>
          <ResponsiveContainer>
            <FlexContainer>
              <TextHTMLOrElementComponent>{status}</TextHTMLOrElementComponent>
            </FlexContainer>
          </ResponsiveContainer>
        </MiskNavbarBanner>
      )
    } else {
      return <div />
    }
  }
}
