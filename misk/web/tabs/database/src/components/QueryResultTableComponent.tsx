/** @jsx jsx */
import { Drawer, Icon, Position } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { css, jsx } from "@emotion/react"
import { Table } from "@misk/core"
import { useState } from "react"
import { Metadata } from "../components"
import { IRunQueryAPIResponse } from "../containers"

export const cssTableScroll = css`
  height: 100%;
  width: 100%;
  overflow-x: auto;
  overflow-y: auto;
  white-space: nowrap;
`

export const QueryResultTableComponent = (props: {
  response: IRunQueryAPIResponse
}) => {
  const [isOpenTableDrawer, setIsOpenTableDrawer] = useState(false)

  const { response } = props
  if (response && "results" in response) {
    const columnLengthMap: { [key: string]: number } = {}
    const data = response.results
      .map(row => {
        const keys = Object.keys(row)
        const sanitized: { [key: string]: string } = {}
        keys.forEach(key => {
          sanitized[key] = JSON.stringify(row[key], null, 2)
          const cellLength = JSON.stringify(row[key], null, 2).length
          if (!(key in columnLengthMap) || columnLengthMap[key] < cellLength) {
            columnLengthMap[key] = cellLength
          }
        })
        return sanitized
      })
      .map(row => {
        // Reorder row keys so that the shorter cell-length columns appear first in the table
        const sortedRow: { [key: string]: string } = {}
        const sortedColumns = Object.entries(columnLengthMap).sort(
          (entry1, entry2) => entry1[1] - entry2[1]
        )
        sortedColumns.forEach(column => (sortedRow[column[0]] = row[column[0]]))
        return sortedRow
      })

    return (
      <div>
        <Metadata
          content={`View Query Results in Table (${data.length} Rows)`}
          labelElement={<Icon icon={IconNames.PANEL_TABLE} />}
          onClick={() => setIsOpenTableDrawer(!isOpenTableDrawer)}
        />
        <Drawer
          isOpen={isOpenTableDrawer}
          onClose={() => setIsOpenTableDrawer(false)}
          position={Position.BOTTOM}
          size={Drawer.SIZE_LARGE}
          title={"Query Result"}
        >
          <div css={cssTableScroll}>
            <Table data={data} />
          </div>
        </Drawer>
      </div>
    )
  } else {
    return <span />
  }
}
