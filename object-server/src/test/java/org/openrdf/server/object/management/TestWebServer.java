package org.openrdf.server.object.management;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import junit.framework.TestCase;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.openrdf.server.object.client.HttpClientFactory;
import org.openrdf.server.object.io.ChannelUtil;

public class TestWebServer extends TestCase {
	private static int MIN_PORT = 49152;
	private static int MAX_PORT = 65535;

	private static int findPort(int seed) {
		int range = (MAX_PORT - MIN_PORT) / 2;
		return (seed % range) + range + MIN_PORT;
	}

	public static class TestResponse {
		public static CountDownLatch latch = new CountDownLatch(0);

		@Method("GET")
		@Type("text/plain")
		public String get() {
			return "Hello World!";
		}

		@Method("POST")
		@Type("text/plain")
		public String post() throws InterruptedException {
			latch.await();
			return "Hello World!";
		}
	}

	private ObjectRepository repository;
	private int port;
	private int ssl;
	private WebServer server;
	private SSLContext sslcontext;

	public TestWebServer(String name) throws Exception {
		super(name);
		Class<? extends TestWebServer> cls = this.getClass();
		String path = cls.getName().replace('.', '/') + ".class";
		String uri = cls.getClassLoader().getResource(path).toExternalForm();
		this.port = findPort(uri.hashCode());
		this.ssl = this.port + 1;
	}

	public void setUp() throws Exception {
		SailRepository memory = new SailRepository(new MemoryStore());
		memory.initialize();
		ObjectRepositoryConfig config = new ObjectRepositoryConfig();
		config.addBehaviour(TestResponse.class, "urn:test:test-response");
		repository = new ObjectRepositoryFactory().createRepository(config,
				memory);
		ObjectConnection con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI("http://localhost:" + port + "/"), RDF.TYPE,
				vf.createURI("urn:test:test-response"));
		con.add(vf.createURI("https://localhost:" + ssl + "/"), RDF.TYPE,
				vf.createURI("urn:test:test-response"));
		con.close();
		char[] password = "changeit".toCharArray();
		File keystore = generateKeyStore("localhost", password);
		keystore.deleteOnExit();
		System.setProperty("javax.net.ssl.keyStorePassword", new String(password));
		System.setProperty("javax.net.ssl.keyStore", keystore.getAbsolutePath());
		sslcontext = createSSLContext();
		server = new WebServer();
		server.addRepository("http://localhost:" + port + "/", repository);
		server.addRepository("https://localhost:" + ssl + "/", repository);
		server.listen(new int[] { port }, new int[] { ssl });
		server.start();
	}

	public void tearDown() throws Exception {
		server.stop();
		repository.shutDown();
		server.destroy();
		System.clearProperty("javax.net.ssl.keyStorePassword");
		System.clearProperty("javax.net.ssl.keyStore");
	}

	public void testClientExecNoOrigins() throws Exception {
		WebServer server = new WebServer();
		HttpRoute route = new HttpRoute(new HttpHost("example.com", 80));
		BasicHttpRequest req = new BasicHttpRequest("GET", "/");
		req.setHeader("Host", "example.com");
		HttpRequestWrapper request = HttpRequestWrapper.wrap(req);
		HttpClientContext context = new HttpClientContext();
		HttpExecutionAware execAware = new HttpExecutionAware() {
			public boolean isAborted() {
				return false;
			}

			public void setCancellable(Cancellable cancellable) {
			}
		};
		CloseableHttpResponse resp = server.execute(route, request, context,
				execAware);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 503, resp
				.getStatusLine().getStatusCode());
	}

	public void testClientExec() throws Exception {
		WebServer server = new WebServer();
		server.addRepository("http://localhost:" + port + "/", repository);
		HttpRoute route = new HttpRoute(new HttpHost("localhost", port));
		BasicHttpRequest req = new BasicHttpRequest("GET", "/");
		req.setHeader("Host", "localhost:" + port);
		HttpRequestWrapper request = HttpRequestWrapper.wrap(req);
		HttpClientContext context = new HttpClientContext();
		HttpExecutionAware execAware = new HttpExecutionAware() {
			public boolean isAborted() {
				return false;
			}

			public void setCancellable(Cancellable cancellable) {
			}
		};
		CloseableHttpResponse resp = server.execute(route, request, context,
				execAware);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
	}

	public void testServerNameClient() throws Exception {
		server.setName("My Name");
		HttpClientFactory factory = HttpClientFactory.getInstance();
		CloseableHttpClient client = factory.createHttpClient("http://localhost");
		String url = "http://localhost:" + port + "/";
		BasicHttpRequest req = new BasicHttpRequest("GET", url, HttpVersion.HTTP_1_1);
		CloseableHttpResponse resp = client.execute(new HttpHost("localhost", port), req);
		assertEquals("My Name", resp.getFirstHeader("Server").getValue());
		EntityUtils.consume(resp.getEntity());
	}

	public void testOpenConnections() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		TestResponse.latch = new CountDownLatch(1);
		String url = "http://localhost:" + port + "/";
		final HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "*/*");
		con.setRequestProperty("Accept-Encoding", "gzip");
		new Thread(new Runnable() {
			public void run() {
				try {
					con.getResponseCode();
					latch.countDown();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
		latch.await();
		assertEquals(1, server.getOpenConnections().length);
		TestResponse.latch.countDown();
		assertEquals(con.getResponseMessage(), 200, con.getResponseCode());
		InputStream in = con.getInputStream();
		try {
			if ("gzip".equals(con.getHeaderField("Content-Encoding"))) {
				in = new GZIPInputStream(in);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(in, out);
			assertEquals("Hello World!", new String(out.toByteArray()));
		} finally {
			in.close();
		}
	}

	public void testServerNameTCP() throws Exception {
		server.setName("My Name");
		String url = "http://localhost:" + port + "/";
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "*/*");
		con.setRequestProperty("Accept-Encoding", "gzip");
		assertEquals(con.getResponseMessage(), 200, con.getResponseCode());
		assertEquals("My Name", con.getHeaderField("Server"));
		InputStream in = con.getInputStream();
		try {
			if ("gzip".equals(con.getHeaderField("Content-Encoding"))) {
				in = new GZIPInputStream(in);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(in, out);
			assertEquals("Hello World!", new String(out.toByteArray()));
		} finally {
			in.close();
		}
	}

	public void testServerNameSSL() throws Exception {
		server.setName("My Name");
		String url = "https://localhost:" + ssl + "/";
		HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
		con.setSSLSocketFactory(sslcontext.getSocketFactory());
		con.setHostnameVerifier(createHostnameVerifier());
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "*/*");
		con.setRequestProperty("Accept-Encoding", "gzip");
		assertEquals(con.getResponseMessage(), 200, con.getResponseCode());
		assertEquals("My Name", con.getHeaderField("Server"));
		InputStream in = con.getInputStream();
		try {
			if ("gzip".equals(con.getHeaderField("Content-Encoding"))) {
				in = new GZIPInputStream(in);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(in, out);
			assertEquals("Hello World!", new String(out.toByteArray()));
		} finally {
			in.close();
		}
	}

	private SSLContext createSSLContext() throws NoSuchAlgorithmException,
			KeyManagementException {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}
	
			public void checkClientTrusted(X509Certificate[] certs,
					String authType) {
			}
	
			public void checkServerTrusted(X509Certificate[] certs,
					String authType) {
			}
		} };
		final SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		return sc;
	}

	private HostnameVerifier createHostnameVerifier() {
		return new HostnameVerifier() {
			public boolean verify(String s, SSLSession sslSession) {
				return true;
			}
		};
	}

	private File generateKeyStore(String alias, char[] password)
			throws IOException, InterruptedException {
		File keystore = File.createTempFile("keystore", "");
		keystore.delete();
		keytool("-genkey",
				"-alias", alias,
				"-keyalg", "RSA",
				"-keysize", "2048",
				"-dname", "CN=unknown, OU=unknown, O=unknown, L=unknown, ST=unknown, C=unknown",
				"-keypass", new String(password),
				"-validity", "1",
				"-keystore", keystore.getAbsolutePath(),
				"-storepass", new String(password)
		);
		return keystore;
	}

	private int keytool(String... args) throws IOException, InterruptedException {
		File keytool = new File(
				new File(System.getProperty("java.home"), "bin"), "keytool");
		return exec(keytool.getAbsolutePath(), args);
	}

	private int exec(String cmd, String[] args) throws IOException,
			InterruptedException {
		String[] cmdArray = new String[1 + args.length];
		cmdArray[0] = cmd;
		System.arraycopy(args, 0, cmdArray, 1, args.length);
		File wd = File.createTempFile("javac", "dir");
		wd.delete();
		wd = wd.getParentFile();
		final Process exec = Runtime.getRuntime().exec(cmdArray, null, wd);
		try {
			Thread gobbler = new Thread() {
				@Override
				public void run() {
					try {
						InputStream in = exec.getInputStream();
						try {
							InputStreamReader isr = new InputStreamReader(in);
							BufferedReader br = new BufferedReader(isr);
							String line = null;
							while ((line = br.readLine()) != null) {
								System.out.println(line);
							}
						} finally {
							in.close();
						}
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}
			};
			gobbler.start();
			InputStream stderr = exec.getErrorStream();
			try {
				exec.getOutputStream().close();
				InputStreamReader isr = new InputStreamReader(stderr);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					System.err.println(line);
				}
			} finally {
				stderr.close();
			}
			return exec.waitFor();
		} finally {
			exec.destroy();
		}
	}
}
