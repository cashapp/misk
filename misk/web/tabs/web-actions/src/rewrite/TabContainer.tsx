import React, { useEffect, useState } from "react"
import { H1 } from "@blueprintjs/core"
import axios, { AxiosResponse } from "axios"
import { WebActionMetadataResponse } from "./types"
import WebActionCard from "./WebActionCard"
import LoadingState from "./LoadingState"
import Spacer from "./Spacer"

export default function TabContainer() {
  const [webactionMetadata, setWebactionMetadata] = useState<
    WebActionMetadataResponse | undefined
  >(undefined)

  useEffect(() => {
    axios
      .get("/api/webaction/metadata")
      .then((response: AxiosResponse<WebActionMetadataResponse>) => {
        response.data.webActionMetadata.sort((a, b) =>
          a.name.localeCompare(b.name)
        )
        setWebactionMetadata(response.data)
      })
  }, [setWebactionMetadata])

  if (webactionMetadata === undefined) {
    return <LoadingState />
  }

  return (
    <>
      <div
        style={{ display: "flex", alignItems: "center", marginBottom: "8px" }}
      >
        <H1 style={{ margin: 0 }}>Web Actions Beta</H1>
        <Spacer size="small" />
        <p style={{ margin: 0 }}>Direct any feedback to #misk-web-discuss.</p>
      </div>
      {webactionMetadata.webActionMetadata.map(webactionMetadata => (
        <WebActionCard webActionMetadata={webactionMetadata} />
      ))}
    </>
  )
}
