import React from 'react';
import { Region } from 'frint-react';
const { Link, Route, Switch } = require('frint-router-react');
import { Alignment, Button, Navbar, NavbarGroup, NavbarHeading, NavbarDivider, Menu, MenuItem } from '@blueprintjs/core';


import { HomePage, Module } from '../components';

import menu, { menuItem } from '../menu';
import routes from '../routes';

export default class RootContainer extends React.Component {
  render() {
    return (
      <div>
        <Navbar>
          <NavbarGroup align={Alignment.LEFT}>
            <NavbarHeading>Misk Admin</NavbarHeading>
            <NavbarDivider />
            {menu.map(({ title, icon="document", className="pt-minimal", url } : menuItem) => (
              <Link to={url}><Button className={className} icon={icon} text={title} /></Link>
            ))}
          </NavbarGroup>
        </Navbar>
        <div className="misk-menu">
          <Menu>
            {menu.map(({ title, icon="document", className="pt-minimal", url } : menuItem) => (
              <Link to={url}><MenuItem className={className} icon={icon} text={title} /></Link>
            ))}
            <Region name="mainMenu" />
          </Menu>
        </div>
        <div className="container misk-main-container">
          <div className="row">
            <div className="twelve columns">
              <Switch>
                {routes.map(({ path, exact, component: C }) => (
                  <Route key={path} path={path} exact={exact} component={C}/>
                ))}
              </Switch>
              <h3>All Modules</h3>
              <Region name="main" />
            </div>
          </div>
        </div>
      </div>
    );
  }
}
