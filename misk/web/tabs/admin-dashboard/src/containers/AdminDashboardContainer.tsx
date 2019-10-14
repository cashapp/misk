import {
  MiskNavbarContainer,
  miskAdminDashboardTabsUrl,
  miskServiceMetadataUrl
} from "@misk/core"
import * as React from "react"

export const AdminDashboardContainer = () => (
  <MiskNavbarContainer
    adminDashboardTabsUrl={`${miskAdminDashboardTabsUrl}/AdminDashboard`}
    serviceMetadataUrl={miskServiceMetadataUrl}
  />
)
