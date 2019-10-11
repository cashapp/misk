import { MiskNavbarContainer, miskServiceMetadataUrl } from "@misk/core"
import * as React from "react"

export const AdminDashboardContainer = () => (
  <MiskNavbarContainer
    adminDashboardTabsUrl={"/api/dashboard/metadata/AdminDashboard"}
    serviceMetadataUrl={miskServiceMetadataUrl}
  />
)
