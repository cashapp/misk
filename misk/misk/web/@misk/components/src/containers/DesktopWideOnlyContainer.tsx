import styled from "styled-components"

/**
 * <DesktopWideOnlyContainer.1>
 *    <span>Stuff</span>
 * </DesktopWideOnlyContainer.1>
 */

export const DesktopWideOnlyContainer = styled.div`
  @media (max-width: 1200px) {
    display: none;
  }
`
