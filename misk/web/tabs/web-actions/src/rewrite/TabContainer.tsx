import React, { createContext, useEffect, useState } from "react"
import { H1, H2 } from "@blueprintjs/core"
import axios, { AxiosResponse } from "axios"
import { WebActionMetadataResponse, WebActionsByPackage } from "./types"
import WebActionCards from "./WebActionCards"
import LoadingState from "./LoadingState"
import Spacer from "./Spacer"

export const ProtobufDocUrlPrefix = createContext(null)

export default function TabContainer() {
  const [webActionsByPackage, setWebActionsByPacakge] = useState<
    WebActionsByPackage | undefined
  >()

  const [protobufDocUrlPrefix, setProtobufDocUrlPrefix] = useState<
    String | undefined
  >()

  useEffect(() => {
    axios
      .get("/api/webaction/metadata")
      .then((response: AxiosResponse<WebActionMetadataResponse>) => {
        response.data.webActionMetadata.sort((a, b) =>
          a.name.localeCompare(b.name)
        )

        setProtobufDocUrlPrefix(response.data.protobufDocUrlPrefix)

        const webActionsByPackage = response.data.webActionMetadata.reduce(
          (packages, action) => {
            if (packages[action.packageName] === undefined) {
              packages[action.packageName] = [action]
            } else {
              packages[action.packageName].push(action)
            }
            return packages
          },
          {} as WebActionsByPackage
        )

        setWebActionsByPacakge(webActionsByPackage)
      })
  }, [setWebActionsByPacakge])

  if (webActionsByPackage === undefined) {
    return <LoadingState />
  }

  return (
    <ProtobufDocUrlPrefix.Provider value={protobufDocUrlPrefix}>
      <div
        style={{ display: "flex", alignItems: "center", marginBottom: "8px" }}
      >
        <H1 style={{ margin: 0 }}>Web Actions</H1>
        <Spacer size="small" />
        <p style={{ margin: 0 }}>
          Direct any feedback to #misk-web-discuss. You can access the old
          version <a href="/_admin/web-actions-old/">here.</a>
        </p>
      </div>
      {Object.keys(webActionsByPackage)
        .sort()
        .map(packageName => (
          <>
            <H2>{packageName}</H2>
            <WebActionCards webActions={webActionsByPackage[packageName]} />
          </>
        ))}
    </ProtobufDocUrlPrefix.Provider>
  )
}
