import HomePage from './components/HomePage'
import Module from './components/Module'

const routes =  [
  {
    path: '/',
    exact: true,
    component: HomePage,
  },
  {
    path: '/:moduleID',
    exact: true,
    component: Module
  }
]

export default routes