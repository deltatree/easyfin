# easyfin usage guide

This guide covers everything beyond the README quickstart.

## 1. Configuring the connection

```java
EasyFin easyFin = EasyFinFactory.builder()
        .bankData("GENODEF1S02")
        .userId("myVRNetKey")
        .customerId("myVRNetKey")
        .pin("1234")
        .tanCallback(challenge -> askUserForTan(challenge.get("challenge")))
        .build();
```

`pin(...)` and bank data are **required**; `build()` fails fast with an `IllegalStateException`
naming the missing value.

### Selecting the bank

`bankData(String)` searches the bundled German bank directory by **name, BIC or BLZ** (case
insensitive, regex-capable):

- exactly one match → used
- no match → `IllegalStateException` naming your search term
- several matches → `IllegalStateException` telling you to refine the search (use the BLZ or BIC)

You can also supply your own endpoint explicitly, which is useful for test or private FinTS servers:

```java
DefaultBankData bank = new DefaultBankData();
bank.setBlz("12345678");
bank.setPinTanVersion("300");
bank.setPinTanAddress("https://fints.example.de/fints");
builder.bankData(bank);
```

When `pinTanAddress` is set, easyfin connects to exactly that endpoint (including a non-default
port). Otherwise HBCI4Java's built-in bank directory decides.

## 2. TAN handling

### The TAN callback

The callback is invoked whenever the bank asks for a TAN. It receives a map with the bank's
challenge text under the key `"challenge"` (`MyHBCICallback.CHALLENGE_KEY`) and must return the TAN:

```java
.tanCallback(ctx -> {
    System.out.println(ctx.get("challenge"));   // e.g. "Bitte TAN eingeben"
    return readLineFromUser();
})
```

### Choosing the TAN method

If your bank offers several TAN methods (chipTAN, pushTAN, …), easyfin picks the first one by
default. To decide yourself, install a selector:

```java
.tanMethodSelector(offered -> {
    // offered: code -> label, in the order the bank announced them
    return offered.entrySet().stream()
            .filter(e -> e.getValue().contains("pushTAN"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(offered.keySet().iterator().next());
})
```

Returning `null` or a code the bank did not offer fails fast with an `IllegalStateException` listing
the valid codes — easyfin never silently falls back.

## 3. Reading data

```java
List<Konto> accounts = easyFin.getAccounts();
Konto giro = easyFin.getAccount("DE02120300000000202051");   // number, IBAN, name, BIC or BLZ
```

`getAccount(search)` requires exactly one match; zero or several matches throw.

```java
// all turnovers, MT940
easyFin.getTurnoversAsStream(giro);

// since a date
easyFin.getTurnoversAsStream(giro, tenDaysAgo());

// camt.052 instead of MT940
easyFin.getTurnoversAsStream(giro, tenDaysAgo(), GetTurnoversModeEnum.KUmsAllCamt);
```

The returned `Stream` is backed by a fully materialized list — the bank dialog completes before the
stream is handed to you. It is not lazy and needs no closing.

## 4. Resource lifecycle

```java
EasyFin easyFin = builder.build();
try {
    // ... use it ...
} finally {
    easyFin.clean();
}
```

- `clean()` shuts the worker thread down and deletes the transient passport file.
- `clean()` is **idempotent** — calling it repeatedly is safe.
- After `clean()` the instance is dead: further calls throw `IllegalStateException`.
- `EasyFinFactory.clean()` shuts HBCI4Java down process-wide; call it once when your application
  exits, not per client.

By default the passport file lives in `java.io.tmpdir`. Override it with:

```java
builder.passportDirectory(Paths.get("/var/run/myapp/passports"));
```

## 5. Threading

Every instance owns a single worker thread; all HBCI work is confined to it. An instance is
therefore safe to share across threads, but calls are executed sequentially. For real parallelism
create several instances — they are fully isolated from each other (separate passport files and
passport factories).

## 6. Proxies and advanced HBCI settings

```java
builder.proxy("proxy.intern.example.com:3128")
       .additionalHBCIConfiguration("client.passport.PinTan.checkcert", "1")
       .additionalHBCIConfiguration("anyOther.hbci4java.property", "value");
```

`additionalHBCIConfiguration(...)` entries are applied **last** and therefore override everything
easyfin sets itself.

## 7. Error handling

All failures are `IllegalStateException` with a message of the form
`"<operation> failed: <detail>"` and the original exception attached as the cause:

```java
try {
    easyFin.getAccounts();
} catch (IllegalStateException e) {
    log.error("could not read accounts: {}", e.getMessage(), e.getCause());
}
```

Common cases:

| Situation | Message starts with |
| --- | --- |
| bank unreachable / TLS problem | `Fetching accounts failed: ` |
| wrong PIN or bank-side rejection | `Fetching accounts failed: ` / `Fetching turnovers failed: ` |
| turnover retrieval rejected | `Fetching turnovers failed: ` |
| instance already cleaned | `This EasyFin instance has been cleaned...` |

## 8. Testing your integration

easyfin's own end-to-end tests drive the real HBCI4Java client over TLS against an embedded mock
FinTS endpoint (`src/test/.../mockfints/MockFinTsServer`). You can use the same approach for your
integration tests: point `BankData.pinTanAddress` at your test endpoint and set
`client.passport.PinTan.checkcert` to `0` for self-signed certificates.

To validate against a real bank, use the opt-in runner:

```bash
EASYFIN_REALBANK_SEARCH="GENODEF1S02" \
EASYFIN_REALBANK_USERID="myVRNetKey" \
EASYFIN_REALBANK_CUSTOMERID="myVRNetKey" \
EASYFIN_REALBANK_PIN="1234" \
  ./gradlew realBankTest
```
