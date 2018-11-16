import { IResizeEntry, ResizeSensor } from "@blueprintjs/core"
import { reduce } from "lodash"
import * as React from "react"
import {
  defaultEnvironment,
  defaultEnvironmentIndicatorsVisible
} from "../../utilities"
import { ITopbarProps, TopbarDimensionAware } from "../Topbar"

/**
 * <TopbarComponent
 *    environment={environment}
 *    environmentBannerVisible={[Environment.DEVELOPMENT]}
 *    environmentTopbarVisible={[Environment.DEVELOPMENT]}
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

export class TopbarComponent extends React.Component<ITopbarProps, {}> {
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
      environmentTopbarVisible = defaultEnvironmentIndicatorsVisible,
      error,
      homeName,
      homeUrl,
      links,
      navbarItems,
      status
    } = this.props
    const { height, width } = this.state
    return (
      <ResizeSensor onResize={this.handleResize}>
        <TopbarDimensionAware
          height={height}
          width={width}
          environment={environment}
          environmentBannerVisible={environmentBannerVisible}
          environmentTopbarVisible={environmentTopbarVisible}
          error={error}
          homeName={homeName}
          homeUrl={homeUrl}
          links={links}
          navbarItems={navbarItems}
          status={status}
        />
      </ResizeSensor>
    )
  }
}
