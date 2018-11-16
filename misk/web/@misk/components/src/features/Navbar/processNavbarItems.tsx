import { Environment } from "@misk/common"
import * as React from "react"
import { TextHTMLOrElementComponent } from "../../components"
import { environmentToColor } from "../../utilities"
import { MiskNavbarHeading, MiskNavbarHeadingEnvironment } from "../Navbar"

/**
 * processNavbarItems(environment, environmentNavbarVisible, navbarItems)
 */

const renderEnvironmentLink = (
  environment?: Environment,
  environmentNavbarVisible?: Environment[]
) => {
  if (
    environmentNavbarVisible &&
    environmentNavbarVisible.includes(environment)
  ) {
    return [environment].map(env => (
      <MiskNavbarHeadingEnvironment color={environmentToColor(env)}>
        {env}
      </MiskNavbarHeadingEnvironment>
    ))
  } else {
    return []
  }
}

const renderNavbarItems = (
  navbarItems?: Array<string | Element | JSX.Element>
) => {
  return navbarItems.map(item => (
    <MiskNavbarHeading>
      <TextHTMLOrElementComponent>{item}</TextHTMLOrElementComponent>
    </MiskNavbarHeading>
  ))
}

export const processNavbarItems = (
  environment?: Environment,
  environmentNavbarVisible?: Environment[],
  navbar_items?: Array<string | Element | JSX.Element>
) =>
  renderEnvironmentLink(environment, environmentNavbarVisible).concat(
    renderNavbarItems(navbar_items)
  )
