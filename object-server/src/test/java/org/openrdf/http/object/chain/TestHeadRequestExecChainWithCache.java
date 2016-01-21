package org.openrdf.http.object.chain;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.cache.HeapResourceFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Path;
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

public class TestHeadRequestExecChainWithCache extends TestCase {
	private static final String RESOURCE = "http://example.org/";

	public static class TestResponse {
		public static int counter;

		@Method("HEAD")
		public HttpResponse head() {
			HttpResponse head = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			head.addHeader("Cache-Control", "no-store");
			return head;
		}

		@Method("GET")
		@Type("text/plain")
		public String get() {
			counter++;
			return "Hello World!";
		}

		@Method("POST")
		public void post(@Type("text/plain") String text) {
			counter++;
			System.out.println(text);
		}

		@Method("GET")
		@Path("etag")
		@Type("text/plain")
		@org.openrdf.annotations.Header("ETag:\"tag\"")
		public String getETag() {
			counter++;
			return "Hello World!";
		}

		@Method("GET")
		@Path("intercept-etag")
		@Type("text/plain")
		public String getInterceptETag() {
			counter++;
			return "Hello World!";
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
		chain = new RequestExecChain(new HeapResourceFactory());
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

	public void testGetETagHelloWorld() throws Exception {
		TestResponse.counter = 0;
		InterceptorTester.callback = new HttpRequestChainInterceptor() {
			
			@Override
			public HttpResponse intercept(HttpRequest request,
					HttpContext context) throws HttpException, IOException {
				String m = request.getRequestLine().getMethod();
				HttpRequest or = ObjectContext.adapt(context)
						.getOriginalRequest();
				assertTrue("GET".equals(m) || or != null
						&& "GET".equals(or.getRequestLine().getMethod()));
				return null;
			}
			
			@Override
			public void process(HttpRequest request, HttpResponse response,
					HttpContext context) throws HttpException, IOException {
				HttpEntity e = response.getEntity();
				Header ct = e == null ? response.getFirstHeader("Content-Type") : e.getContentType();
				assertTrue(String.valueOf(ct).contains("text/plain"));
			}
		};
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "etag",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("\"tag\"", resp.getFirstHeader("ETag").getValue());
		execute(request);
		InterceptorTester.callback = null;
		assertEquals(1, TestResponse.counter);
	}

	public void testGetInterceptETagHelloWorld() throws Exception {
		TestResponse.counter = 0;
		InterceptorTester.callback = new HttpRequestChainInterceptor() {
			
			@Override
			public HttpResponse intercept(HttpRequest request,
					HttpContext context) throws HttpException, IOException {
				String m = request.getRequestLine().getMethod();
				HttpRequest or = ObjectContext.adapt(context)
						.getOriginalRequest();
				assertTrue("GET".equals(m)
						|| "GET".equals(or.getRequestLine().getMethod()));
				return null;
			}
			
			@Override
			public void process(HttpRequest request, HttpResponse response,
					HttpContext context) throws HttpException, IOException {
				response.addHeader("ETag", "\"intercept-etag\"");
			}
		};
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "intercept-etag",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		execute(request);
		InterceptorTester.callback = null;
		assertEquals(1, TestResponse.counter);
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
