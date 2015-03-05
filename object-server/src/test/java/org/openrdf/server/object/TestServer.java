package org.openrdf.server.object;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import junit.framework.TestCase;

import org.apache.http.ssl.SSLContexts;
import org.openrdf.OpenRDFException;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.model.URI;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.server.object.io.ChannelUtil;
import org.openrdf.server.object.io.FileUtil;
import org.openrdf.server.object.management.JVMUsageMXBean;
import org.openrdf.server.object.management.KeyStoreMXBean;
import org.openrdf.server.object.management.ObjectServerMXBean;
import org.openrdf.server.object.management.RepositoryMXBean;
import org.openrdf.store.blob.BlobObject;

public class TestServer extends TestCase {
	private static final String PREFIX = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
			+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
			+ "PREFIX rep: <http://www.openrdf.org/config/repository#>\n"
			+ "PREFIX sr: <http://www.openrdf.org/config/repository/sail#>\n"
			+ "PREFIX sail: <http://www.openrdf.org/config/sail#>\n"
			+ "PREFIX msg: <http://www.openrdf.org/rdf/2011/messaging#>\n";
	private static int MIN_PORT = 49152;
	private static int MAX_PORT = 65535;

	private static int findPort(int seed) {
		int range = (MAX_PORT - MIN_PORT) / 2;
		return (seed % range) + range + MIN_PORT;
	}

	public static abstract class TestResponse implements RDFObject {

		@Method("GET")
		@Type("text/plain")
		public String get() throws OpenRDFException, IOException {
			ObjectConnection con = this.getObjectConnection();
			BlobObject blob = con.getBlobObject((URI) this.getResource());
			return blob.getLength() > 0 ? blob.getCharContent(true).toString() : null;
		}
	}

	private String port;
	private String ssl;
	private Server server;
	private File dataDir;

	public TestServer(String name) {
		super(name);
		Class<? extends TestServer> cls = this.getClass();
		String path = cls.getName().replace('.', '/') + ".class";
		String uri = cls.getClassLoader().getResource(path).toExternalForm();
		this.port = Integer.toString(findPort(uri.hashCode()));
		this.ssl = Integer.toString(findPort((uri + "#ssl").hashCode()));
	}

	public void setUp() throws Exception {
		dataDir = FileUtil.createTempDir("server");
		FileUtil.deleteOnExit(dataDir);
		server = new Server();
	}

	public void tearDown() throws Exception {
		server.destroy();
	}

	public void testMXBean() throws Exception {
		assertNull(getMBean("*:*", ObjectServerMXBean.class));
		server.init("-d", dataDir.getAbsolutePath(), "-p", port);
		ObjectServerMXBean server = getMBean("*:*", ObjectServerMXBean.class);
		assertNotNull(server);
		assertFalse(server.isRunning());
		assertFalse(server.isCompilingInProgress());
		assertFalse(server.isStartingInProgress());
		assertFalse(server.isStoppingInProgress());
		JVMUsageMXBean usage = getMBean("*:*", JVMUsageMXBean.class);
		assertNotNull(usage.getJVMUsage());
		assertTrue(usage.getJVMUsage().length > 3);
	}

	public void testRepositoryMXBean() throws Exception {
		server.init("-d", dataDir.getAbsolutePath(), "-p", port);
		String url = "http://localhost:" + port + "/";
		String PROLOG = "BASE <" + url + ">\n" + PREFIX;
		RepositoryMXBean system = getMBean("*:*,name=SYSTEM", RepositoryMXBean.class);
		system.sparqlUpdate(PROLOG + "INSERT DATA {<TestClass> a owl:Class;\n"
				+ "msg:mixin '" + TestResponse.class.getName() + "'}");
		ObjectServerMXBean objectServer = getMBean("*:*", ObjectServerMXBean.class);
		objectServer.recompileSchema();
		objectServer.addRepository(url, PREFIX
				+ "<#config> a rep:Repository;\n"
				+ "rep:repositoryID 'localhost';\n" + "rdfs:label '" + url
				+ "';\n" + "rep:repositoryImpl [\n"
				+ "rep:repositoryType 'openrdf:SailRepository';\n"
				+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n" + ""
				+ "].\n");
		server.poke(); // update registered repositories
		RepositoryMXBean repository = getMBean("*:*,name=localhost", RepositoryMXBean.class);
		repository.sparqlUpdate(PROLOG + "INSERT DATA {<> a <TestClass>}");
		repository.storeBlob(url, "Hello World!");
		server.start();
		assertHelloWorld("Hello World!", new URL(url).openConnection());
	}

	public void testKeyStoreMXBean() throws Exception {
		char[] password = "changeit".toCharArray();
		File keystore = generateKeyStore("localhost", password);
		FileUtil.deleteOnExit(keystore);
		System.setProperty("javax.net.ssl.keyStore", keystore.getAbsolutePath());
		System.setProperty("javax.net.ssl.keyStorePassword", new String(password));
		server.init("-d", dataDir.getAbsolutePath(), "-p", port, "-s", ssl);
		String url = "https://localhost:" + ssl + "/";
		String PROLOG = "BASE <" + url + ">\n" + PREFIX;
		RepositoryMXBean system = getMBean("*:*,name=SYSTEM", RepositoryMXBean.class);
		system.sparqlUpdate(PROLOG + "INSERT DATA {<TestClass> a owl:Class;\n"
				+ "msg:mixin '" + TestResponse.class.getName() + "'}");
		ObjectServerMXBean objectServer = getMBean("*:*", ObjectServerMXBean.class);
		objectServer.recompileSchema();
		objectServer.addRepository(url, PREFIX
				+ "<#config> a rep:Repository;\n"
				+ "rep:repositoryID 'localhost';\n" + "rdfs:label '" + url
				+ "';\n" + "rep:repositoryImpl [\n"
				+ "rep:repositoryType 'openrdf:SailRepository';\n"
				+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n" + ""
				+ "].\n");
		server.poke(); // update registered repositories
		RepositoryMXBean repository = getMBean("*:*,name=localhost", RepositoryMXBean.class);
		repository.sparqlUpdate(PROLOG + "INSERT DATA {<> a <TestClass>}");
		repository.storeBlob(url, "Hello World!");
		server.start();
		KeyStoreMXBean store = getMBean("*:*", KeyStoreMXBean.class);
		assertFalse(store.isCertificateSigned());
		assertTrue(store.getCertificateExperation() > System.currentTimeMillis());
		String cer = store.exportCertificate();
		File truststore = copyCacerts(password);
		importCertificate(cer, "localhost", truststore, password);
		truststore.deleteOnExit();
		SSLContext sslcontext = SSLContexts.custom()
				.loadTrustMaterial(truststore, password).build();
		HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
		con.setSSLSocketFactory(sslcontext.getSocketFactory());
		con.setHostnameVerifier(createHostnameVerifier());
		assertHelloWorld("Hello World!", con);
		System.clearProperty("javax.net.ssl.keyStore");
		System.clearProperty("javax.net.ssl.keyStorePassword");
	}

	private HostnameVerifier createHostnameVerifier() {
		return new HostnameVerifier() {
			public boolean verify(String s, SSLSession sslSession) {
				return true;
			}
		};
	}

	private <T> T getMBean(String name, Class<T> btype) throws MalformedObjectNameException {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		QueryExp instanceOf = Query.isInstanceOf(Query.value(btype.getName()));
		for (ObjectName on : mbs.queryNames(ObjectName.getInstance(name), instanceOf)) {
			return MBeanServerInvocationHandler.newProxyInstance(mbs, on, btype, false);
		}
		return null;
	}

	private void assertHelloWorld(String expected, URLConnection http)
			throws ProtocolException, IOException {
		HttpURLConnection con = (HttpURLConnection) http;
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "*/*");
		con.setRequestProperty("Accept-Encoding", "gzip");
		assertEquals(con.getResponseMessage(), 200, con.getResponseCode());
		InputStream in = con.getInputStream();
		try {
			if ("gzip".equals(con.getHeaderField("Content-Encoding"))) {
				in = new GZIPInputStream(in);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(in, out);
			assertEquals(expected, new String(out.toByteArray()));
		} finally {
			in.close();
		}
	}

	private File copyCacerts(char[] password) throws IOException,
			InterruptedException {
		File truststore = File.createTempFile("trust", "store");
		File cacerts = new File(new File(new File(System.getProperty("java.home"), "lib"), "security"), "cacerts");
		info.aduna.io.FileUtil.copyFile(cacerts, truststore);
		keytool("-storepasswd",
				"-new", new String(password),
				"-keystore", truststore.getAbsolutePath(),
				"-storepass", "changeit"
		);
		return truststore;
	}

	private void importCertificate(String cer, String alias, File truststore,
			char[] password) throws IOException, InterruptedException {
		File keytool = new File(
				new File(System.getProperty("java.home"), "bin"), "keytool");
		exec(cer.getBytes(), keytool.getAbsolutePath(), "-import",
				"-alias", alias,
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
		return exec(null, keytool.getAbsolutePath(), args);
	}

	private int exec(byte[] stdin, String cmd, String... args) throws IOException,
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
				OutputStream out = exec.getOutputStream();
				if (stdin != null) {
					out.write(stdin);
				}
				out.close();
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
