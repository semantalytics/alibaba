package org.openrdf.http.object;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.net.ssl.SSLContext;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.openrdf.OpenRDFException;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.http.object.io.FileUtil;
import org.openrdf.http.object.management.ObjectServerMXBean;
import org.openrdf.model.URI;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.store.blob.BlobObject;

public class TestServerControl extends TestCase {
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
	private File dataDir;
	private ServerControl control;

	public TestServerControl(String name) throws Exception {
		super(name);
		SSLContext.getDefault();
		Class<? extends TestServerControl> cls = this.getClass();
		String path = cls.getName().replace('.', '/') + ".class";
		String uri = cls.getClassLoader().getResource(path).toExternalForm();
		this.port = Integer.toString(findPort(uri.hashCode()));
		this.ssl = Integer.toString(findPort((uri + "#ssl").hashCode()));
	}

	public void setUp() throws Exception {
		dataDir = FileUtil.createTempDir("server");
		FileUtil.deleteOnExit(dataDir);
		control = new ServerControl();
	}

	public void tearDown() throws Exception {
		control.destroy();
	}

	public void testServerName() throws Exception {
		control.init("-d", dataDir.getAbsolutePath(), "-n", "test name");
		control.start();
		assertEquals("test name", getMBean("*:*", ObjectServerMXBean.class)
				.getServerName());
	}

	public void testPorts() throws Exception {
		control.init("-d", dataDir.getAbsolutePath(), "-p", port, "-s", ssl);
		control.start();
		assertEquals(port, getMBean("*:*", ObjectServerMXBean.class).getPorts());
		assertEquals(ssl, getMBean("*:*", ObjectServerMXBean.class)
				.getSSLPorts());
	}

	public void testStop() throws Exception {
		Server server = new Server();
		server.init("-d", dataDir.getAbsolutePath(), "--trust");
		assertNotNull(getMBean("*:*", ObjectServerMXBean.class));
		control.init("-p", port, "-status", "-stop");
		control.start();
		server.poke();
		assertNull(getMBean("*:*", ObjectServerMXBean.class));
	}

	public void testListRepositories() throws Exception {
		control.init("-d", dataDir.getAbsolutePath(), "-p", port, "-add", "http://example.com/sparql", "-x",
				"http://example.com/", "-list");
		control.start();
	}

	public void testUpdateQuery() throws Exception {
		String url = "http://localhost:" + port + "/";
		String PROLOG = "BASE <" + url + ">\n" + PREFIX;
		Server server = new Server();
		server.init("-d", dataDir.getAbsolutePath(), "--trust");
		ObjectServerMXBean objectServer = getMBean("*:*",
				ObjectServerMXBean.class);
		objectServer.addRepository(url, PREFIX
				+ "<#config> a rep:Repository;\n"
				+ "rep:repositoryID 'localhost';\n" + "rdfs:label '" + url
				+ "';\n" + "rep:repositoryImpl [\n"
				+ "rep:repositoryType 'openrdf:SailRepository';\n"
				+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n" + ""
				+ "].\n");
		server.poke(); // update registered repositories
		control.init("-p", port, "-id", "localhost", "-update", PROLOG
				+ "INSERT DATA {<> a <TestClass>}", "-query", PROLOG
				+ "DESCRIBE <>");
		control.start();
	}

	public void testReadWrite() throws Exception {
		String url = "http://localhost:" + port + "/";
		Server server = new Server();
		server.init("-d", dataDir.getAbsolutePath(), "--trust");
		ObjectServerMXBean objectServer = getMBean("*:*",
				ObjectServerMXBean.class);
		objectServer.addRepository(url, PREFIX
				+ "<#config> a rep:Repository;\n"
				+ "rep:repositoryID 'localhost';\n" + "rdfs:label '" + url
				+ "';\n" + "rep:repositoryImpl [\n"
				+ "rep:repositoryType 'openrdf:SailRepository';\n"
				+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n" + ""
				+ "].\n");
		server.poke(); // update registered repositories
		File read = File.createTempFile("read", "", dataDir);
		File write = File.createTempFile("read", "", dataDir);
		FileUtils.writeStringToFile(write, "Hello World!");
		control.init("-p", port, "-id", "localhost", "-write", url, "-file", write.getAbsolutePath());
		control.start();
		control.init("-p", port, "-id", "localhost", "-read", url, "-file", read.getAbsolutePath());
		control.start();
		assertEquals("Hello World!", FileUtils.readFileToString(read));
	}

	private <T> T getMBean(String name, Class<T> btype) throws MalformedObjectNameException {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		QueryExp instanceOf = Query.isInstanceOf(Query.value(btype.getName()));
		for (ObjectName on : mbs.queryNames(ObjectName.getInstance(name), instanceOf)) {
			return MBeanServerInvocationHandler.newProxyInstance(mbs, on, btype, false);
		}
		return null;
	}
}
