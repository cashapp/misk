import styled from "styled-components"

/**
 * <MobileOnlyContainer>
 *    <span>Stuff</span>
 * </MobileOnlyContainer>
 */

export const MobileOnlyContainer = styled.div`
  @media (min-width: 768px) {
    display: none;
  }
`
