import * as React from "react"
import { IDimensionAwareProps } from "../Navbar"

/**
 * <NavItems
 *    height={this.state.height}
 *    width={this.state.width}
 *    processedNavbarItems={this.props.processedNavbarItems}
 *  />
 */

export interface INavItemsProps {
  processedNavbarItems?: JSX.Element[]
}

export class NavItems extends React.Component<
  IDimensionAwareProps & INavItemsProps,
  {}
> {
  public render() {
    const { processedNavbarItems, width } = this.props
    return processedNavbarItems
      .slice(0, Math.floor(Math.min(width - 300, 1800) / 400))
      .map(item => item)
  }
}
