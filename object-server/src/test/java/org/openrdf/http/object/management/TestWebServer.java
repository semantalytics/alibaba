package org.openrdf.http.object.management;

import info.aduna.io.FileUtil;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

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
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.http.object.client.HttpClientFactory;
import org.openrdf.http.object.io.ChannelUtil;
import org.openrdf.http.object.management.WebServer;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;

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
		SSLContext.getDefault(); // initialise SSL
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
		File truststore = copyCacerts(password);
		File keystore = generateKeyStore("localhost", password);
		File cer = exportCertificate("localhost", keystore, password);
		importCertificate(cer, "localhost", truststore, password);
		truststore.deleteOnExit();
		keystore.deleteOnExit();
		cer.deleteOnExit();
        final KeyStore identityStore = KeyStore.getInstance(KeyStore.getDefaultType());
        final FileInputStream instream = new FileInputStream(keystore);
        try {
            identityStore.load(instream, password);
        } finally {
            instream.close();
        }
		sslcontext = SSLContexts.custom()
				.loadTrustMaterial(truststore, password)
				.loadKeyMaterial(identityStore, password).build();
		server = new WebServer(sslcontext);
		server.addRepository("http://localhost:" + port + "/", repository);
		server.addRepository("https://localhost:" + ssl + "/", repository);
		server.listen(new int[] { port }, new int[] { ssl });
		server.start();
	}

	public void tearDown() throws Exception {
		server.stop();
		repository.shutDown();
		server.destroy();
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

	private HostnameVerifier createHostnameVerifier() {
		return new HostnameVerifier() {
			public boolean verify(String s, SSLSession sslSession) {
				return true;
			}
		};
	}

	private File copyCacerts(char[] password) throws IOException,
			InterruptedException {
		File truststore = File.createTempFile("trust", "store");
		File cacerts = new File(new File(new File(System.getProperty("java.home"), "lib"), "security"), "cacerts");
		FileUtil.copyFile(cacerts, truststore);
		keytool("-storepasswd",
				"-new", new String(password),
				"-keystore", truststore.getAbsolutePath(),
				"-storepass", "changeit"
		);
		return truststore;
	}

	private File exportCertificate(String alias, File keystore, char[] password)
			throws IOException, InterruptedException {
		File cer = File.createTempFile(alias, ".cer");
		keytool("-export",
				"-alias", alias,
				"-keypass", new String(password),
				"-keystore", keystore.getAbsolutePath(),
				"-storepass", new String(password),
				"-file", cer.getAbsolutePath(),
				"-rfc"
		);
		return cer;
	}

	private void importCertificate(File cer, String alias, File truststore,
			char[] password) throws IOException, InterruptedException {
		keytool("-import",
				"-alias", alias,
				"-file", cer.getAbsolutePath(),
				"-noprompt", "-trustcacerts",
				"-keystore", truststore.getAbsolutePath(),
				"-storepass", new String(password)
		);
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
