/** @jsx jsx */
import { H3, Spinner } from "@blueprintjs/core"
import { css, jsx } from "@emotion/core"
import { CodePreContainer } from "@misk/core"
import * as React from "react"

export interface IConfigResource {
  name: string
  file: string
}

export interface IConfigProps {
  resources?: string
}

const cssH3 = css({ fontFamily: "Fira Code, Menlo" })

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  renderConfig(resource: IConfigResource) {
    return (
      <div>
        <br />
        <H3 css={css({ fontFamily: "Fira Code, Menlo" })}>{resource.name}</H3>
        <CodePreContainer>{resource.file}</CodePreContainer>
      </div>
    )
  }

  render() {
    const { resources } = this.props
    if (resources) {
      return (
        <div>
          {resources &&
            Object.entries(resources).map(([name, file]) =>
              this.renderConfig({ name, file })
            )}
        </div>
      )
    } else {
      return (
        <div>
          <br />
          <H3 css={cssH3}>{"Effective Config"}</H3>
          <CodePreContainer>{<Spinner />}</CodePreContainer>
          <br />
          <H3 css={cssH3}>{"classpath://common.yaml"}</H3>
          <CodePreContainer>{<Spinner />}</CodePreContainer>
          <H3 css={cssH3}>{"classpath://production.yaml"}</H3>
          <CodePreContainer>{<Spinner />}</CodePreContainer>
          <H3 css={cssH3}>{"classpath://staging.yaml"}</H3>
          <CodePreContainer>{<Spinner />}</CodePreContainer>
          <H3 css={cssH3}>{"JVM"}</H3>
          <CodePreContainer>{<Spinner />}</CodePreContainer>
        </div>
      )
    }
  }
}
