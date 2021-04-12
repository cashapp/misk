import React from "react"
import { Menu, H5, UL } from "@blueprintjs/core"
import { WebActionMetadata } from "./types"
import WebActionCollapse from "./WebActionCollapse"
import WebActionSendRequest from "./WebActionSendRequest"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionCardBody({ webActionMetadata }: Props) {
  const titleJoined = (title: string[]) => title.join(", ")
  const subtitleWithLength = (title: string, contents: any[]) =>
    `${title} (${contents.length})`

  const accessAnnotations = webActionMetadata.functionAnnotations.filter(
    a => a.includes("Access") || a.includes("authz")
  )

  return (
    <Menu
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        columnGap: "16px"
      }}
    >
      <WebActionCollapse
        title={webActionMetadata.responseMediaType}
        subtitle="Content Types"
      >
        <H5>Request Content Types:</H5>
        <UL>
          {webActionMetadata.requestMediaTypes.map(type => (
            <li key={type}>{type}</li>
          ))}
        </UL>

        <H5>Response Content Types:</H5>
        <UL>
          <li>{webActionMetadata.responseMediaType}</li>
        </UL>
      </WebActionCollapse>

      <WebActionCollapse
        title={titleJoined(webActionMetadata.allowedServices)}
        subtitle={subtitleWithLength(
          "Services",
          webActionMetadata.allowedServices
        )}
      >
        <UL>
          {webActionMetadata.allowedServices.map(service => (
            <li key={service}>{service}</li>
          ))}
        </UL>
      </WebActionCollapse>

      <WebActionCollapse
        title={titleJoined(webActionMetadata.applicationInterceptors)}
        subtitle={subtitleWithLength(
          "Application Interceptors",
          webActionMetadata.applicationInterceptors
        )}
      >
        <UL>
          {webActionMetadata.applicationInterceptors.map(interceptor => (
            <li key={interceptor}>{interceptor}</li>
          ))}
        </UL>
      </WebActionCollapse>

      <WebActionCollapse
        title={titleJoined(webActionMetadata.allowedCapabilities)}
        subtitle={subtitleWithLength(
          "Roles",
          webActionMetadata.allowedCapabilities
        )}
      >
        <UL>
          {webActionMetadata.allowedCapabilities.map(capability => (
            <li key={capability}>{capability}</li>
          ))}
        </UL>
      </WebActionCollapse>

      <WebActionCollapse
        title={titleJoined(webActionMetadata.networkInterceptors)}
        subtitle={subtitleWithLength(
          "Network Interceptors",
          webActionMetadata.networkInterceptors
        )}
      >
        <UL>
          {webActionMetadata.networkInterceptors.map(interceptor => (
            <li key={interceptor}>{interceptor}</li>
          ))}
        </UL>
      </WebActionCollapse>

      <WebActionCollapse
        title={titleJoined(accessAnnotations)}
        subtitle="Access"
      >
        <UL>
          {accessAnnotations.map(annotation => (
            <li key={annotation}>{annotation}</li>
          ))}
        </UL>
      </WebActionCollapse>

      <div style={{ gridColumn: "auto / span 2" }}>
        <WebActionCollapse title="Send a Request" doubleWidth={true}>
          <WebActionSendRequest webActionMetadata={webActionMetadata} />
        </WebActionCollapse>
      </div>
    </Menu>
  )
}
