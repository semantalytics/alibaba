package org.openrdf.server.object.server.helpers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLEventReader;

import junit.framework.TestCase;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;
import org.openrdf.OpenRDFException;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Path;
import org.openrdf.annotations.Type;
import org.openrdf.model.Model;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.GraphQueryResultImpl;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.openrdf.server.object.client.HttpUriResponse;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

public class TestResourceOperationResponseBody extends TestCase {
	private static final String RESOURCE = "http://example.com/";

	public static class TestEcho {
		@Method("POST")
		@Type({ "text/boolean", "application/sparql-results+xml",
				"application/sparql-results+json" })
		public boolean echoBoolean(@Type({ "text/boolean",
				"application/sparql-results+xml",
				"application/sparql-results+json" }) boolean bool) {
			return bool;
		}

		@Method("POST")
		@Type("image/*")
		public BufferedImage echoImage(@Type("image/*") BufferedImage image) {
			return image;
		}

		@Method("POST")
		@Path("array")
		@Type("application/octet-stream")
		public byte[] echoBinaryArray(
				@Type("application/octet-stream") byte[] binary) {
			return binary;
		}

		@Method("POST")
		@Path("stream")
		@Type("application/octet-stream")
		public ByteArrayOutputStream echoBinaryArray(
				@Type("application/octet-stream") ByteArrayOutputStream binary) {
			return binary;
		}

		@Method("POST")
		@Type("text/x-dateTime")
		public XMLGregorianCalendar echoDateTime(
				@Type("text/x-dateTime") XMLGregorianCalendar cal) {
			return cal;
		}

		@Method("POST")
		@Path("document-fragment")
		@Type({ "application/xml-external-parsed-entity",
				"text/xml-external-parsed-entity" })
		public DocumentFragment echoDocumentFragment(@Type({
				"application/xml-external-parsed-entity",
				"text/xml-external-parsed-entity" }) DocumentFragment doc) {
			return doc;
		}

		@Method("POST")
		@Path("document")
		@Type({ "application/xml", "text/xml", "image/xml", "text/xsl" })
		public Document echoDocument(@Type({ "application/xml", "text/xml",
				"image/xml", "text/xsl" }) Document doc) {
			return doc;
		}

		@Method("POST")
		@Path("form-map")
		@Type("application/x-www-form-urlencoded")
		public Map<String, String[]> echoFormMap(
				@Type("application/x-www-form-urlencoded") Map<String, String[]> map) {
			return map;
		}

		@Method("POST")
		@Path("form-string")
		@Type("application/x-www-form-urlencoded")
		public String echoFormMap(
				@Type("application/x-www-form-urlencoded") String string) {
			return string;
		}

		@Method("POST")
		@Path("graph-result")
		@Type({ "text/turtle", "application/xml+rdf", "application/ld+json" })
		public GraphQueryResult echoGraph(
				@Type({ "text/turtle", "application/xml+rdf",
						"application/ld+json" }) GraphQueryResult graph)
				throws OpenRDFException {
			try {
				List<Statement> stmts = new ArrayList<Statement>();
				while (graph.hasNext()) {
					stmts.add(graph.next());
				}
				return new GraphQueryResultImpl(graph.getNamespaces(),
						stmts.iterator());
			} finally {
				graph.close();
			}
		}

		@Method("POST")
		@Path("entity")
		@Type("text/*")
		public HttpEntity echoEntity(@Type("text/*") HttpEntity entity) {
			return entity;
		}

		@Method("POST")
		@Type("message/http")
		public HttpMessage echoMessage(@Type("message/http") HttpMessage message) {
			return message;
		}

		@Method("POST")
		@Path("input-stream")
		@Type("application/octet-stream")
		public InputStream echoInputStream(
				@Type("application/octet-stream") InputStream stream)
				throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int read;
			byte[] buf = new byte[1024];
			while ((read = stream.read(buf)) >= 0) {
				baos.write(buf, 0, read);
			}
			return new ByteArrayInputStream(baos.toByteArray());
		}

		@Method("POST")
		@Path("model")
		@Type({ "text/turtle", "application/xml+rdf", "application/ld+json" })
		public Model echoModel(@Type({ "text/turtle", "application/xml+rdf",
				"application/ld+json" }) Model model) {
			return model;
		}

		@Method("POST")
		@Type("text/x-integer")
		public Integer echoInteger(@Type("text/x-integer") Integer integer) {
			return integer;
		}

		@Method("POST")
		@Path("resource")
		@Type("text/uri-list")
		public Object echoResource(@Type("text/uri-list") Object resource) {
			assert resource instanceof RDFObject;
			return resource;
		}

		@Method("POST")
		@Path("resources")
		@Type("text/uri-list")
		public Object[] echoResource(@Type("text/uri-list") Object[] resource) {
			return resource;
		}

		@Method("POST")
		@Path("url")
		@Type("text/uri-list")
		public URL echoURL(@Type("text/uri-list") URL url) {
			return url;
		}

		@Method("POST")
		@Path("urls")
		@Type("text/uri-list")
		public URL[] echoURL(@Type("text/uri-list") URL[] url) {
			return url;
		}

		@Method("POST")
		@Path("net.uri")
		@Type("text/uri-list")
		public java.net.URI echoURI(@Type("text/uri-list") java.net.URI uri) {
			return uri;
		}

		@Method("POST")
		@Path("net.uris")
		@Type("text/uri-list")
		public java.net.URI[] echoURI(@Type("text/uri-list") java.net.URI[] uri) {
			return uri;
		}

		@Method("POST")
		@Path("uri")
		@Type("text/uri-list")
		public URI echoURI(@Type("text/uri-list") URI uri) {
			return uri;
		}

		@Method("POST")
		@Path("uris")
		@Type("text/uri-list")
		public URI[] echoURI(@Type("text/uri-list") URI[] uri) {
			return uri;
		}

		@Method("POST")
		@Path("readable")
		@Type("text/plain")
		public Readable echoReadable(@Type("text/plain") Readable readable)
				throws IOException {
			StringWriter writer = new StringWriter();
			CharBuffer cb = CharBuffer.allocate(1024);
			while (readable.read(cb) >= 0) {
				cb.flip();
				writer.write(cb.array(), 0, cb.limit());
				cb.clear();
			}
			return new StringReader(writer.toString());
		}

		@Method("POST")
		@Path("channel")
		@Type("application/octet-stream")
		public ReadableByteChannel echoChannel(
				@Type("application/octet-stream") ReadableByteChannel channel)
				throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ByteBuffer buf = ByteBuffer.allocate(1024);
			while (channel.read(buf) >= 0) {
				buf.flip();
				baos.write(buf.array(), 0, buf.limit());
				buf.clear();
			}
			return Channels.newChannel(new ByteArrayInputStream(baos
					.toByteArray()));
		}

		@Method("POST")
		@Type({ "text/csv", "text/tab-separated-values",
				"application/sparql-results+xml",
				"application/sparql-results+json" })
		public TupleQueryResult echoTuple(@Type({ "text/csv",
				"text/tab-separated-values", "application/sparql-results+xml",
				"application/sparql-results+json" }) TupleQueryResult result) {
			return result;
		}

		@Method("POST")
		@Path("xml-event")
		@Type({ "application/xml", "text/xml", "image/xml", "text/xsl",
				"application/xml-external-parsed-entity",
				"text/xml-external-parsed-entity" })
		public XMLEventReader echoDocument(@Type({ "application/xml",
				"text/xml", "image/xml", "text/xsl",
				"application/xml-external-parsed-entity",
				"text/xml-external-parsed-entity" }) XMLEventReader reader) {
			return reader;
		}
	}

	private CalliContext context;
	private ObjectRepository repository;
	private ObjectConnection con;

	public void setUp() throws Exception {
		context = CalliContext.create();
		context.setProtocolScheme("http");
		SailRepository memory = new SailRepository(new MemoryStore());
		memory.initialize();
		ObjectRepositoryConfig config = new ObjectRepositoryConfig();
		config.addBehaviour(TestEcho.class, "urn:test:test-echo");
		repository = new ObjectRepositoryFactory().createRepository(config,
				memory);
		con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI(RESOURCE), RDF.TYPE,
				vf.createURI("urn:test:test-echo"));
	}

	public void tearDown() throws Exception {
		con.close();
		repository.shutDown();
	}

	public void testBoolean() throws Exception {
		assertPostRoundTrip(RESOURCE, "text/boolean", "true");
		assertPostRoundTrip(RESOURCE, "application/sparql-results+json", "{\n"
				+ "  \"head\" : { },\n" + "  \"boolean\" : true\n" + "}");
		assertPostRoundTrip(
				RESOURCE,
				"application/sparql-results+xml",
				"<?xml version='1.0' encoding='UTF-8'?>\n"
						+ "<sparql xmlns='http://www.w3.org/2005/sparql-results#'>\n"
						+ "	<head>\n" + "	</head>\n"
						+ "	<boolean>true</boolean>\n" + "</sparql>\n");
	}

	public void testBufferedImage() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		BufferedImage bi = new BufferedImage(100, 100,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = bi.createGraphics();
		g.setColor(Color.BLACK);
		g.drawString("image", 50, 50);
		g.dispose();
		ImageIO.write(bi, "png", baos);
		assertPostRoundTrip(RESOURCE, "image/png", baos.toByteArray());
		baos.reset();
		ImageIO.write(bi, "jpeg", baos);
		assertPostRoundTrip(RESOURCE, "image/jpeg", baos.toByteArray());
	}

	public void testBinaryArray() throws Exception {
		assertPostRoundTrip(RESOURCE + "array", "application/octet-stream",
				"binary");
	}

	public void testBinaryStream() throws Exception {
		assertPostRoundTrip(RESOURCE + "stream", "application/octet-stream",
				"binary");
	}

	public void testDateTime() throws Exception {
		assertPostRoundTrip(RESOURCE, "text/x-dateTime", "2015-01-01T00:00:00Z");
	}

	public void testDocumentFragment() throws Exception {
		assertPostRoundTrip(RESOURCE + "document-fragment",
				"application/xml-external-parsed-entity", "<xml/>");
	}

	public void testDocument() throws Exception {
		assertPostRoundTrip(RESOURCE + "document", "application/xml",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><xml/>");
	}

	public void testFormMap() throws Exception {
		assertPostRoundTrip(RESOURCE + "form-map",
				"application/x-www-form-urlencoded",
				"param=value1&param=value2");
	}

	public void testFormString() throws Exception {
		assertPostRoundTrip(RESOURCE + "form-string",
				"application/x-www-form-urlencoded",
				"param=value1&param=value2");
	}

	public void testGraph() throws Exception {
		assertPostRoundTrip(RESOURCE + "graph-result", "text/turtle",
				"\n<> a <> .\n");
	}

	public void testEntity() throws Exception {
		assertPostRoundTrip(RESOURCE + "entity", "text/plain", "text");
	}

	public void testMessage() throws Exception {
		assertPostRoundTrip(RESOURCE, "message/http", "GET /index.html HTTP/1.1\r\nHost: www.example.com\r\n\r\n");
		assertPostRoundTrip(RESOURCE, "message/http", "HTTP/1.1 204 No Content\r\nDate: Mon, 23 May 2005 22:38:34 GMT\r\n\r\n");
	}

	public void testInputStream() throws Exception {
		assertPostRoundTrip(RESOURCE + "input-stream",
				"application/octet-stream", "binary");
	}

	public void testModel() throws Exception {
		assertPostRoundTrip(RESOURCE + "model", "text/turtle", "\n<> a <> .\n");
	}

	public void testInteger() throws Exception {
		assertPostRoundTrip(RESOURCE, "text/x-integer", "100");
	}

	public void testResource() throws Exception {
		assertPostRoundTrip(RESOURCE + "resource", "text/uri-list", RESOURCE);
	}

	public void testResources() throws Exception {
		assertPostRoundTrip(RESOURCE + "resources", "text/uri-list", RESOURCE);
	}

	public void testURL() throws Exception {
		assertPostRoundTrip(RESOURCE + "url", "text/uri-list", RESOURCE);
	}

	public void testURLs() throws Exception {
		assertPostRoundTrip(RESOURCE + "urls", "text/uri-list", RESOURCE);
	}

	public void testNetURI() throws Exception {
		assertPostRoundTrip(RESOURCE + "net.uri", "text/uri-list", RESOURCE);
	}

	public void testNetURIs() throws Exception {
		assertPostRoundTrip(RESOURCE + "net.uris", "text/uri-list", RESOURCE);
	}

	public void testURI() throws Exception {
		assertPostRoundTrip(RESOURCE + "uri", "text/uri-list", RESOURCE);
	}

	public void testURIs() throws Exception {
		assertPostRoundTrip(RESOURCE + "uris", "text/uri-list", RESOURCE);
	}

	public void testReadable() throws Exception {
		assertPostRoundTrip(RESOURCE + "readable", "text/plain", "text");
	}

	public void testChannel() throws Exception {
		assertPostRoundTrip(RESOURCE + "channel", "application/octet-stream",
				"binary");
	}

	public void testTuple() throws Exception {
		assertPostRoundTrip(RESOURCE, "text/csv", "name\r\nvalue\r\n");
		assertPostRoundTrip(RESOURCE, "text/tab-separated-values",
				"?name\n\"value\"\n");
	}

	public void testXMLEvent() throws Exception {
		assertPostRoundTrip(RESOURCE + "xml-event", "application/xml",
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml></xml>");
	}

	private void assertPostRoundTrip(String url, String type, byte[] data)
			throws OpenRDFException, IOException, HttpException {
		ResourceOperation target = getResource();
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
				"POST", url, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", type);
		request.setHeader("Content-Type", type);
		request.setEntity(new ByteArrayEntity(data));
		HttpUriResponse resp = target.invoke(request);
		StatusLine line = resp.getStatusLine();
		assertEquals(line.getReasonPhrase(), 200, line.getStatusCode());
		byte[] echo = EntityUtils.toByteArray(resp.getEntity());
		assertEquals(data.length, echo.length);
		assertTrue(Arrays.equals(data, echo));
	}

	private void assertPostRoundTrip(String url, String type, String data)
			throws OpenRDFException, IOException, HttpException {
		ResourceOperation target = getResource();
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
				"POST", url, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", type);
		request.setHeader("Content-Type", type);
		request.setEntity(new StringEntity(data));
		HttpUriResponse resp = target.invoke(request);
		StatusLine line = resp.getStatusLine();
		assertEquals(line.getReasonPhrase(), 200, line.getStatusCode());
		String echo = EntityUtils.toString(resp.getEntity());
		assertEquals(data, echo);
	}

	private ResourceOperation getResource() throws QueryEvaluationException,
			RepositoryException {
		RDFObject object = con.getObjects(RDFObject.class,
				con.getValueFactory().createURI(RESOURCE)).singleResult();
		return new ResourceOperation(object, context);
	}
}
