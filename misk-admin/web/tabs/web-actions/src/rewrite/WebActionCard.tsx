import React from "react"
import { Card, H5 } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import WebActionCardHeader from "./WebActionCardHeader"
import WebActionCardBody from "./WebActionCardBody"
import Spacer from "./Spacer"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionCard({ webActionMetadata }: Props) {
  const annotationsWorthShowing = webActionMetadata.functionAnnotations.filter(
    annotation =>
      !annotation.includes("pathPattern") &&
      !annotation.includes("RequestContentType") &&
      !annotation.includes("ResponseContentType") &&
      !annotation.includes("Access") &&
      !annotation.includes("authz") &&
      !annotation.includes("function") &&
      !annotation.includes("Description")
  )

  return (
    <Card
      interactive={true}
      style={{ marginBottom: "24px", maxWidth: "1150px" }}
    >
      <WebActionCardHeader
        title={webActionMetadata.name}
        httpMethod={webActionMetadata.httpMethod}
        pathPattern={webActionMetadata.pathPattern}
      />
      <Spacer size="small" layout="vertical" />
      {annotationsWorthShowing.map(annotation => (
        <H5 key={annotation}>{annotation}</H5>
      ))}
      <Spacer size="small" layout="vertical" />
      <WebActionCardBody webActionMetadata={webActionMetadata} />
    </Card>
  )
}
