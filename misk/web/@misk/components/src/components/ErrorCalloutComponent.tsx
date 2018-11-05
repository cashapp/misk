import { Callout } from "@blueprintjs/core"
import * as React from "react"
import styled from "styled-components"

/**
 * <ErrorCalloutComponent error={props.error}/>
 */

export interface IError {
  [key: string]: any
}

export interface IErrorCalloutProps {
  error: IError
}

const ErrorCallout = styled(Callout)`
  margin: 20px 0;
`

const RawError = styled.pre`
  text-align: left;
`

const generateStatus = (props: IErrorCalloutProps) => {
  return `[${props.error.response.status || "Error"}]`
}

const generateDescription = (props: IErrorCalloutProps) => {
  const statusText = props.error.response.statusText
    ? `${props.error.response.statusText}. `
    : ""
  const data = props.error.response.data ? `${props.error.response.data}. ` : ""
  return statusText + data
}

const generateUrl = (props: IErrorCalloutProps) => {
  return `[URL] ${props.error.config.url || ""}`
}

export const ErrorCalloutComponent = (props: IErrorCalloutProps) => (
  <ErrorCallout
    title={`${generateStatus(props)} ${generateDescription(props)}`}
    intent="danger"
  >
    {generateUrl(props)}
    <RawError>{JSON.stringify(props.error, null, 2)}</RawError>
  </ErrorCallout>
)
