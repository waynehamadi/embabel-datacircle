# Datacircle tools for Embabel

Find the best GTM APIs. We benchmark all the GTM APIs and we put the results on our website.

Our website: https://datacircle.dev

This library gives [Embabel](https://github.com/embabel/embabel-agent) four tools that call Datacircle's open API, https://api.datacircle.dev/v1. You give them to a prompt runner beside your app's own tools, or you ask for them as a tool group. You need no key and no account.

| Tool | What the tool gives | Call |
| --- | --- | --- |
| `datacircle_list_apis` | Every GTM API on Datacircle, with the API's slug, number of plans, number of providers and best plan | `GET /apis` |
| `datacircle_best_plans` | One API's plans, sorted by score out of 100, best first. Each plan comes with a rank and a price per 1,000 calls | `GET /apis/{slug}/best` |
| `datacircle_search_plans` | Plans that match a provider or plan name, on all APIs or on one API. You can sort them by score, provider or price | `GET /plans` |
| `datacircle_provider` | The APIs a provider sells, and the provider's rank on each API | `GET /providers/{slug}` |

## Install

The library is on [JitPack](https://jitpack.io/#waynehamadi/embabel-datacircle). With Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.waynehamadi</groupId>
    <artifactId>embabel-datacircle</artifactId>
    <version>0.1.0</version>
</dependency>
```

With Gradle:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.waynehamadi:embabel-datacircle:0.1.0")
}
```

The library uses the Embabel your app already has, 1.0.0 or later, on Spring Boot 3.5 or 4 and Java 21 or later. It adds no other dependency.

## Give the tools to a prompt runner

```java
import dev.datacircle.embabel.DatacircleTools;

String answer = ai.withDefaultLlm()
        .withTools(DatacircleTools.all())
        .withTool(myTool)
        .generateText("Who are the 3 best providers for the email-finder API?");
```

## Ask for the tool group

A Spring Boot app gets the tool group by itself. Its role is `datacircle`, so an action asks for it by name:

```java
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import dev.datacircle.embabel.DatacircleTools;

@Agent(description = "Finds the best GTM API for a brief")
public class ShortlistAgent {

    @Action
    @AchievesGoal(description = "The brief is answered from Datacircle's ranking")
    public Answer answer(Brief brief, Ai ai) {
        return new Answer(ai.withDefaultLlm()
                .withToolGroup(DatacircleTools.ROLE)
                .generateText(brief.text()));
    }
}
```

An app that registers its tool groups itself uses `DatacircleTools.toolGroup()`.

## What the tools answer

Each tool answers in short text, one API or plan a line, with each plan's page on datacircle.dev. For example, `datacircle_best_plans` with `{"api": "email-finder", "limit": 3}` answers:

```text
Employee email (email-finder): the best 3 of 5,444 plans. Sheet: https://datacircle.dev/email-finder-api-pricing
1. Emelia, 100,000 credits per month: score 76.6/100, $4.99 per 1,000 calls, $0 per 1,000 not found, monthly. https://datacircle.dev/find-email-apis/emelia/19-monthly
2. ...
```

When a call fails, the tool gives an error result that says what to do, for example when a slug does not exist. It does not throw.

## Options

`DatacircleTools.all(baseUrl, timeout)` and `DatacircleTools.toolGroup(baseUrl, timeout)` set another base URL or another timeout. The defaults are `https://api.datacircle.dev/v1` and 30 seconds.

In a Spring Boot app:

| Property | What it does |
| --- | --- |
| `datacircle.base-url` | Another base URL for the tool group |
| `datacircle.tool-group.enabled=false` | Leaves the tool group out |

A bean of your own named `datacircleToolGroup` takes the place of ours.

The API allows 60 calls a minute from one address. Its spec is at https://api.datacircle.dev/v1/openapi.json.

## Tests

```bash
./mvnw test                                                  # offline, on a local server
DATACIRCLE_LIVE=1 ./mvnw test                                # also calls each tool once on the live API
./mvnw test -Dembabel.version=1.5.4 -Dspring-boot.version=4.1.1
```

## License

MIT
