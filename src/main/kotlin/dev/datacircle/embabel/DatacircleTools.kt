package dev.datacircle.embabel

import com.embabel.agent.api.tool.Tool
import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.ToolGroupMetadata
import com.embabel.agent.core.ToolGroupPermission
import com.embabel.common.core.types.Semver
import java.time.Duration

/**
 * Embabel tools for the open [Datacircle](https://datacircle.dev) API, which benchmarks the GTM APIs (email
 * finders, company enrichment, LinkedIn data and more) and ranks every plan of each by its score out of 100 and its
 * price per 1,000.
 *
 * Give [all] to a prompt runner beside your own tools, or ask for the tool group by its role, [ROLE]: a Spring Boot
 * app has it as a bean by itself.
 *
 * ```java
 * String answer = ai.withDefaultLlm()
 *         .withTools(DatacircleTools.all())
 *         .generateText("Which email finder API is the cheapest per 1,000?");
 *
 * @Action
 * public Shortlist shortlist(Brief brief, Ai ai) {
 *     return ai.withDefaultLlm().withToolGroup(DatacircleTools.ROLE).createObject(brief.prompt(), Shortlist.class);
 * }
 * ```
 *
 * The API needs no key and no account. It allows 60 calls a minute from one address. Its spec is at
 * `https://api.datacircle.dev/v1/openapi.json`.
 */
object DatacircleTools {

    /** The tool group's role, for `withToolGroup(...)`. */
    const val ROLE = "datacircle"

    const val LIST_APIS = "datacircle_list_apis"
    const val BEST_PLANS = "datacircle_best_plans"
    const val SEARCH_PLANS = "datacircle_search_plans"
    const val PROVIDER = "datacircle_provider"

    const val DEFAULT_BASE_URL = "https://api.datacircle.dev/v1"

    @JvmField
    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)

    /**
     * The four tools, on `https://api.datacircle.dev/v1` unless [baseUrl] says another address.
     *
     * @param baseUrl the API's base URL
     * @param timeout the connection and answer timeout of each call
     */
    @JvmStatic
    @JvmOverloads
    fun all(baseUrl: String = DEFAULT_BASE_URL, timeout: Duration = DEFAULT_TIMEOUT): List<Tool> {
        val calls = Calls(baseUrl, timeout)
        return listOf(
            Tool.create(
                LIST_APIS,
                "List the GTM APIs that Datacircle ranks (email finder, company enrichment, LinkedIn profile and " +
                    "more), each with its slug, how many plans and providers it has, and its best plan.",
                Tool.InputSchema.of(
                    Tool.Parameter.string(
                        "query",
                        "Words that must all appear in the API's name or slug, like email or linkedin. Leave " +
                            "empty to list every API",
                        false,
                    ),
                ),
            ) { input -> calls.listApis(input) },
            Tool.create(
                BEST_PLANS,
                "Get the best plans of one GTM API, best first by Datacircle's score out of 100, with each plan's " +
                    "price per 1,000 and its page.",
                Tool.InputSchema.of(
                    Tool.Parameter.string("api", "The API's slug, as $LIST_APIS gives it, like email-finder"),
                    Tool.Parameter.integer("limit", "Number of plans from 1 through 50. Defaults to 10", false),
                ),
            ) { input -> calls.bestPlans(input) },
            Tool.create(
                SEARCH_PLANS,
                "Search the plans of the GTM APIs on Datacircle by API, and by words in the provider's or plan's " +
                    "name, sorted by score (best first), provider (A to Z) or price (lowest $ per 1,000 first).",
                Tool.InputSchema.of(
                    Tool.Parameter.string(
                        "api",
                        "The API's name or slug, like email-finder. A provider's name goes in query. Defaults to " +
                            "every API",
                        false,
                    ),
                    Tool.Parameter.string(
                        "query",
                        "Words that must all appear in the provider's or plan's name, like hunter",
                        false,
                    ),
                    Tool.Parameter.string(
                        "sort",
                        "Sort: score, provider or price. Defaults to score",
                        false,
                        listOf("score", "provider", "price"),
                    ),
                    Tool.Parameter.integer("limit", "Number of plans from 1 through 50. Defaults to 10", false),
                    Tool.Parameter.integer("offset", "Number of found plans to skip first. Defaults to 0", false),
                ),
            ) { input -> calls.searchPlans(input) },
            Tool.create(
                PROVIDER,
                "Get one provider's rank on each GTM API it sells on Datacircle, with its best plan there. A " +
                    "provider is a company that sells the APIs, like Hunter or Bright Data.",
                Tool.InputSchema.of(
                    Tool.Parameter.string("provider", "The provider's name or slug, like Bright Data or bright-data"),
                ),
            ) { input -> calls.provider(input) },
        )
    }

    /**
     * The four tools as one tool group, its role [ROLE], for an app that registers tool groups itself (a Spring
     * Boot app has it already).
     *
     * @param baseUrl the API's base URL
     * @param timeout the connection and answer timeout of each call
     */
    @JvmStatic
    @JvmOverloads
    fun toolGroup(baseUrl: String = DEFAULT_BASE_URL, timeout: Duration = DEFAULT_TIMEOUT): ToolGroup =
        ToolGroup.ofTools(
            ToolGroupMetadata(
                description = "Datacircle's ranking of the GTM APIs (email finder, company enrichment, LinkedIn " +
                    "profile and more): list the APIs, get one API's best plans, search every plan by score or " +
                    "price per 1,000, and get a provider's ranks. Open API, no key.",
                role = ROLE,
                name = "datacircle",
                provider = "Datacircle",
                permissions = setOf(ToolGroupPermission.INTERNET_ACCESS),
                version = Semver(0, 1, 0),
            ),
            all(baseUrl, timeout),
        )
}
