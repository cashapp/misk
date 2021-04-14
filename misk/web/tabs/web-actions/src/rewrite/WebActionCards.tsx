import React from "react"
import {WebActionMetadata} from "./types";
import WebActionCard from "./WebActionCard";

interface Props {
  webActions: WebActionMetadata[]
}

export default function WebActionCards({webActions}: Props) {
  return (
    <>
      {webActions.map((webActionMetadata) =>
        <WebActionCard webActionMetadata={webActionMetadata} />
      )}
    </>
  )
}