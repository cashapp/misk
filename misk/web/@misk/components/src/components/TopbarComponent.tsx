import { IResizeEntry, ResizeSensor } from "@blueprintjs/core"
import { reduce } from "lodash"
import * as React from "react"
import { ITopbarProps, TopbarComponentDimensionAware } from "../components"

/**
 * <TopbarComponent
 *    environment={environment}
 *    environmentBannerVisible={[Environment.DEVELOPMENT]}
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
    return (
      <ResizeSensor onResize={this.handleResize}>
        <TopbarComponentDimensionAware
          height={this.state.height}
          width={this.state.width}
          environment={this.props.environment}
          environmentBannerVisible={this.props.environmentBannerVisible}
          error={this.props.error}
          homeName={this.props.homeName}
          homeUrl={this.props.homeUrl}
          links={this.props.links}
          navbarItems={this.props.navbarItems}
          status={this.props.status}
        />
      </ResizeSensor>
    )
  }
}
