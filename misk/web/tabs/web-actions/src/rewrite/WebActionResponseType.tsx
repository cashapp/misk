import React, {useContext} from "react"
import { HTMLTable, UL } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import WebActionCollapse from "./WebActionCollapse"
import WebActionProtoField from "./WebActionProtoField"
import {ProtobufDocUrlPrefix} from "./TabContainer"

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

  const protobufDocUrlPrefix = useContext(ProtobufDocUrlPrefix)

  return (
    <>
      {responseType ? (
        <WebActionCollapse
          title={"Response Type"}
          subtitle={webActionMetadata.responseType}
          doubleWidth={true}
        >
          <UL style={{ listStyle: "none" }}>
            <a href={protobufDocUrlPrefix + webActionMetadata.responseType}>{webActionMetadata.requestType}</a>
            <li>
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
