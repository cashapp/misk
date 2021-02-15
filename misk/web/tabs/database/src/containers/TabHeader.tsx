/** @jsx jsx */
import { Button, Callout, Drawer, H1, H5 } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { CodePreContainer, WrapTextContainer } from "@misk/core"
import { useState } from "react"
import { cssFloatLeft, cssTableScroll, cssHeader } from "../components"

export const TabHeader = () => {
  const [isOpenInstructions, setIsOpenInstructions] = useState(false)

  return (
    <span css={cssHeader}>
      <span css={cssFloatLeft}>
        <H1>Database</H1>
      </span>
      <Button
        active={isOpenInstructions}
        css={cssFloatLeft}
        onClick={() => setIsOpenInstructions(!isOpenInstructions)}
      >
        {"Install Instructions"}
      </Button>
      <Drawer
        isOpen={isOpenInstructions}
        onClose={() => setIsOpenInstructions(false)}
        size={Drawer.SIZE_LARGE}
        title={"Install Instructions"}
      >
        <div css={cssTableScroll}>
          <Callout
            title={
              "1. Enable the Database tab Web Actions for Hibernate with installHibernateAdminDashboardWebActions()"
            }
          >
            <CodePreContainer>
              {`// YourServicePersistenceModule.kt

class YourServicePersistenceModule(...) : KAbstractModule() {
  override fun configure() {
    install(SkimHibernateModule(...))

    install(object : HibernateEntityModule(YourServiceDbCluster::class) {
      override fun configureHibernate() {
+       installHibernateAdminDashboardWebActions()
+
        addEntity<DbCustomer>()
        addEntity<DbDesignStudio>()
        addEntity<DbNotificationChannel>()
        addEntity<DbSmsBlockedNumber>()
        addEntity<DbTemplate>()
      }
    })
  }
}`}
            </CodePreContainer>
          </Callout>
          <Callout
            title={
              "2. Configure misk-hibernate.HibernateEntityModule with your queries"
            }
          >
            <WrapTextContainer>
              This database dashboard tab supports a few different query types.
              You will need to update your Persistence module to install queries
              with respective access permissions.
              <H5>Static Query</H5>
              The Misk.Query class allows for defining a query statically so
              that engineers who use this tab only see the constraints, orders,
              and selects pre-configured when running a read only query.
              <H5>Dynamic Query</H5>A dynamic query provides maximum flexibility
              with ad hoc read only queries allowing adding any number of
              constraints, orders, and select path configuration just as you
              would in checked in code.
            </WrapTextContainer>

            <CodePreContainer>
              {`// YourServicePersistenceModule.kt

class YourServicePersistenceModule(...) : KAbstractModule() {
  override fun configure() {
    install(SkimHibernateModule(...))

    install(object : HibernateEntityModule(YourServiceDbCluster::class) {
      override fun configureHibernate() {
        ...
        
-       addEntity<DbDesignStudio>()
+       addEntityWithStaticQuery<DbDesignStudio, DbDesignStudioQuery, AdminDashboardAccess>()
        
        ...
        
-       addEntity<DbTemplate>()
+       addEntityWithDynamicQuery<DbTemplate, AdminDashboardAccess>()
      }
    })
  }
}`}
            </CodePreContainer>
          </Callout>
          <Callout
            title={
              "3. Use AccessAnnotations to configure per query access permissions"
            }
          >
            <WrapTextContainer>
              In some cases, you may want to limit the use of certain read-only
              queries to only engineers, and broader access to others such as
              customer support folks. Access Annotations provide a way to easily
              do that. You can create a new annotation, assign access
              permissions to it, and then add your query with the annotation to
              have it enforce permissions on your queries in the dashboard. Note
              the example below where the DbTemplate DynamicQuery has tighter
              access to only engineers but DbDesignStudio Static Query has
              broader access to anyone who can reach the admin dashboard. By
              idiom, access annotations are defined and bound in your service's
              Access module.
            </WrapTextContainer>

            <CodePreContainer>
              {`// YourServicePersistenceModule.kt

class YourServicePersistenceModule(...) : KAbstractModule() {
  override fun configure() {
    install(SkimHibernateModule(...))

    install(object : HibernateEntityModule(YourServiceDbCluster::class) {
      override fun configureHibernate() {
        ...
        
        addEntityWithStaticQuery<DbDesignStudio, DbDesignStudioQuery, AdminDashboardAccess>()
        
        ...
        
-       addEntityWithDynamicQuery<DbTemplate, AdminDashboardAccess>()
+       addEntityWithDynamicQuery<DbTemplate, DatabaseQueryAdminDashboardAccess>()
      }
    })
  }
}

// YourServiceAccessModule.kt

/** Configures access to authenticated web actions and the admin dashboard. */
internal class YourServiceAccessModule : KAbstractModule() {
  override fun configure() {
    // Give engineers access to the admin dashboard for your service
    multibind<AccessAnnotationEntry>().toInstance(AccessAnnotationEntry<AdminDashboardAccess>(
        capabilities = listOf("admin_console"))
    )

    // Set access for read-only queries through Database tab that only services owners need access
    multibind<AccessAnnotationEntry>().toInstance(AccessAnnotationEntry<DatabaseQueryAdminDashboardAccess>(
        capabilities = listOf("your-service-owners"))
    )
  }
}

/** Set access for read-only queries through Database tab */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DatabaseQueryAdminDashboardAccess
`}
            </CodePreContainer>
          </Callout>
        </div>
      </Drawer>
    </span>
  )
}
