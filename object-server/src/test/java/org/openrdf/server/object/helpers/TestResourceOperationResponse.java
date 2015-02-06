package org.openrdf.server.object.helpers;

import junit.framework.TestCase;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpResponse;
import org.openrdf.annotations.Method;
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
import org.openrdf.server.object.helpers.ObjectContext;
import org.openrdf.server.object.helpers.ResourceTarget;

public class TestResourceOperationResponse extends TestCase {
	private static final String RESOURCE = "http://example.com/";

	public static class TestResponse {

		@Method("POST")
		@Path("echo")
		public HttpResponse echoResponse(@Type("message/http") HttpResponse message) {
			return message;
		}

		@Method("GET")
		@Path("redirect")
		public HttpResponse redirect() {
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 303, "See Other");
			resp.setHeader("Location", "http://example.org/");
			return resp;
		}
	}

	private ObjectContext context;
	private ObjectRepository repository;
	private ObjectConnection con;

	public void setUp() throws Exception {
		context = ObjectContext.create();
		context.setProtocolScheme("http");
		SailRepository memory = new SailRepository(new MemoryStore());
		memory.initialize();
		ObjectRepositoryConfig config = new ObjectRepositoryConfig();
		config.addBehaviour(TestResponse.class, "urn:test:test-response");
		repository = new ObjectRepositoryFactory().createRepository(config,
				memory);
		con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI(RESOURCE), RDF.TYPE,
				vf.createURI("urn:test:test-response"));
	}

	public void tearDown() throws Exception {
		con.close();
		repository.shutDown();
	}

	public void testEchoResponse() throws Exception {
		ResourceTarget target = getResource();
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
				"POST", RESOURCE + "echo", HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "*/*");
		request.setHeader("Content-Type", "message/http");
		request.setEntity(new StringEntity("HTTP/1.1 202 Accepted\r\n"));
		HttpUriResponse resp = target.invoke(request);
		StatusLine line = resp.getStatusLine();
		assertEquals(line.getReasonPhrase(), 202, line.getStatusCode());
	}

	private ResourceTarget getResource() throws QueryEvaluationException,
			RepositoryException {
		RDFObject object = con.getObjects(RDFObject.class,
				con.getValueFactory().createURI(RESOURCE)).singleResult();
		return new ResourceTarget(object, context);
	}

	public void testRedirect() throws Exception {
		ResourceTarget target = getResource();
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
				"GET", RESOURCE + "redirect", HttpVersion.HTTP_1_1);
		HttpUriResponse resp = target.invoke(request);
		StatusLine line = resp.getStatusLine();
		assertEquals(line.getReasonPhrase(), 303, line.getStatusCode());
		assertEquals("http://example.org/", resp.getFirstHeader("Location").getValue());
	}

}
