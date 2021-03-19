import React from "react"

const SPACING = {
  XSMALL: "6px",
  SMALL: "12px",
  MEDIUM: "18px",
  LARGE: "24px",
  XLARGE: "30px"
}

type Size = "xsmall" | "small" | "medium" | "large" | "xlarge"

interface Props {
  size: Size
  layout?: "vertical" | "horizontal"
}

const SIZES: { [key in Size]: string | number } = {
  xsmall: SPACING.XSMALL,
  small: SPACING.SMALL,
  medium: SPACING.MEDIUM,
  large: SPACING.LARGE,
  xlarge: SPACING.XLARGE
}

export default function Spacer({ size, layout = "vertical" }: Props) {
  return (
    <div
      style={{
        display: layout === "vertical" ? "block" : "inline-block",
        height: SIZES[size],
        width: SIZES[size]
      }}
    />
  )
}
