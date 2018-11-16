import { IResizeEntry, ResizeSensor } from "@blueprintjs/core"
import { reduce } from "lodash"
import * as React from "react"
import {
  defaultEnvironment,
  defaultEnvironmentIndicatorsVisible
} from "../../utilities"
import { DimensionAwareNavbar, INavbarProps } from "../Navbar"

/**
 * <Component
 *    environment={environment}
 *    environmentBannerVisible={[Environment.DEVELOPMENT]}
 *    environmentNavbarVisible={[Environment.DEVELOPMENT]}
 *    error={error}
 *    homeName={"Misk Home"}
 *    homeUrl={"/"}
 *    links={links}
 *    navbarItems={[ "Test1", '<a href="#">Test2</>', <span key={2}>Test3</span> ]}
 *    status={"News Item"}
 *  />
 */

export interface IDimensionAwareProps {
  height: number
  width: number
}

export class Navbar extends React.Component<INavbarProps, {}> {
  public state = {
    height: 0,
    width: 0
  }

  handleResize = (entries: IResizeEntry[]) => {
    this.setState(
      reduce(
        entries,
        (dimension, e) => ({
          height: Math.max(dimension.height, e.contentRect.height),
          width: Math.max(dimension.width, e.contentRect.width)
        }),
        { height: 0, width: 0 }
      )
    )
  }

  public render() {
    const {
      environment = defaultEnvironment,
      environmentBannerVisible = defaultEnvironmentIndicatorsVisible,
      environmentNavbarVisible = defaultEnvironmentIndicatorsVisible,
      error,
      homeName,
      homeUrl,
      links,
      navbar_items,
      status
    } = this.props
    const { height, width } = this.state
    return (
      <ResizeSensor onResize={this.handleResize}>
        <DimensionAwareNavbar
          height={height}
          width={width}
          environment={environment}
          environmentBannerVisible={environmentBannerVisible}
          environmentNavbarVisible={environmentNavbarVisible}
          error={error}
          homeName={homeName}
          homeUrl={homeUrl}
          links={links}
          navbar_items={navbar_items}
          status={status}
        />
      </ResizeSensor>
    )
  }
}
