import { HomePage, Module } from './components'

const routes =  [{
  path: '/_admin',
  exact: true,
  component: HomePage,
}, {
  path: '/_admin/:moduleID',
  exact: true,
  component: Module
}]

export default routes