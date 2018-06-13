import React from 'react';
const { Link } = require('frint-router-react');
import { MenuItem, MenuDivider } from '@blueprintjs/core';

export namespace MenuComponent {
  export interface Props {
    match: {
      isExact: boolean,
      params: {
        moduleID: string
      },
      url: string
    }
  }
}

class MenuComponent extends React.Component<MenuComponent.Props> {
  render() {
    if (false) {
      return (
        <Link
            to="/"
            className="nav-item is-tab"
            activeClassName="is-active"
            exact
          >
          <MenuItem icon="new-object" text="New object"/>
        </Link>
      )
    }

    return (
      <div>
        <Link to="/log4j">Another tests</Link>
        <Link to="/log4j"><MenuItem icon="annotation" text="Log4"/></Link>
        <MenuDivider/>        
      </div>
    );
  }
}

export default MenuComponent;
