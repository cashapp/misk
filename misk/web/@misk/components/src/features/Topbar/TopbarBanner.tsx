import { color, Environment } from "@misk/common"
import * as React from "react"
import styled from "styled-components"
import { TextHTMLOrElementComponent } from "../../components"
import { FlexContainer, ResponsiveContainer } from "../../containers"
import { environmentToColor } from "../../utilities"

/**
 * <TopbarBanner
 *    environment={this.props.environment}
 *    environmentBannerVisible={this.props.environmentBannerVisible}
 *    status={this.props.status}
 *  />
 */

export interface ITopbarBannerProps {
  environment?: Environment
  environmentBannerVisible?: Environment[]
  status?: string | Element | JSX.Element
}

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

export class TopbarBanner extends React.Component<ITopbarBannerProps, {}> {
  public render() {
    const { environment, environmentBannerVisible, status } = this.props
    if (
      environmentBannerVisible &&
      environmentBannerVisible.includes(environment)
    ) {
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
}
