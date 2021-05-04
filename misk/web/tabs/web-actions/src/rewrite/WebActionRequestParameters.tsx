import React, { useContext } from "react"
import { HTMLTable, UL } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import WebActionParameter from "./WebActionParameter"
import WebActionCollapse from "./WebActionCollapse"
import WebActionProtoField from "./WebActionProtoField"
import { ProtobufDocUrlPrefix } from "./TabContainer"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionRequestParameters({
  webActionMetadata
}: Props) {
  const requestType = webActionMetadata.types[webActionMetadata.requestType]
  const parameters = webActionMetadata.parameters

  const protobufDocUrlPrefix = useContext(ProtobufDocUrlPrefix)

  if (parameters.length == 0) {
    return null
  }

  return (
    <>
      {requestType ? (
        <WebActionCollapse
          title={"Request Parameters"}
          subtitle={webActionMetadata.requestType}
          doubleWidth={true}
        >
          <UL style={{ listStyle: "none" }}>
            <li>
              <a href={protobufDocUrlPrefix + webActionMetadata.requestType}>{webActionMetadata.requestType}</a>
              <HTMLTable style={{ marginBottom: "0px" }}>
                {requestType.fields.map(field => (
                  <WebActionProtoField field={field} />
                ))}
              </HTMLTable>
            </li>
          </UL>
        </WebActionCollapse>
      ) : (
        <WebActionCollapse title={"Request Parameters"} doubleWidth={true}>
          <UL style={{ listStyle: "none" }}>
            <li>
              <HTMLTable style={{ marginBottom: "0px" }}>
                {parameters.map(parameter => (
                  <WebActionParameter parameter={parameter} />
                ))}
              </HTMLTable>
            </li>
          </UL>
        </WebActionCollapse>
      )}
    </>
  )
}
