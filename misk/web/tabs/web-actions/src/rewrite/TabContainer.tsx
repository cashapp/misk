import React, { useEffect, useState } from "react"
import { H1 } from "@blueprintjs/core"
import axios, { AxiosResponse } from "axios"
import { WebActionMetadataResponse } from "./types"
import WebActionCard from "./WebActionCard"
import LoadingState from "./LoadingState"

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
      <H1>Web Actions</H1>
      {webactionMetadata.webActionMetadata.map(webactionMetadata => (
        <WebActionCard webActionMetadata={webactionMetadata} />
      ))}
    </>
  )
}
