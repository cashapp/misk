import {
  MiskNavbarContainer,
  //  TOOD use this after Misk-Web fix
  // miskAdminDashboardTabsUrl,
  miskServiceMetadataUrl
} from "@misk/core"
import * as React from "react"

export const AdminDashboardContainer = () => (
  <MiskNavbarContainer
    dashboardMetadataUrl={`/api/dashboard/metadata/AdminDashboard`}
    serviceMetadataUrl={miskServiceMetadataUrl}
  />
)
