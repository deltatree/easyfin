# easyfin
Threadsafe HBCI4JAVA Wrapper for easily accessing your financial accounts. Easyfin uses https://github.com/hbci4j/hbci4java/ as library.

Usage:
See [example](https://github.com/deltatree/easyfin/blob/master/src/test/java/de/deltatree/pub/apis/easyfin/UsageExample.java)

## Docker

Pull and run the Docker image:
```bash
docker pull ghcr.io/deltatree/easyfin:latest
docker run -d -p 8080:8080 ghcr.io/deltatree/easyfin:latest
```

Build locally:
```bash
docker build -t easyfin .
docker run -d -p 8080:8080 easyfin
```

## Gradle

```gradle
dependencies {
  implementation 'com.github.hbci4j:hbci4j-core:3.1.88'
  implementation 'de.deltatree.pub.apis:easyfin:${latest.version}'
}
```

## Maven

```maven
...
<dependencies>
  <dependency>
    <groupId>com.github.hbci4j</groupId>
    <artifactId>hbci4j-core</artifactId>
    <version>3.1.88</version>
  </dependency>
  <dependency>
    <groupId>de.deltatree.pub.apis</groupId>
    <artifactId>easyfin</artifactId>
    <version>${latest.version}</version>
  </dependency>
</dependencies>
...
```

[![Release](https://img.shields.io/github/v/release/deltatree/easyfin)](https://github.com/deltatree/easyfin/releases)
[![Maven Central](https://img.shields.io/maven-central/v/de.deltatree.pub.apis/easyfin)](https://central.sonatype.com/artifact/de.deltatree.pub.apis/easyfin)
[![Docker](https://img.shields.io/badge/docker-ghcr.io-blue)](https://github.com/deltatree/easyfin/pkgs/container/easyfin)

For the latest version, check the [releases page](https://github.com/deltatree/easyfin/releases) or [Maven Central](https://central.sonatype.com/artifact/de.deltatree.pub.apis/easyfin).
