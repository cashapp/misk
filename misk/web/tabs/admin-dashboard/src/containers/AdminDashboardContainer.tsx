import {
  MiskNavbarContainer,
  //  TOOD use this after Misk-Web fix
  // miskAdminDashboardTabsUrl,
  miskServiceMetadataUrl
} from "@misk/core"
import * as React from "react"

export const AdminDashboardContainer = () => (
  <MiskNavbarContainer
    adminDashboardTabsUrl={`/api/dashboard/metadata/AdminDashboardTab`}
    serviceMetadataUrl={miskServiceMetadataUrl}
  />
)
