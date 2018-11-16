import * as React from "react"
import { IDimensionAwareProps } from "../Topbar"

/**
 * <TopbarNavItems
 *    height={this.state.height}
 *    width={this.state.width}
 *    processedNavbarItems={this.props.processedNavbarItems}
 *  />
 */

export interface ITopbarNavItemsProps {
  processedNavbarItems?: JSX.Element[]
}

export class TopbarNavItems extends React.Component<
  IDimensionAwareProps & ITopbarNavItemsProps,
  {}
> {
  public render() {
    const { processedNavbarItems, width } = this.props
    return processedNavbarItems
      .slice(0, Math.floor(Math.min(width - 300, 1800) / 400))
      .map(item => item)
  }
}
