import { Environment } from "@misk/common"
import * as React from "react"
import { TextHTMLOrElementComponent } from "../../components"
import { environmentToColor } from "../../utilities"
import { MiskNavbarHeading, MiskNavbarHeadingEnvironment } from "../Topbar"

/**
 * processNavbarItems(environment, environmentTopbarVisible, navbarItems)
 */

const renderEnvironmentLink = (
  environment?: Environment,
  environmentTopbarVisible?: Environment[]
) => {
  if (
    environmentTopbarVisible &&
    environmentTopbarVisible.includes(environment)
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
      <TextHTMLOrElementComponent content={item} />
    </MiskNavbarHeading>
  ))
}

export const processNavbarItems = (
  environment?: Environment,
  environmentTopbarVisible?: Environment[],
  navbarItems?: Array<string | Element | JSX.Element>
) =>
  renderEnvironmentLink(environment, environmentTopbarVisible).concat(
    renderNavbarItems(navbarItems)
  )
