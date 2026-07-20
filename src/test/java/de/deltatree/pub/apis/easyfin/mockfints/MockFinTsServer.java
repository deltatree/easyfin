package de.deltatree.pub.apis.easyfin.mockfints;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Embedded HTTPS endpoint that speaks the FinTS PIN/TAN transport (HTTP POST,
 * Base64-encoded body, ISO-8859-1 payload) so end-to-end tests can drive the
 * real hbci4java client through easyfin's public API without touching a bank.
 *
 * <p>
 * hbci4java's {@code CommPinTan} always connects via HTTPS, so the server is
 * TLS-enabled with a bundled self-signed certificate; tests disable certificate
 * checking via {@code client.passport.PinTan.checkcert=0}.
 * </p>
 *
 * <p>
 * The server records every decoded request so tests can assert what the client
 * actually sent on the wire, and delegates the response to a pluggable
 * responder, which lets each scenario script its own dialog.
 * </p>
 */
public final class MockFinTsServer implements AutoCloseable {

	/** FinTS payloads are ISO-8859-1 on the wire. */
	public static final Charset FINTS_CHARSET = Charset.forName("ISO-8859-1");

	private static final String KEYSTORE_RESOURCE = "/mockfints/mockfints.p12";
	private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

	private final HttpsServer server;
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private volatile Function<String, String> responder;
	private volatile int responseStatus = 200;

	private MockFinTsServer(HttpsServer server) {
		this.server = server;
	}

	/**
	 * Starts a server on an ephemeral port that answers every request with the
	 * given responder.
	 *
	 * @param responder maps the decoded FinTS request to the FinTS response
	 * @return the started server
	 */
	public static MockFinTsServer start(Function<String, String> responder) {
		try {
			HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			https.setHttpsConfigurator(new HttpsConfigurator(sslContext()));

			MockFinTsServer mock = new MockFinTsServer(https);
			mock.responder = responder;

			https.createContext("/", exchange -> {
				byte[] body = readAll(exchange.getRequestBody());
				String decoded = new String(Base64.getMimeDecoder().decode(new String(body, FINTS_CHARSET).trim()),
						FINTS_CHARSET);
				mock.requests.add(decoded);

				String reply = mock.responder.apply(decoded);
				byte[] encoded = Base64.getEncoder().encode(reply.getBytes(FINTS_CHARSET));

				exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
				exchange.sendResponseHeaders(mock.responseStatus, encoded.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(encoded);
				}
			});
			https.setExecutor(null);
			https.start();
			return mock;
		} catch (Exception e) {
			throw new IllegalStateException("could not start mock FinTS server", e);
		}
	}

	private static SSLContext sslContext() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = MockFinTsServer.class.getResourceAsStream(KEYSTORE_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing test keystore " + KEYSTORE_RESOURCE);
			}
			keyStore.load(in, KEYSTORE_PASSWORD);
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, KEYSTORE_PASSWORD);

		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), null, null);
		return ctx;
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		int read;
		while ((read = in.read(chunk)) > 0) {
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

	/** Swaps the responder mid-test (e.g. to fail a later dialog step). */
	public void setResponder(Function<String, String> responder) {
		this.responder = responder;
	}

	/** Makes the endpoint answer with the given HTTP status code. */
	public void setResponseStatus(int responseStatus) {
		this.responseStatus = responseStatus;
	}

	public int getPort() {
		return this.server.getAddress().getPort();
	}

	/**
	 * The address to configure on {@link de.deltatree.pub.apis.easyfin.BankData},
	 * pointing easyfin (and therefore hbci4java) at this server.
	 */
	public String getPinTanAddress() {
		return "https://localhost:" + getPort() + "/fints";
	}

	/** All FinTS requests the client sent, decoded, in order. */
	public List<String> getRequests() {
		return Collections.unmodifiableList(this.requests);
	}

	public String getLastRequest() {
		return this.requests.isEmpty() ? null : this.requests.get(this.requests.size() - 1);
	}

	@Override
	public void close() {
		this.server.stop(0);
	}
}
