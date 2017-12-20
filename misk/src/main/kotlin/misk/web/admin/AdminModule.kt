package misk.web.admin

import misk.inject.KAbstractModule

class AdminModule(vararg val adminServicesLink: AdminServiceLink) : KAbstractModule() {
    override fun configure() {
        newSetBinder<AdminServiceLink>()
        for (adminServiceInfo in adminServicesLink) {
            newSetBinder<AdminServiceLink>().addBinding().toInstance(adminServiceInfo)
        }
    }
}
