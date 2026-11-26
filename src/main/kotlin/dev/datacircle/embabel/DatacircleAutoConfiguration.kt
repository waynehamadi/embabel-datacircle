package dev.datacircle.embabel

import com.embabel.agent.core.ToolGroup
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Gives a Spring Boot app the Datacircle tool group, its role [DatacircleTools.ROLE], so that a prompt runner asks
 * for it by `withToolGroup(DatacircleTools.ROLE)`.
 *
 * `datacircle.base-url` sets another address of the API; `datacircle.tool-group.enabled=false` leaves the group
 * out; a bean of the app's own named `datacircleToolGroup` takes its place.
 */
@AutoConfiguration
@ConditionalOnClass(ToolGroup::class)
@ConditionalOnProperty(prefix = "datacircle.tool-group", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DatacircleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["datacircleToolGroup"])
    fun datacircleToolGroup(environment: Environment): ToolGroup =
        DatacircleTools.toolGroup(environment.getProperty("datacircle.base-url", DatacircleTools.DEFAULT_BASE_URL))
}
