package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.deltatree.pub.apis.easyfin.mockfints.MockBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UJ-4 — the lifecycle journey: a consumer creates clients repeatedly (e.g. in a
 * polling loop), sometimes concurrently, and always cleans up in a
 * {@code finally} block. Nothing may leak and cleanup must be safe to repeat.
 */
class Uj4LifecycleE2ETest {

	@TempDir
	Path passportDir;

	@Test
	@DisplayName("UJ-4: repeated create-use-clean cycles leave no passport files behind")
	void repeatedCyclesLeaveNothingBehind() throws IOException {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			for (int i = 0; i < 3; i++) {
				EasyFin ef = MockBank.clientFor(server, passportDir);
				try {
					ef.getAccounts();
				} catch (IllegalStateException expected) {
					// the mock rejects; the lifecycle is what we assert
				} finally {
					ef.clean();
				}
			}
			assertEquals(3, server.getRequests().size(), "each cycle must open its own dialog");
			assertNoPassportFilesLeft();
		}
	}

	@Test
	@DisplayName("UJ-4: two concurrent clients do not interfere and both clean up")
	void concurrentClientsAreIsolated() throws Exception {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			int clients = 2;
			CountDownLatch ready = new CountDownLatch(clients);
			CountDownLatch go = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(clients);
			AtomicInteger failures = new AtomicInteger();

			for (int i = 0; i < clients; i++) {
				Thread t = new Thread(() -> {
					EasyFin ef = MockBank.clientFor(server, passportDir);
					try {
						ready.countDown();
						go.await(10, TimeUnit.SECONDS);
						ef.getAccounts();
					} catch (IllegalStateException expected) {
						// expected: the mock rejects
					} catch (Exception unexpected) {
						failures.incrementAndGet();
					} finally {
						ef.clean();
						done.countDown();
					}
				});
				t.setDaemon(true);
				t.start();
			}

			assertTrue(ready.await(10, TimeUnit.SECONDS), "clients should start");
			go.countDown();
			assertTrue(done.await(60, TimeUnit.SECONDS), "clients should finish");

			assertEquals(0, failures.get(), "no unexpected failures across concurrent clients");
			assertEquals(clients, server.getRequests().size(), "each client opens its own dialog");
			assertNoPassportFilesLeft();
		}
	}

	@Test
	@DisplayName("UJ-4: clean() is idempotent and using a cleaned client fails clearly")
	void cleanIsIdempotentAndGuardsReuse() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			ef.clean();
			ef.clean();
			ef.clean();

			IllegalStateException ex = assertThrows(IllegalStateException.class, ef::getAccounts);
			assertTrue(ex.getMessage().contains("cleaned"), "unexpected message: " + ex.getMessage());
			assertEquals(0, server.getRequests().size(), "a cleaned client must not contact the bank");
		}
	}

	private void assertNoPassportFilesLeft() throws IOException {
		try (Stream<Path> files = Files.list(passportDir)) {
			List<String> leftovers = files.map(p -> p.getFileName().toString())
					.filter(n -> n.startsWith("hbci---") || n.endsWith(".passport")).collect(Collectors.toList());
			assertTrue(leftovers.isEmpty(), "passport files leaked: " + leftovers);
		}
	}
}
