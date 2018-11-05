import styled from "styled-components"

/**
 * <ResponsiveContainer>
 *    <span>Stuff</span>
 * </ResponsiveContainer>
 */

export const ResponsiveContainer = styled.div`
  margin: 0 auto;
  @media (min-width: 768px) {
    width: 750px;
  }
  @media (min-width: 992px) {
    width: 970px;
  }
  @media (min-width: 1200px) {
    width: 1170px;
  }
`
