/** @jsx jsx */
import { Collapse } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
import React, { useState } from "react"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"
import { SendQueryContainer } from "../containers"
import { QueryFormContainer } from "./QueryFormContainer"

export const SetFormData = React.createContext(null)

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const RunQueryCollapseContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
  isOpen: boolean
  tag: string
}) => {
  const { databaseQuery, tag } = props

  // Determine if Send Request form for the Web Action should be open
  const isOpen = props.isOpen

  const [] = useState(false)
  const [] = useState(false)
  const [] = useState("{}")
  const [formData, setFormData] = useState({} as any)
  const [isOpenRequestBodyPreview, setIsOpenRequestBodyPreview] = useState(
    false
  )

  console.log("the big one", formData)

  return (
    <Collapse isOpen={isOpen}>
      <FlexContainer>
        <SetFormData.Provider value={setFormData}>
          <QueryFormContainer
            databaseQuery={databaseQuery}
            isOpenRequestBodyPreview={isOpenRequestBodyPreview}
            setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
          />
        </SetFormData.Provider>
        <SendQueryContainer
          databaseQuery={databaseQuery}
          formData={formData}
          isOpenRequestBodyPreview={isOpenRequestBodyPreview}
          setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
          tag={tag}
        />
      </FlexContainer>
    </Collapse>
  )
}
