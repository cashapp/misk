import * as React from "react"

/**
 * <TextHTMLOrElementComponent length={35}>{<div>item</div>}</TextHTMLOrElementComponent>
 * <TextHTMLOrElementComponent length={35}>{"Test"} </TextHTMLOrElementComponent>
 * <TextHTMLOrElementComponent length={35}>{'<a href="http://squareup.com/">Test</a>'} </TextHTMLOrElementComponent>
 *
 * Renders passed in text, HTML-parseable string, or React element with optional length constraints for text.
 */

export interface ITextHTMLOrElementProps {
  children: string | Element | JSX.Element
}

export const TextHTMLOrElementComponent = (
  props: ITextHTMLOrElementProps
): JSX.Element => {
  const { children } = props
  let FormattedContent: any = children

  if (typeof children === "string" && !children.startsWith("<")) {
    FormattedContent = <span>{children}</span>
  } else if (typeof children === "string" && children.startsWith("<")) {
    FormattedContent = <span dangerouslySetInnerHTML={{ __html: children }} />
  }
  if (FormattedContent == null) {
    FormattedContent = <span />
  }
  return FormattedContent
}
