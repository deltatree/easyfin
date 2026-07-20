# easyfin

[![Build](https://github.com/deltatree/easyfin/actions/workflows/build.yml/badge.svg)](https://github.com/deltatree/easyfin/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/deltatree/easyfin)](https://github.com/deltatree/easyfin/releases)
[![Maven Central](https://img.shields.io/maven-central/v/de.deltatree.pub.apis/easyfin)](https://central.sonatype.com/artifact/de.deltatree.pub.apis/easyfin)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Threadsafe Java wrapper around [HBCI4Java](https://github.com/hbci4j/hbci4java) for reading your
German bank accounts over **FinTS/HBCI PIN/TAN** — directly from your own code, with **no cloud
service in between**.

easyfin gives you a small fluent API for the two things most people actually need: *list my
accounts* and *give me my turnovers*. It handles the HBCI dialog, thread confinement, passport
lifecycle and TAN callbacks for you.

## Requirements

- **Java 21** or newer
- **`hbci4j-core`** on your classpath — easyfin declares it as a *provided* dependency, so you
  choose the version. easyfin is built and tested against **4.1.11**, which requires Java 17+ and
  brings Jakarta JAXB (`jakarta.xml.bind-api`, `jaxb-runtime`) transitively.

## Installation

### Gradle

```gradle
dependencies {
  implementation 'com.github.hbci4j:hbci4j-core:4.1.11'
  implementation 'de.deltatree.pub.apis:easyfin:<latest.version>'
}
```

### Maven

```xml
<dependencies>
  <dependency>
    <groupId>com.github.hbci4j</groupId>
    <artifactId>hbci4j-core</artifactId>
    <version>4.1.11</version>
  </dependency>
  <dependency>
    <groupId>de.deltatree.pub.apis</groupId>
    <artifactId>easyfin</artifactId>
    <version>LATEST</version>
  </dependency>
</dependencies>
```

For the current version see the [releases page](https://github.com/deltatree/easyfin/releases) or
[Maven Central](https://central.sonatype.com/artifact/de.deltatree.pub.apis/easyfin).

## Quickstart

```java
EasyFin easyFin = EasyFinFactory.builder()
        .bankData("GENODEF1S02")          // search by name, BIC or BLZ
        .userId("myVRNetKey")             // for Volksbanken (agree21) and Sparkassen
        .customerId("myVRNetKey")         // customerId and userId are usually identical
        .pin("1234")
        .tanCallback(challenge -> askUserForTan(challenge.get("challenge")))
        .build();
try {
    for (Konto account : easyFin.getAccounts()) {
        easyFin.getTurnoversAsStream(account, tenDaysAgo())
               .forEach(t -> System.out.println(account.number + " " + t.value + " " + t.usage));
    }
} finally {
    easyFin.clean();       // releases the worker thread and deletes the passport file
    EasyFinFactory.clean();
}
```

See [docs/usage.md](docs/usage.md) for the full guide, including TAN-method selection, proxies and
error handling.

## API at a glance

| Call | What it does |
| --- | --- |
| `EasyFinFactory.builder()` | starts the fluent configuration |
| `EasyFin.getAccounts()` | lists all accounts reachable with your credentials |
| `EasyFin.getAccount(search)` | finds exactly one account by number, IBAN, name, BIC or BLZ |
| `EasyFin.getTurnoversAsStream(account[, from][, mode])` | reads turnovers (MT940 or camt) |
| `EasyFin.clean()` | releases resources; idempotent, call it in a `finally` block |

Failures surface as `IllegalStateException` with a `"<operation> failed: <detail>"` message and the
original cause attached.

## Building from source

```bash
./gradlew build      # compile, format check, SpotBugs, unit + E2E tests, Javadoc, coverage
```

The end-to-end tests drive the real HBCI4Java client over real TLS against an embedded **simulated
FinTS bank** that completes the actual protocol dialog — synchronisation, BPD/UPD negotiation and an
MT940 statement — so listing accounts and reading turnovers are verified without a bank or
credentials. To additionally verify against your real bank:

```bash
EASYFIN_REALBANK_SEARCH="GENODEF1S02" \
EASYFIN_REALBANK_USERID="myVRNetKey" \
EASYFIN_REALBANK_CUSTOMERID="myVRNetKey" \
EASYFIN_REALBANK_PIN="1234" \
  ./gradlew realBankTest
```

## GitHub Packages

```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = "https://maven.pkg.github.com/deltatree/easyfin"
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}
```

## Security

easyfin never logs your PIN, TAN or passport passphrase. The transient HBCI passport file is
encrypted with a per-instance `SecureRandom` passphrase and deleted on `clean()`. Please report
vulnerabilities as described in [SECURITY.md](SECURITY.md).

## License

Apache-2.0 — see [LICENSE](LICENSE).
