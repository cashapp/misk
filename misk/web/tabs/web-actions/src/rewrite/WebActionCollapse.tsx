import React, { useState } from "react"
import { Collapse, MenuItem } from "@blueprintjs/core"

interface Props {
  title: string
  subtitle?: string
  children?: React.ReactNode
}

export default function WebActionCollapse({
  title,
  subtitle,
  children
}: Props) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div style={{ maxWidth: "550px", wordBreak: "break-word" }}>
      <MenuItem
        text={title}
        label={subtitle}
        icon={expanded ? "caret-down" : "caret-right"}
        onClick={() => setExpanded(expanded => !expanded)}
      />
      <Collapse isOpen={expanded} transitionDuration={0}>
        {children}
      </Collapse>
    </div>
  )
}
