/** @jsx jsx */
import { Drawer, Icon, Position } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { css, jsx } from "@emotion/core"
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
    const data = response.results.map(row => {
      const keys = Object.keys(row)
      const sanitized: { [key: string]: string } = {}
      keys.forEach(key => {
        sanitized[key] = JSON.stringify(row[key], null, 2)
      })
      return sanitized
    })
    return (
      <div>
        <Metadata
          content={"View Query Results in Table"}
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
