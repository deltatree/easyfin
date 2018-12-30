# easyfin
Threadsafe HBCI4JAVA Wrapper for easily accessing your financial accounts

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
dependencies {
  compile 'de.deltatree.pub.apis:easyfin:1.0.1'
}
```

Maven:
```maven
...
<dependencies>
  <dependency>
    <groupId>de.deltatree.pub.apis</groupId>
    <artifactId>easyfin</artifactId>
    <version>1.0.1</version>
  </dependency>
</dependencies>
...
```
