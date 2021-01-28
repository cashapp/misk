import {
  MiskNavbarContainer,
  miskDashboardMetadataUrl,
  miskServiceMetadataUrl,
} from "@misk/core"
import * as React from "react"

export const AdminDashboardContainer = () => (
  <MiskNavbarContainer
    dashboardMetadataUrl={miskDashboardMetadataUrl("admindashboard")}
    serviceMetadataUrl={miskServiceMetadataUrl}
  />
)
