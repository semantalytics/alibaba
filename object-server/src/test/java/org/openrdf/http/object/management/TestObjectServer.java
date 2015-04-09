package org.openrdf.http.object.management;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import junit.framework.TestCase;

import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.http.object.io.ChannelUtil;
import org.openrdf.http.object.io.DirUtil;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.rio.RDFFormat;

public class TestObjectServer extends TestCase {
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
	private ObjectServer server;
	private File dataDir;

	public TestObjectServer(String name) {
		super(name);
		Class<? extends TestObjectServer> cls = this.getClass();
		String path = cls.getName().replace('.', '/') + ".class";
		String uri = cls.getClassLoader().getResource(path).toExternalForm();
		this.port = Integer.toString(findPort(uri.hashCode()));
		this.base = "http://localhost:" + port + "/";
	}

	public void setUp() throws Exception {
		dataDir = DirUtil.createTempDir("object-server");
		DirUtil.deleteOnExit(dataDir);
		server = new ObjectServer(dataDir);
		server.setPorts(port);
	}

	public void tearDown() throws Exception {
		server.destroy();
	}

	public void testCompile() throws Exception {
		String loc = server.addRepository(dataDir.toURI().toASCIIString(),
				base, PREFIX + "<#config> a rep:Repository;\n"
						+ "rep:repositoryID 'localhost';\n" + "rdfs:label '"
						+ base + "';\n" + "rep:repositoryImpl [\n"
						+ "rep:repositoryType 'openrdf:SailRepository';\n"
						+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n"
						+ "" + "].\n");
		RepositoryConnection sys = server.openSchemaConnection(loc);
		try {
			sys.add(new StringReader(PREFIX + "<TestClass> a owl:Class;\n"
					+ "msg:mixin '" + TestResponse.class.getName() + "'.\n"),
					base, RDFFormat.TURTLE);
		} finally {
			sys.close();
		}
		ObjectConnection local = server.getRepository(loc)
				.getConnection();
		try {
			local.add(new StringReader(PREFIX + "<> a <TestClass>.\n"), base,
					RDFFormat.TURTLE);
		} finally {
			local.close();
		}
		server.init();
		server.start();
		assertHelloWorld(base);
	}

	public void testDynamicResources() throws Exception {
		String loc = server.addRepository(dataDir.toURI().toASCIIString(),
				base, PREFIX + "<#config> a rep:Repository;\n"
						+ "rep:repositoryID 'localhost';\n" + "rdfs:label '"
						+ base + "';\n" + "rep:repositoryImpl [\n"
						+ "rep:repositoryType 'openrdf:SailRepository';\n"
						+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n"
						+ "" + "].\n");
		RepositoryConnection sys = server.openSchemaConnection(loc);
		try {
			sys.add(new StringReader(PREFIX + "<TestClass> a owl:Class;\n"
					+ "msg:mixin '" + TestResponse.class.getName()
					+ "'.\n"), base, RDFFormat.TURTLE);
		} finally {
			sys.close();
		}
		server.init();
		server.start();
		ObjectConnection local = server.getRepository(loc)
				.getConnection();
		try {
			local.add(new StringReader(PREFIX + "<dynamic> a <TestClass>.\n"), base,
					RDFFormat.TURTLE);
		} finally {
			local.close();
		}
		assertHelloWorld(base + "dynamic");
	}

	public void testDynamicRepository() throws Exception {
		RepositoryConnection sys = server.openSchemaConnection(dataDir.toURI().toASCIIString());
		try {
			sys.add(new StringReader(PREFIX + "<TestClass> a owl:Class;\n"
					+ "msg:mixin '" + TestResponse.class.getName()
					+ "'.\n"), base, RDFFormat.TURTLE);
		} finally {
			sys.close();
		}
		server.init();
		server.start();
		String loc = server.addRepository(dataDir.toURI().toASCIIString(),
				base, PREFIX + "<#config> a rep:Repository;\n"
						+ "rep:repositoryID 'localhost';\n" + "rdfs:label '"
						+ base + "';\n" + "rep:repositoryImpl [\n"
						+ "rep:repositoryType 'openrdf:SailRepository';\n"
						+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n"
						+ "" + "].\n");
		ObjectConnection local = server.getRepository(loc)
				.getConnection();
		try {
			local.add(new StringReader(PREFIX + "<dynamic> a <TestClass>.\n"), base,
					RDFFormat.TURTLE);
		} finally {
			local.close();
		}
		assertHelloWorld(base + "dynamic");
	}

	public void testRecompile() throws Exception {
		server.init();
		server.start();
		RepositoryConnection sys = server.openSchemaConnection(dataDir.toURI().toASCIIString());
		try {
			sys.add(new StringReader(PREFIX + "<TestClass> a owl:Class;\n"
					+ "msg:mixin '" + TestResponse.class.getName()
					+ "'.\n"), base, RDFFormat.TURTLE);
		} finally {
			sys.close();
		}
		String loc = server.addRepository(dataDir.toURI().toASCIIString(),
				base, PREFIX + "<#config> a rep:Repository;\n"
						+ "rep:repositoryID 'localhost';\n" + "rdfs:label '"
						+ base + "';\n" + "rep:repositoryImpl [\n"
						+ "rep:repositoryType 'openrdf:SailRepository';\n"
						+ "sr:sailImpl [sail:sailType 'openrdf:NativeStore']\n"
						+ "" + "].\n");
		ObjectConnection local = server.getRepository(loc)
				.getConnection();
		try {
			local.add(new StringReader(PREFIX + "<dynamic> a <TestClass>.\n"), base,
					RDFFormat.TURTLE);
		} finally {
			local.close();
		}
		server.recompileSchema();
		assertHelloWorld(base + "dynamic");
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
