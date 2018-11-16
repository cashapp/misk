import { color } from "@misk/common"
import { Link } from "react-router-dom"
import styled from "styled-components"

export const MiskNavbarHeading = styled.span`
  font-size: 24px !important;
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 0px;
  margin-right: 30px;
  color: ${color.platinum};
  min-width: fit-content;

  a {
    color: ${color.platinum} !important;
    letter-spacing: 1px;
    text-decoration: none;
    &:hover {
      color: ${color.white} !important;
      text-decoration: none;
    }
  }
`

export const MiskNavbarHeadingEnvironment = styled(MiskNavbarHeading)`
  color: ${props => props.color} !important;
  min-width: 0;
`

export const MiskLink = styled(Link)`
  color: ${color.gray};
  text-decoration: none;
  &:hover {
    color: ${color.white};
    text-decoration: none;
  }
`
