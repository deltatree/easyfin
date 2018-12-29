# easyfin
Threadsafe HBCI4JAVA Wrapper for easily accessing your financial accounts so you need no cloud services to do that

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
