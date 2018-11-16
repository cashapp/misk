import { Button, Collapse, Icon } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { color, IDashboardTab } from "@misk/common"
import { groupBy, sortBy } from "lodash"
import * as React from "react"
import styled from "styled-components"
import { ErrorCalloutComponent } from "../../components"
import { FlexContainer, ResponsiveContainer } from "../../containers"
import { MiskLink } from "../Navbar"

/**
 * <Menu
 *    error={this.props.error}
 *    links={this.props.links}
 *    processedNavbarItems={this.props.processedNavbarItems}
 *  />
 */

export interface IMenuProps {
  error?: any
  links?: IDashboardTab[]
  processedNavbarItems?: JSX.Element[]
}

const MiskNavbarButton = styled(Button)`
  background-color: ${color.cadet} !important;
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
  background-color: ${color.cadet};
  display: block;
  margin: 60px -20px 0 -20px;
`

const MiskMenu = styled.div`
  min-height: 250px;
  padding: 50px 0px;
  @media (max-width: 768px) {
    padding: 50px 20px;
  }
  overflow-y: scroll;
  max-height: 100vh;
`

const MiskMenuNavbarItems = styled.div`
  display: inline-block;
`

const MiskMenuLinks = styled(FlexContainer)`
  padding-bottom: 35px;
`

const MiskMenuLink = styled(MiskLink)`
  font-size: 16px;
  flex-basis: 300px;
  padding: 5px 0;
  color: ${color.platinum};
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

export class Menu extends React.Component<IMenuProps, {}> {
  public state = {
    isOpen: false
  }

  public render() {
    const { isOpen } = this.state
    const { error, links, processedNavbarItems } = this.props
    return (
      <div>
        <MiskNavbarButton onClick={this.handleClick}>
          <MiskNavbarIcon
            iconSize={32}
            icon={isOpen ? IconNames.CROSS : IconNames.MENU}
          />
        </MiskNavbarButton>
        <MiskCollapse isOpen={isOpen} keepChildrenMounted={true}>
          <MiskMenu>
            <ResponsiveContainer>
              <MiskMenuNavbarItems>
                <FlexContainer>
                  {processedNavbarItems.map(item => (
                    <span key={item.key} onClick={this.handleClick}>
                      {item}
                    </span>
                  ))}
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
      </div>
    )
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
