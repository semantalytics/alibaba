package org.openrdf.server.object.server.helpers;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import junit.framework.TestCase;

import org.apache.http.HttpVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Header;
import org.openrdf.annotations.Iri;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Param;
import org.openrdf.annotations.Path;
import org.openrdf.annotations.Type;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.openrdf.server.object.client.HttpUriResponse;
import org.openrdf.server.object.server.exceptions.MethodNotAllowed;

public class TestResourceOperation extends TestCase {
	private static final String RESOURCE = "http://example.com/";
	private static final String MARK = "http://example.com/mark/";

	public static class TestEcho {
		@Method("GET")
		@Type("text/plain")
		public String getTextPlain() {
			return "plain text";
		}

		@Method("GET")
		@Type("text/xml;q=0.9")
		public String getXML() {
			return "<xml/>";
		}

		@Method("POST")
		@Type("text/plain")
		public String postTextPlain(@Type({"text/plain;q=1", "*/*"}) String text) {
			return text;
		}

		@Method("POST")
		@Path("text")
		@Type("text/plain")
		public String postText(@Type("text/plain") String text) {
			return text;
		}

		@Method("POST")
		@Path("text")
		@Type("text/plain")
		public String postXML(@Type("text/xml") String xml) {
			return "plain " + xml;
		}

		@Method("POST")
		@Path("data")
		@Type("text/plain")
		public String postFeed(@Type({"application/xml+atom", "application/xml"}) byte[] atom) {
			return "atom";
		}

		@Method("POST")
		@Path("data")
		@Type("text/plain")
		public String postRDF(@Type({"application/xml+rdf", "text/turtle"}) byte[] rdf) {
			return "rdf";
		}

		@Method("GET")
		@Path("cookie")
		@Type("text/plain")
		public String echoCookie(@Header("Cookie") String cookie) {
			return cookie;
		}

		@Method("GET")
		@Path("cookie2")
		@Type("text/plain")
		public String echoCookie2(@Header("Cookie") String[] cookie) {
			return Arrays.asList(cookie).toString();
		}

		@Method("GET")
		@Path("accept")
		@Type("text/plain")
		public String echoAccept(@Header("Accept") @Param("accept") String[] accept) {
			String str = Arrays.toString(accept);
			return str.substring(1, str.length()-1);
		}

		@Method("GET")
		@Path("?param")
		@Type("text/plain")
		public String echoParam(@Param("param") String param) {
			return param;
		}

		@Method("GET")
		@Path("?multi")
		@Type("text/plain")
		public String echoParam(@Param("multi") String[] multi) {
			return Arrays.toString(multi);
		}

		@Method("GET")
		@Path("name")
		@Type("text/plain")
		@Iri("urn:test:test-echo#name")
		public String echoName() {
			return TestEcho.class.getSimpleName();
		}

		@Method("GET")
		@Path("query(?<query>\\?.*)?")
		@Type("text/plain")
		public String echoQueryString(@Param("query") String query) {
			return query;
		}

		@Method("GET")
		@Path("path(?<param>/segment)?")
		@Type("text/plain")
		public String echoPath(@Param("param") String param) {
			return param;
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface SubClassOf {
		@Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
		String value();
	}

	public static class TestMarkup {

		@Method("GET")
		@Path("n")
		@Type("text/plain")
		@Iri("urn:test:test-markup#name")
		@SubClassOf("urn:test:test-echo#name")
		public String markupName() {
			return TestMarkup.class.getSimpleName();
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
		config.addBehaviour(TestMarkup.class, "urn:test:test-markup");
		repository = new ObjectRepositoryFactory().createRepository(config, memory);
		con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI(RESOURCE), RDF.TYPE, vf.createURI("urn:test:test-echo"));
		con.add(vf.createURI(MARK), RDF.TYPE, vf.createURI("urn:test:test-echo"));
		con.add(vf.createURI(MARK), RDF.TYPE, vf.createURI("urn:test:test-markup"));
	}

	public void tearDown() throws Exception {
		con.close();
		repository.shutDown();
	}

	public void testAccept() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		assertEquals("text/plain", target.getAccept("POST", RESOURCE));
	}

	public void testAllowedHeaders() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		assertEquals(Collections.singleton("Cookie"), target.getAllowedHeaders("GET", RESOURCE + "cookie"));
		assertEquals(Collections.singleton("Accept"), target.getAllowedHeaders("GET", RESOURCE + "accept"));
	}

	public void testAllowedMethods() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		assertEquals(new HashSet<String>(Arrays.asList("GET", "HEAD", "POST")), target.getAllowedMethods(RESOURCE));
	}

	public void testInvokeGetText() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("plain text", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeGetXML() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/xml");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("<xml/>", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePreferText() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain;q=0.9, text/xml;q=0.8");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("plain text", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePreferXML() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain;q=0.7, text/xml;q=0.8");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("<xml/>", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeGetAnything() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE, HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/xml, text/plain");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("plain text", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePost() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE, HttpVersion.HTTP_1_1);
		request.setEntity(new StringEntity("text"));
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("text", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeUnknown() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("UNKNOWN", RESOURCE, HttpVersion.HTTP_1_1);
		try {
			target.invoke(request);
			fail();
		} catch (MethodNotAllowed e) {
			// expected
		}
	}

	public void testInvokePostText() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE + "text", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		request.setHeader("Content-Type", "text/plain");
		request.setEntity(new StringEntity("text"));
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("text", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePostXML() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE + "text", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		request.setHeader("Content-Type", "text/xml");
		request.setEntity(new StringEntity("<xml/>"));
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("plain <xml/>", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeHeader() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "cookie", HttpVersion.HTTP_1_1);
		request.setHeader("Cookie", "yum");
		HttpUriResponse resp = target.invoke(request);
		assertEquals("yum", EntityUtils.toString(resp.getEntity()));
		assertTrue(Arrays.toString(resp.getHeaders("Vary")).contains("Cookie"));
	}

	public void testInvokeMultiHeader() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "cookie2", HttpVersion.HTTP_1_1);
		request.addHeader("Cookie", "hum");
		request.addHeader("Cookie", "yum");
		HttpUriResponse resp = target.invoke(request);
		assertEquals("[hum, yum]", EntityUtils.toString(resp.getEntity()));
		assertTrue(Arrays.toString(resp.getHeaders("Vary")).contains("Cookie"));
	}

	public void testInvokeQueryParam() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "?param=value", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("value", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeMultiQueryParam() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "?multi=1&multi=2", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("[1, 2]", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeQueryAndHeader() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "accept?accept=text/xml", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpUriResponse resp = target.invoke(request);
		assertEquals("text/xml, text/plain", EntityUtils.toString(resp.getEntity()));
		assertTrue(Arrays.toString(resp.getHeaders("Vary")).contains("Accept"));
	}

	public void testInvokeQueryString() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "query?q=value", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("?q=value", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeNoQueryString() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "query", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 204, resp.getStatusLine().getStatusCode());
		assertNull(resp.getEntity());
	}

	public void testInvokePathVariable() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "path/segment?param=value", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("/segment", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokeEmptyPathVariable() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "path?param=value", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 204, resp.getStatusLine().getStatusCode());
		assertNull(resp.getEntity());
	}

	public void testParentOfSubMethod() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "name", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals(TestEcho.class.getSimpleName(), EntityUtils.toString(resp.getEntity()));
	}

	public void testSubMethod() throws Exception {
		ResourceOperation target = getResource(MARK);
		BasicHttpRequest request = new BasicHttpRequest("GET", MARK + "name", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals(TestMarkup.class.getSimpleName(), EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePostAtom() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE + "data", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		request.setHeader("Content-Type", "application/xml");
		request.setEntity(new StringEntity("<atom/>"));
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("atom", EntityUtils.toString(resp.getEntity()));
	}

	public void testInvokePostRDF() throws Exception {
		ResourceOperation target = getResource(RESOURCE);
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE + "data", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		request.setHeader("Content-Type", "application/xml+rdf");
		request.setEntity(new StringEntity("<rdf/>"));
		HttpUriResponse resp = target.invoke(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp.getStatusLine().getStatusCode());
		assertEquals("rdf", EntityUtils.toString(resp.getEntity()));
	}

	private ResourceOperation getResource(String iri)
			throws QueryEvaluationException, RepositoryException {
		RDFObject object = con.getObjects(RDFObject.class,
				con.getValueFactory().createURI(iri)).singleResult();
		return new ResourceOperation(object, context);
	}
}
