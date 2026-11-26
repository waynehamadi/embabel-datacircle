package dev.datacircle.embabel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupPermission;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class DatacircleToolsTest {

    private static final String APIS = """
            [{"api": "Employee email", "slug": "email-finder", "plans": 5444, "providers": 300,
              "best": {"provider": "Emelia", "plan": "100,000 credits per month", "score": 76.6, "perThousand": 4.99}},
             {"api": "Company profile", "slug": "linkedin-company", "plans": 1, "providers": 1, "best": null}]
            """;
    private static final String BEST = """
            {"api": "Employee email", "slug": "email-finder", "total": 5444,
             "sheetPage": "https://datacircle.dev/email-finder-api-pricing",
             "plans": [{"provider": "Emelia", "plan": "Pro", "api": "Employee email", "rank": 1, "score": 76.64,
                        "perThousand": 4.99, "per": "call", "notFoundPerThousand": 0, "billing": "monthly",
                        "latencyMs": 812, "page": "https://datacircle.dev/find-email-apis/emelia/pro"}]}
            """;
    private static final String PLANS = """
            {"total": 2, "offset": 1, "plans": [{"provider": "Hunter", "plan": "Starter", "api": "People search",
             "rank": 18, "score": null, "perThousand": null, "page": "https://datacircle.dev/p/hunter"}]}
            """;
    private static final String PROVIDER = """
            {"provider": "Bright Data", "slug": "bright-data", "website": "https://brightdata.com",
             "apis": [{"api": "Employee profile", "slug": "linkedin-profile", "rank": 3,
                       "plans": [{"plan": "Pay as you go", "score": 60, "perThousand": 1.5, "per": "record"},
                                 {"plan": "Growth", "score": 71.25, "perThousand": 1.05, "per": "record"}]},
                      {"api": "SERP", "slug": "serp", "rank": null, "plans": []}]}
            """;
    private static final List<String> NAMES =
            List.of("datacircle_list_apis", "datacircle_best_plans", "datacircle_search_plans", "datacircle_provider");

    private static HttpServer server;
    private static final List<String> requests = new ArrayList<>();
    private Map<String, Tool> tools;

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/", exchange -> {
            String target = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(target);
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String body;
            if (path.equals("/v1/apis")) {
                body = APIS;
            } else if (path.equals("/v1/apis/email-finder/best")) {
                body = BEST;
            } else if (path.equals("/v1/apis/broken/best")) {
                body = "{\"api\": ";
            } else if (path.equals("/v1/plans") && target.contains("query=busy")) {
                status = 429;
                body = "{\"error\": \"too many calls\"}";
            } else if (path.equals("/v1/plans") && target.contains("api=nope")) {
                status = 400;
                body = "{\"error\": \"no API is named nope\"}";
            } else if (path.equals("/v1/plans")) {
                body = PLANS;
            } else if (path.equals("/v1/providers/bright-data")) {
                body = PROVIDER;
            } else {
                status = 404;
                body = "{\"error\": \"no API has the slug nope\", \"slugs\": [\"email-finder\"]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    }

    @BeforeEach
    void setUp() {
        requests.clear();
        tools = byName(DatacircleTools.all(baseUrl(), Duration.ofSeconds(5)));
    }

    @Test
    void listsApisAndFiltersThemByWords() {
        assertEquals(
                "- Employee email (email-finder): 5,444 plans from 300 providers. Best: Emelia, 100,000 credits per "
                        + "month, score 76.6/100, $4.99 per 1,000.\n"
                        + "- Company profile (linkedin-company): 1 plan from 1 provider.\n"
                        + "2 of 2 APIs. Pass a slug to datacircle_best_plans or datacircle_search_plans.",
                text(call("datacircle_list_apis", "{}")));
        assertEquals(text(call("datacircle_list_apis", "{}")), text(call("datacircle_list_apis", "")));
        String found = text(call("datacircle_list_apis", "{\"query\": \"EMAIL finder\"}"));
        assertTrue(found.startsWith("- Employee email (email-finder)"), found);
        assertTrue(found.contains("1 of 2 APIs."), found);
        assertEquals(
                "No API's name or slug holds every word of \"zzz\". Leave the query empty to list 2 APIs.",
                text(call("datacircle_list_apis", "{\"query\": \" zzz \"}")));
        assertEquals(5, requests.size());
        assertTrue(requests.stream().allMatch("/v1/apis"::equals), requests.toString());
    }

    @Test
    void givesTheBestPlans() {
        assertEquals(
                "Employee email (email-finder): the best 1 of 5,444 plans. "
                        + "Sheet: https://datacircle.dev/email-finder-api-pricing\n"
                        + "1. Emelia, Pro: score 76.6/100, $4.99 per 1,000 calls, $0 per 1,000 not found, monthly, "
                        + "median 812 ms. https://datacircle.dev/find-email-apis/emelia/pro",
                text(call("datacircle_best_plans", "{\"api\": \" Email-Finder \", \"limit\": 3}")));
        call("datacircle_best_plans", "{\"api\": \"email-finder\", \"limit\": \"4\"}");
        call("datacircle_best_plans", "{\"api\": \"email-finder\"}");
        assertEquals(
                List.of("/v1/apis/email-finder/best?limit=3", "/v1/apis/email-finder/best?limit=4",
                        "/v1/apis/email-finder/best?limit=10"),
                requests);
    }

    @Test
    void searchesThePlans() {
        assertEquals(
                "Found 2 plans; showing 2 to 2.\n"
                        + "- Hunter, Starter (People search): rank 18, not scored, no listed price. "
                        + "https://datacircle.dev/p/hunter",
                text(call("datacircle_search_plans",
                        "{\"api\": \"people search\", \"query\": \"hunter io\", \"sort\": \"Price\", \"limit\": 5, "
                                + "\"offset\": 1}")));
        call("datacircle_search_plans", "{}");
        assertEquals(
                List.of("/v1/plans?sort=price&limit=5&offset=1&api=people+search&query=hunter+io",
                        "/v1/plans?sort=score&limit=10&offset=0"),
                requests);
    }

    @Test
    void givesAProvidersRanks() {
        assertEquals(
                "Bright Data (https://brightdata.com) sells 2 APIs. Its rank among each API's providers:\n"
                        + "- Employee profile (linkedin-profile): rank 3, 2 plans. Best: Growth, score 71.3/100, "
                        + "$1.05 per 1,000 records.\n"
                        + "- SERP (serp): not ranked, 0 plans.",
                text(call("datacircle_provider", "{\"provider\": \"Bright Data\"}")));
        assertEquals(List.of("/v1/providers/bright-data"), requests);
    }

    @Test
    void saysWhatWentWrongAsAnErrorResult() {
        assertError(
                "Datacircle API request failed: HTTP 404. no API has the slug nope. "
                        + "Call datacircle_list_apis for every API's slug.",
                call("datacircle_best_plans", "{\"api\": \"nope\"}"));
        assertError(
                "Datacircle API request failed: HTTP 400. no API is named nope. "
                        + "Call datacircle_list_apis for every API's name and slug.",
                call("datacircle_search_plans", "{\"api\": \"nope\"}"));
        assertError(
                "Datacircle API request failed: HTTP 429. too many calls. The API allows 60 calls a minute "
                        + "from one address. Retry in a minute.",
                call("datacircle_search_plans", "{\"query\": \"busy\"}"));
        assertError("The Datacircle API returned invalid JSON.", call("datacircle_best_plans", "{\"api\": \"broken\"}"));
        assertError("limit must be between 1 and 50", call("datacircle_best_plans", "{\"api\": \"email-finder\", \"limit\": 51}"));
        assertError("limit must be a whole number", call("datacircle_best_plans", "{\"api\": \"email-finder\", \"limit\": 2.5}"));
        assertError("sort must be one of [price, provider, score]", call("datacircle_search_plans", "{\"sort\": \"x\"}"));
        assertError("offset must be 0 or more", call("datacircle_search_plans", "{\"offset\": -1}"));
        assertError("api must not be empty", call("datacircle_best_plans", "{\"api\": \" \"}"));
        assertError("api must not be empty", call("datacircle_best_plans", "{}"));
        assertError("the arguments are not valid JSON", call("datacircle_best_plans", "{api"));
        assertError("the arguments must be a JSON object", call("datacircle_best_plans", "[1]"));
        assertError("api must be a string", call("datacircle_best_plans", "{\"api\": [\"email-finder\"]}"));
        assertTrue(error(call("datacircle_provider", "{\"provider\": \"北京\"}"))
                .startsWith("Datacircle writes each provider's name in Latin letters"));
        assertTrue(requests.stream().noneMatch(r -> r.contains("limit=51")), requests.toString());
        assertTrue(requests.stream().noneMatch(r -> r.contains("/providers/")), requests.toString());
    }

    @Test
    void saysWhenTheApiIsUnreachable() {
        Map<String, Tool> offline = byName(DatacircleTools.all("http://127.0.0.1:1/v1", Duration.ofSeconds(2)));
        assertError(
                "The Datacircle API refused the connection. Check the network and the base URL.",
                offline.get("datacircle_list_apis").call("{}"));
    }

    @Test
    void givesTheModelFourToolsWithTheirSchemas() {
        assertEquals(NAMES, DatacircleTools.all().stream().map(t -> t.getDefinition().getName()).toList());
        assertEquals(Set.of("api", "query", "sort", "limit", "offset"), properties("datacircle_search_plans").keySet());
        assertEquals(Set.of("query"), properties("datacircle_list_apis").keySet());
        assertEquals("integer", type(properties("datacircle_best_plans").get("limit")));
        assertEquals("string", type(properties("datacircle_provider").get("provider")));
        assertEquals(List.of("score", "provider", "price"),
                ((Map<?, ?>) properties("datacircle_search_plans").get("sort")).get("enum"));
        assertEquals(List.of("api"), required("datacircle_best_plans"));
        assertEquals(List.of("provider"), required("datacircle_provider"));
        assertEquals(List.of(), required("datacircle_search_plans"));
        assertEquals(List.of(), required("datacircle_list_apis"));
        for (Tool tool : tools.values()) {
            assertFalse(tool.getDefinition().getDescription().isBlank(), tool.getDefinition().getName());
            assertFalse(tool.getMetadata().getReturnDirect(), tool.getDefinition().getName());
            for (Object property : properties(tool.getDefinition().getName()).values()) {
                assertFalse(((String) ((Map<?, ?>) property).get("description")).isBlank());
            }
        }
    }

    @Test
    void theToolGroupHoldsTheFourToolsUnderItsRole() {
        ToolGroup group = DatacircleTools.toolGroup(baseUrl(), Duration.ofSeconds(5));
        ToolGroupMetadata metadata = group.getMetadata();
        assertEquals("datacircle", DatacircleTools.ROLE);
        assertEquals(DatacircleTools.ROLE, metadata.getRole());
        assertEquals("datacircle", metadata.getName());
        assertEquals("Datacircle", metadata.getProvider());
        assertTrue(metadata.infoString(true, 0).contains("0.1.0"), metadata.infoString(true, 0));
        assertEquals(Set.of(ToolGroupPermission.INTERNET_ACCESS), metadata.getPermissions());
        assertFalse(metadata.getDescription().isBlank());
        assertEquals(NAMES, group.getTools().stream().map(t -> t.getDefinition().getName()).toList());
        String answer = text(byName(group.getTools()).get("datacircle_best_plans").call("{\"api\": \"email-finder\"}"));
        assertTrue(answer.startsWith("Employee email (email-finder): the best 1"), answer);
        assertEquals(List.of("/v1/apis/email-finder/best?limit=10"), requests);
    }

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DatacircleAutoConfiguration.class));

    @Test
    void aSpringBootAppHasTheToolGroupByItself() {
        context.withPropertyValues("datacircle.base-url=" + baseUrl()).run(app -> {
            ToolGroup group = app.getBean(ToolGroup.class);
            assertSame(group, app.getBean("datacircleToolGroup"));
            assertEquals(DatacircleTools.ROLE, group.getMetadata().getRole());
            String answer = text(byName(group.getTools()).get("datacircle_provider").call("{\"provider\": \"bright-data\"}"));
            assertTrue(answer.startsWith("Bright Data (https://brightdata.com) sells 2 APIs."), answer);
            assertEquals(List.of("/v1/providers/bright-data"), requests);
        });
        context.run(app -> assertEquals(1, app.getBeansOfType(ToolGroup.class).size()));
    }

    @Test
    void theAppTurnsTheToolGroupOffOrGivesItsOwn() {
        context.withPropertyValues("datacircle.tool-group.enabled=false")
                .run(app -> assertTrue(app.getBeansOfType(ToolGroup.class).isEmpty()));
        context.withPropertyValues("datacircle.tool-group.enabled=true")
                .run(app -> assertEquals(1, app.getBeansOfType(ToolGroup.class).size()));
        context.withUserConfiguration(OwnToolGroup.class).run(app -> {
            assertEquals(1, app.getBeansOfType(ToolGroup.class).size());
            assertSame(OwnToolGroup.GROUP, app.getBean("datacircleToolGroup"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnToolGroup {
        static final ToolGroup GROUP = DatacircleTools.toolGroup("http://127.0.0.1:9/v1");

        @Bean
        ToolGroup datacircleToolGroup() {
            return GROUP;
        }
    }

    private Tool.Result call(String name, String input) {
        return tools.get(name).call(input);
    }

    private static Map<String, Tool> byName(List<? extends Tool> list) {
        Map<String, Tool> map = new TreeMap<>();
        for (Tool tool : list) {
            map.put(tool.getDefinition().getName(), tool);
        }
        return map;
    }

    private Map<?, ?> schema(String name) {
        return (Map<?, ?>) Json.parse(tools.get(name).getDefinition().getInputSchema().toJsonSchema());
    }

    private Map<?, ?> properties(String name) {
        return (Map<?, ?>) schema(name).get("properties");
    }

    private List<?> required(String name) {
        Object required = schema(name).get("required");
        return required == null ? List.of() : (List<?>) required;
    }

    private static String type(Object property) {
        return (String) ((Map<?, ?>) property).get("type");
    }

    private static String text(Tool.Result result) {
        return assertInstanceOf(Tool.Result.Text.class, result, result.toString()).getContent();
    }

    private static String error(Tool.Result result) {
        return assertInstanceOf(Tool.Result.Error.class, result, result.toString()).getMessage();
    }

    private static void assertError(String message, Tool.Result result) {
        assertEquals(message, error(result));
    }
}
