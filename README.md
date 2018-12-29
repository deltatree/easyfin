# easyfin
Threadsafe HBCI4JAVA Wrapper for easily accessing your financial accounts so you need no cloud services to do that

Usage:
```java
EasyFin to = EasyFinFactory.builder().loginName("VRNetKey Alias/ID").loginPassword("VRNetKey Password")
		.bankData("Name / BIC / BLZ der Zielbank").build();

try {
	final AtomicInteger i = new AtomicInteger(0);

	for (Konto k : to.getAccounts()) {
		to.getTurnoversAsStream(k).forEach(t -> System.out.println(i.incrementAndGet() + " " + t.bdate));
	}
} finally {
	EasyFinFactory.destroyAll();
}
```

Gradle:
```gradle
repositories {
  jcenter()
}

dependencies {
  compile 'de.deltatree.pub.apis:easyfin:1.0.1'
}
```

Maven:
```maven
...
<repositories>
  <repository>
    <id>jcenter</id>
    <url>https://jcenter.bintray.com/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>de.deltatree.pub.apis</groupId>
    <artifactId>easyfin</artifactId>
    <version>1.0.1</version>
  </dependency>
</dependencies>
...
```
