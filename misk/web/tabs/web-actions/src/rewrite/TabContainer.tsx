import React, { useEffect, useState } from "react"
import { H1 } from "@blueprintjs/core"
import axios from "axios"
import {
  DashboardMetadataResponse,
  ServiceMetadataResponse,
  WebActionMetadataResponse
} from "./types"
import WebActionCard from "./WebActionCard"

export default function TabContainer() {
  const [dashboardMetadata, setDashboardMetadata] = useState<
    DashboardMetadataResponse | undefined
  >(undefined)
  const [serviceMetadata, setServiceMetadata] = useState<
    ServiceMetadataResponse | undefined
  >(undefined)
  const [webactionMetadata, setWebactionMetadata] = useState<
    WebActionMetadataResponse | undefined
  >(undefined)

  // TODO: Convert to the real URLs, but using these to test easily (copies of a services's API responses).
  useEffect(() => {
    axios
      .get("http://localhost:8080/dashboard-metadata.json")
      .then(response => {
        setDashboardMetadata(response.data)
      })
  }, [setDashboardMetadata])

  useEffect(() => {
    axios.get("http://localhost:8080/service-metadata.json").then(response => {
      setServiceMetadata(response.data)
    })
  }, [setServiceMetadata])

  useEffect(() => {
    axios
      .get("http://localhost:8080/webaction-metadata.json")
      .then(response => {
        setWebactionMetadata(response.data)
      })
  }, [setWebactionMetadata])

  if (
    dashboardMetadata === undefined ||
    serviceMetadata === undefined ||
    webactionMetadata === undefined
  ) {
    return <p>Loading</p>
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
