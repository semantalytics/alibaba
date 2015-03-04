package org.openrdf.server.object;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

import junit.framework.TestCase;

import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.event.NotifyingRepositoryConnection;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.repository.manager.RepositoryProvider;
import org.openrdf.rio.RDFFormat;
import org.openrdf.server.object.io.ChannelUtil;
import org.openrdf.server.object.io.FileUtil;
import org.openrdf.server.object.management.ObjectServerMXBean;

public class TestServer extends TestCase {
	private static final String PREFIX = "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>.\n"
			+ "@prefix owl: <http://www.w3.org/2002/07/owl#>.\n"
			+ "@prefix rep: <http://www.openrdf.org/config/repository#>.\n"
			+ "@prefix sr: <http://www.openrdf.org/config/repository/sail#>.\n"
			+ "@prefix sail: <http://www.openrdf.org/config/sail#>.\n"
			+ "@prefix msg: <http://www.openrdf.org/rdf/2011/messaging#>.\n";
	private static int MIN_PORT = 49152;
	private static int MAX_PORT = 65535;

	private static int findPort(int seed) {
		int range = (MAX_PORT - MIN_PORT) / 2;
		return (seed % range) + range + MIN_PORT;
	}

	public static class TestResponse {

		@Method("GET")
		@Type("text/plain")
		public String get() {
			return "Hello World!";
		}
	}

	private String port;
	private String base;
	private Server server;
	private File dataDir;

	public TestServer(String name) {
		super(name);
		Class<? extends TestServer> cls = this.getClass();
		String path = cls.getName().replace('.', '/') + ".class";
		String uri = cls.getClassLoader().getResource(path).toExternalForm();
		this.port = Integer.toString(findPort(uri.hashCode()));
		this.base = "http://localhost:" + port + "/";
	}

	public void setUp() throws Exception {
		dataDir = FileUtil.createTempDir("server");
		FileUtil.deleteOnExit(dataDir);
		server = new Server();
	}

	public void tearDown() throws Exception {
		server.destroy();
	}

	public void testObjectServerMXBean() throws Exception {
		assertNull(getMBean(ObjectServerMXBean.class));
		server.init("-d", dataDir.getAbsolutePath(), "-p", port);
		ObjectServerMXBean server = getMBean(ObjectServerMXBean.class);
		assertNotNull(server);
		assertFalse(server.isRunning());
		assertFalse(server.isCompilingInProgress());
		assertFalse(server.isStartingInProgress());
		assertFalse(server.isStoppingInProgress());
	}

	public void testRepositoryManager() throws Exception {
		LocalRepositoryManager manager = RepositoryProvider.getRepositoryManager(dataDir);
		NotifyingRepositoryConnection sys = manager.getSystemRepository()
				.getConnection();
		try {
			sys.add(new StringReader(PREFIX + "<TestClass> a owl:Class;\n"
					+ "msg:mixin '" + TestResponse.class.getName()
					+ "'.\n"), base, RDFFormat.TURTLE);
		} finally {
			sys.close();
		}
		server.init("-d", dataDir.getAbsolutePath(), "-p", port);
		ObjectServerMXBean server = getMBean(ObjectServerMXBean.class);
		server.addRepository(base, PREFIX + "<#config> a rep:Repository;\n"
				+ "rep:repositoryID 'localhost';\n" + "rdfs:label '" + base
				+ "';\n" + "rep:repositoryImpl [\n"
				+ "rep:repositoryType 'openrdf:SailRepository';\n"
				+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n" + ""
				+ "].\n");
		RepositoryConnection local = manager.getRepository("localhost")
				.getConnection();
		try {
			local.add(new StringReader(PREFIX + "<> a <TestClass>.\n"), base,
					RDFFormat.TURTLE);
		} finally {
			local.close();
		}
		server.start();
		assertHelloWorld(base);
	}

	private <T> T getMBean(Class<T> btype) throws MalformedObjectNameException {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		QueryExp instanceOf = Query.isInstanceOf(Query.value(btype.getName()));
		for (ObjectName on : mbs.queryNames(ObjectName.WILDCARD, instanceOf)) {
			return MBeanServerInvocationHandler.newProxyInstance(mbs, on, btype, false);
		}
		return null;
	}

	private void assertHelloWorld(String url) throws IOException,
			MalformedURLException, ProtocolException {
		HttpURLConnection con = (HttpURLConnection) new URL(url)
				.openConnection();
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
			assertEquals("Hello World!", new String(out.toByteArray()));
		} finally {
			in.close();
		}
	}
}
