package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskModule
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateModule
import misk.web.WebModule

fun main(args: Array<String>) {
    MiskApplication(
            ExemplarConfigModule(),

            MiskModule(),

            WebModule(),

            HibernateModule(),
            ExemplarModule(),
            EnvironmentModule.fromEnvironmentVariable()
    ).startAndAwaitStopped()
}
