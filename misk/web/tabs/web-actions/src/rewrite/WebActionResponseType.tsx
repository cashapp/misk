import React from "react"
import { HTMLTable, UL } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import WebActionCollapse from "./WebActionCollapse"
import WebActionProtoField from "./WebActionProtoField"
import LinkToProtoDoc from "./LinkToProtoDoc"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionResponseType({ webActionMetadata }: Props) {
  const responseType =
    webActionMetadata.responseTypes[webActionMetadata.responseType]
  const parameters = webActionMetadata.parameters

  if (parameters.length == 0) {
    return null
  }

  return (
    <>
      {responseType ? (
        <WebActionCollapse
          title={"Response Type"}
          subtitle={webActionMetadata.responseType}
          doubleWidth={true}
        >
          <UL style={{ listStyle: "none" }}>
            <li>
              <LinkToProtoDoc protoClass={webActionMetadata.requestType} />
              <HTMLTable style={{ marginBottom: "0px" }}>
                {responseType.fields.map(field => (
                  <WebActionProtoField field={field} />
                ))}
              </HTMLTable>
            </li>
          </UL>
        </WebActionCollapse>
      ) : (
        <WebActionCollapse title={"Response Type"} doubleWidth={true}>
          <UL style={{ listStyle: "none" }}>
            <li>{webActionMetadata.returnType}</li>
          </UL>
        </WebActionCollapse>
      )}
    </>
  )
}
