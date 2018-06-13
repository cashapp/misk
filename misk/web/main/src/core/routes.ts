import { HomePage, Module } from './components'

const routes =  [{
  path: '/',
  exact: true,
  component: HomePage,
}, {
  path: '/:moduleID',
  exact: true,
  component: Module
}]

export default routes