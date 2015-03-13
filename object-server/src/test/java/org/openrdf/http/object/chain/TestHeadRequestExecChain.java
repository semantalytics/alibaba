package org.openrdf.http.object.chain;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Type;
import org.openrdf.http.object.exceptions.BadGateway;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;

public class TestHeadRequestExecChain extends TestCase {
	private static final String RESOURCE = "http://example.org/";

	public static class TestResponse {
		@Method("HEAD")
		public HttpResponse head() {
			HttpResponse head = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			head.addHeader("Cache-Control", "no-store");
			return head;
		}

		@Method("GET")
		@Type("text/plain")
		public String get() {
			return "Hello World!";
		}

		@Method("POST")
		public void post(@Type("text/plain") String text) {
			System.out.println(text);
		}
	}

	private ObjectContext context;
	private ObjectRepository repository;
	private RequestExecChain chain;
	static InputStream body;

	public void setUp() throws Exception {
		context = ObjectContext.create();
		context.setProtocolScheme("http");
		SailRepository memory = new SailRepository(new MemoryStore());
		memory.initialize();
		ObjectRepositoryConfig config = new ObjectRepositoryConfig();
		config.addBehaviour(TestResponse.class, "urn:test:test-response");
		repository = new ObjectRepositoryFactory().createRepository(config,
				memory);
		ObjectConnection con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI(RESOURCE), RDF.TYPE,
				vf.createURI("urn:test:test-response"));
		con.close();
		chain = new RequestExecChain();
		chain.addRepository(RESOURCE, repository);
	}

	public void tearDown() throws Exception {
		chain.shutdown();
		repository.shutDown();
		chain.awaitTermination(1, TimeUnit.SECONDS);
	}

	public void testGetHelloWorld() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		Header length = resp.getFirstHeader("Content-Length");
		assertNotNull(length);
		assertEquals(Integer.toString("Hello World!".length()), length.getValue());
	}

	public void testPostHelloWorld() throws Exception {
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Content-Type", "text/plain");
		request.setEntity(new StringEntity("Hello World!"));
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 204, resp
				.getStatusLine().getStatusCode());
	}

	private HttpResponse execute(HttpRequest request)
			throws InterruptedException, ExecutionException {
		return chain.execute(new HttpHost("example.org"), request, context,
				new FutureCallback<HttpResponse>() {
					public void failed(Exception ex) {
						if (ex instanceof RuntimeException) {
							throw (RuntimeException) ex;
						} else {
							throw new BadGateway(ex);
						}
					}

					public void completed(HttpResponse result) {
						// yay!
					}

					public void cancelled() {
						// oops!
					}
				}).get();
	}
}
