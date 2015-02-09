package org.openrdf.server.object.chain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import junit.framework.TestCase;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.openrdf.annotations.Method;
import org.openrdf.annotations.Param;
import org.openrdf.annotations.Path;
import org.openrdf.annotations.Type;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.openrdf.server.object.chain.RequestExecChain;
import org.openrdf.server.object.exceptions.BadGateway;
import org.openrdf.server.object.exceptions.BadRequest;
import org.openrdf.server.object.helpers.ObjectContext;
import org.openrdf.server.object.io.ChannelUtil;

public class TestRequestExecChain extends TestCase {
	private static final String RESOURCE = "http://example.org/";
	private static final String SECURE = "https://example.org/";
	private static final String MATCH = RESOURCE + "match";
	private static final String SINCE = RESOURCE + "since";
	private static final String VERSION = RESOURCE + "version";

	public static class TestResponse {

		@Method("GET")
		@Type("text/plain")
		public String get() {
			return "Hello World!";
		}

		@Method("POST")
		public HttpResponse post() {
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			resp.setEntity(new InputStreamEntity(body));
			return resp;
		}

		@Method("POST")
		@Path("?json")
		@Type("text/json")
		public String echoJSON(@Type("text/json") String json) {
			return json;
		}

		@Method("GET")
		@Type("application/xml")
		public InputStream getXML() {
			return null;
		}

		@Method("GET")
		@Type("text/json")
		public String getJSON() {
			return Arrays.toString((int[]) Array.newInstance(Integer.TYPE, 1024));
		}

		@Method("DELETE")
		public void delete() {
			throw new BadRequest();
		}

		@Method("GET")
		@Path("path[^\\?]*")
		@Type("text/plain")
		public String getPath(@Param("0") String path) {
			return path;
		}
	}

	public static class TestETagResponse {
		public static int counter;

		@Method("HEAD")
		public HttpResponse head() {
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			resp.setHeader("ETag", "\"hello\"");
			return resp;
		}

		@Method("GET")
		@Type("text/plain")
		public String get() {
			counter++;
			return "Hello World!";
		}

		@Method("POST")
		@Type("text/plain")
		public String post() {
			return "Hello World!";
		}
	}

	public static class TestLastModifiedResponse {
		public static Date modified = new Date();
		public static int counter;

		@Method("HEAD")
		public HttpResponse head() {
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			resp.setHeader("Last-Modified", DateUtils.formatDate(modified));
			return resp;
		}

		@Method("GET")
		@Type("text/plain")
		public String get() {
			counter++;
			return "Hello World!";
		}

		@Method("POST")
		@Type("text/plain")
		public String post() {
			return "Hello World!";
		}
	}

	public static class TestVersionResponse {
		public static String version = "A";

		@Method("HEAD")
		public HttpResponse head() {
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			resp.setHeader("Content-Version", version);
			return resp;
		}

		@Method("GET")
		@Type("text/plain")
		public String get() {
			return "Hello World!";
		}

		@Method("POST")
		public HttpResponse post() throws UnsupportedEncodingException {
			version = Character.toString((char) (version.charAt(0) + 1));
			BasicHttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
			resp.setHeader("Content-Version", version);
			resp.setEntity(new StringEntity("Hello World!"));
			return resp;
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
		config.addBehaviour(TestETagResponse.class, "urn:test:test-etag-response");
		config.addBehaviour(TestLastModifiedResponse.class, "urn:test:test-lastmodified-response");
		config.addBehaviour(TestVersionResponse.class, "urn:test:test-version-response");
		repository = new ObjectRepositoryFactory().createRepository(config,
				memory);
		ObjectConnection con = repository.getConnection();
		ValueFactory vf = con.getValueFactory();
		con.add(vf.createURI(RESOURCE), RDF.TYPE,
				vf.createURI("urn:test:test-response"));
		con.add(vf.createURI(MATCH), RDF.TYPE,
				vf.createURI("urn:test:test-etag-response"));
		con.add(vf.createURI(SINCE), RDF.TYPE,
				vf.createURI("urn:test:test-lastmodified-response"));
		con.add(vf.createURI(VERSION), RDF.TYPE,
				vf.createURI("urn:test:test-version-response"));
		con.close();
		chain = new RequestExecChain();
		chain.addRepository(RESOURCE, repository);
		chain.addRepository(SECURE, repository);
	}

	public void tearDown() throws Exception {
		chain.shutdown();
		repository.shutDown();
		chain.awaitTermination(1, TimeUnit.SECONDS);
	}

	public void testHelloWorld() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
	}

	public void testGetNull() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "application/xml");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 404, resp
				.getStatusLine().getStatusCode());
		assertNull(resp.getEntity());
		assertEquals("OPTIONS, DELETE, GET, HEAD, POST", resp.getFirstHeader("Allow").getValue());
	}

	public void testResponseWithParialBody() throws Exception {
		final AtomicLong read = new AtomicLong();
		final AtomicBoolean closed = new AtomicBoolean(false);
		body = Channels.newInputStream(new ReadableByteChannel() {
			public boolean isOpen() {
				return !closed.get();
			}
			public void close() throws IOException {
				closed.set(true);
			}
			public int read(ByteBuffer dst) throws IOException {
				if (closed.get())
					return -1;
				int r = dst.remaining();
				read.addAndGet(r);
				dst.put((byte[]) Array.newInstance(Byte.TYPE, r));
				return r;
			}
		});
		BasicHttpRequest request = new BasicHttpRequest("POST", RESOURCE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp = execute(request);
		try {
			assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
					.getStatusLine().getStatusCode());
			assertTrue(read.get() > 256);
			assertNull(resp.getFirstHeader("Content-Length"));
		} finally {
			closed.set(true);
			EntityUtils.consume(resp.getEntity());
		}
	}

	public void testOptions() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("OPTIONS", RESOURCE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 204, resp
				.getStatusLine().getStatusCode());
		assertEquals("OPTIONS, DELETE, GET, HEAD, POST", resp.getFirstHeader("Allow").getValue());
	}

	public void testNoneMatchRequest() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", MATCH,
				HttpVersion.HTTP_1_1);
		request.setHeader("If-None-Match", "\"hello\"");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 304, resp
				.getStatusLine().getStatusCode());
	}

	public void testMatchRequest() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("POST", MATCH,
				HttpVersion.HTTP_1_1);
		request.setHeader("If-Match", "\"world\"");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 412, resp
				.getStatusLine().getStatusCode());
		request.setHeader("If-Match", "\"hello\"");
		assertEquals(200, execute(request).getStatusLine().getStatusCode());
	}

	public void testUnmodifiedRequest() throws Exception {
		Date yesterday = new Date();
		yesterday.setDate(yesterday.getDate() - 1);
		BasicHttpRequest request = new BasicHttpRequest("POST", SINCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("If-Unmodified-Since", DateUtils.formatDate(yesterday));
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 412, resp
				.getStatusLine().getStatusCode());
		request.setHeader("If-Unmodified-Since", DateUtils.formatDate(new Date()));
		assertEquals(200, execute(request).getStatusLine().getStatusCode());
	}

	public void testModifiedRequest() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", SINCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("If-Modified-Since", DateUtils.formatDate(new Date()));
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 304, resp
				.getStatusLine().getStatusCode());
	}

	public void testBadRequest() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("DELETE", RESOURCE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 400, resp
				.getStatusLine().getStatusCode());
		EntityUtils.consume(resp.getEntity());
	}

	public void testInterceptor() throws Exception {
		chain.resetCacheNow();
		InterceptorTester.interceptCount = 0;
		InterceptorTester.processCount = 0;
		InterceptorTester.response = new BasicHttpResponse(
				HttpVersion.HTTP_1_1, 405, "Testing Authorization");
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		assertEquals(405, execute(request).getStatusLine().getStatusCode());
		assertEquals(1, InterceptorTester.interceptCount);
		assertEquals(1, InterceptorTester.processCount);
		assertEquals(200, execute(request).getStatusLine().getStatusCode());
		assertEquals(2, InterceptorTester.interceptCount);
		assertEquals(2, InterceptorTester.processCount);
	}

	public void testContentVersion() throws Exception {
		BasicHttpRequest get = new BasicHttpRequest("GET", VERSION,
				HttpVersion.HTTP_1_1);
		BasicHttpRequest post = new BasicHttpRequest("POST", VERSION,
				HttpVersion.HTTP_1_1);
		TestVersionResponse.version = "A";
		assertEquals("A", execute(get).getFirstHeader("Content-Version")
				.getValue());
		HttpResponse resp = execute(post);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("B", resp.getFirstHeader("Content-Version").getValue());
		assertEquals("A", resp.getFirstHeader("Derived-From").getValue());
	}

	public void testPath() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "path",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("path", EntityUtils.toString(resp.getEntity()));
	}

	public void testNestedPath() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "path/file",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("path/file", EntityUtils.toString(resp.getEntity()));
	}

	public void testDeepPath() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE + "path1/path2/file",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("path1/path2/file", EntityUtils.toString(resp.getEntity()));
	}

	public void testGZip() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", RESOURCE,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/json");
		request.setHeader("Accept-Encoding", "gzip");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("gzip", resp.getFirstHeader("Content-Encoding").getValue());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream in = new GZIPInputStream(resp.getEntity().getContent());
		ChannelUtil.transfer(in, baos);
		String json = new String(baos.toByteArray());
		assertTrue(json.startsWith("["));
	}

	public void testCache() throws Exception {
		TestETagResponse.counter = 0;
		BasicHttpRequest request = new BasicHttpRequest("GET", MATCH,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		assertEquals(1, TestETagResponse.counter);
	}

	public void testPublicCache() throws Exception {
		TestETagResponse.counter = 0;
		BasicHttpRequest request = new BasicHttpRequest("GET", MATCH,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		assertEquals(1, TestETagResponse.counter);
	}

	public void testCacheServerReset() throws Exception {
		TestETagResponse.counter = 0;
		BasicHttpRequest request = new BasicHttpRequest("GET", MATCH,
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/plain");
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		chain.resetCacheNow();
		resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals("Hello World!", EntityUtils.toString(resp.getEntity()));
		assertEquals(Integer.toString("Hello World!".length()), resp.getFirstHeader("Content-Length").getValue());
		assertEquals(2, TestETagResponse.counter);
	}

	public void testCacheServerClient() throws Exception {
		TestLastModifiedResponse.modified = new Date();
		TestLastModifiedResponse.counter = 0;
		BasicHttpRequest request = new BasicHttpRequest("GET", SINCE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp1 = execute(request);
		assertEquals(resp1.getStatusLine().getReasonPhrase(), 200, resp1
				.getStatusLine().getStatusCode());
		Thread.sleep(1000);
		chain.resetCacheNow();
		String last = resp1.getFirstHeader("Last-Modified").getValue();
		request.setHeader("If-Modified-Since", last);
		HttpResponse resp2 = execute(request);
		assertEquals(200, resp2.getStatusLine().getStatusCode());
		assertEquals(2, TestLastModifiedResponse.counter);
	}

	public void testClientCache() throws Exception {
		TestLastModifiedResponse.modified = new Date();
		BasicHttpRequest request = new BasicHttpRequest("GET", SINCE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp1 = execute(request);
		assertEquals(resp1.getStatusLine().getReasonPhrase(), 200, resp1
				.getStatusLine().getStatusCode());
		String last = resp1.getFirstHeader("Last-Modified").getValue();
		request.setHeader("If-Modified-Since", last);
		HttpResponse resp2 = execute(request);
		assertEquals(304, resp2.getStatusLine().getStatusCode());
	}

	public void testUnGZip() throws Exception {
		String json = Arrays.toString((int[]) Array.newInstance(Integer.TYPE, 1024));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream out = new GZIPOutputStream(baos);
		out.write(json.getBytes());
		out.close();
		byte[] gz = baos.toByteArray();
		BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", RESOURCE + "?json",
				HttpVersion.HTTP_1_1);
		request.setHeader("Accept", "text/json");
		request.setHeader("Content-Type", "text/json");
		request.setHeader("Content-Encoding", "gzip");
		request.setEntity(new ByteArrayEntity(gz));
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 200, resp
				.getStatusLine().getStatusCode());
		assertEquals(json, EntityUtils.toString(resp.getEntity()));
	
	}

	public void testPing() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("OPTIONS", "*",
				HttpVersion.HTTP_1_1);
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 204, resp
				.getStatusLine().getStatusCode());
	}

	public void testSecureResource() throws Exception {
		BasicHttpRequest request = new BasicHttpRequest("GET", SECURE,
				HttpVersion.HTTP_1_1);
		HttpResponse resp = execute(request);
		assertEquals(resp.getStatusLine().getReasonPhrase(), 400, resp
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
