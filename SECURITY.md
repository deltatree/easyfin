# Security Policy

## Reporting a vulnerability

easyfin handles online-banking credentials, so security reports are taken seriously.

Please report vulnerabilities **privately** — do not open a public issue:

- via [GitHub Security Advisories](https://github.com/deltatree/easyfin/security/advisories/new), or
- by e-mail to `alexander.widak@deltatree.de`

Please include a description, affected version, and — if possible — a minimal reproduction. You can
expect an acknowledgement within a few working days.

## Supported versions

Fixes are released for the latest published version on
[Maven Central](https://central.sonatype.com/artifact/de.deltatree.pub.apis/easyfin).

## Handling of secrets in easyfin

- PIN, TAN and the passport passphrase are **never written to any log** at any level.
- The transient HBCI passport file is protected with a per-instance passphrase generated from
  `java.security.SecureRandom`.
- The passport file is created in `java.io.tmpdir` (configurable via
  `EasyFinBuilder.passportDirectory(Path)`), registered for deletion on JVM exit, and deleted by
  `EasyFin.clean()`.
- Credentials are held in memory only for the lifetime of the `EasyFin` instance and are never
  transmitted anywhere except to the configured bank endpoint over TLS.
- Certificate checking is enabled by default; disabling it
  (`client.passport.PinTan.checkcert=0`) is intended for tests against local mock endpoints only.
