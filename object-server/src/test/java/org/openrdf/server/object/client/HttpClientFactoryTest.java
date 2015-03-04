/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.server.object.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

import junit.framework.ComparisonFailure;
import junit.framework.TestCase;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.openrdf.server.object.fluid.consumers.HttpMessageWriter;
import org.openrdf.server.object.io.ChannelUtil;

public class HttpClientFactoryTest extends TestCase {
	static final String ORIGIN = "http://example.com";
	static final BasicStatusLine _200 = new BasicStatusLine(
			HttpVersion.HTTP_1_1, 200, "OK");
	static final BasicStatusLine _302 = new BasicStatusLine(
			HttpVersion.HTTP_1_1, 302, "Found");
	static final BasicStatusLine _401 = new BasicStatusLine(
			HttpVersion.HTTP_1_1, 401, "Unauthorized");
	final Queue<HttpResponse> responses = new LinkedList<HttpResponse>();
	private final HttpClient client = HttpClientFactory.getInstance().createHttpClient(ORIGIN);
	private final ClientExecChain director = new ClientExecChain() {
		public CloseableHttpResponse execute(HttpRoute route,
				HttpRequestWrapper request, HttpClientContext clientContext,
				HttpExecutionAware execAware) throws IOException, HttpException {
			HttpResponse response = responses.poll();
			byte[] http = asByteArray(request);
			ByteArrayEntity entity = new ByteArrayEntity(http, ContentType.create("message/http"));
			response.setHeader(entity.getContentType());
			long length = entity.getContentLength();
			if (length >= 0) {
				response.setHeader("Content-Length", Long.toString(length));
			}
			response.setEntity(entity);
			HttpHost target = route.getTargetHost();
			try {
				URI root = new URI(target.getSchemeName(), null, target.getHostName(), target.getPort(), "/", null, null);
				return new HttpUriResponse(root.resolve(request.getURI()).toASCIIString(), response);
			} catch (URISyntaxException e) {
				return new HttpUriResponse(request.getURI().toASCIIString(), response);
			}
		}
	};

	public void setUp() throws Exception {
		responses.clear();
		HttpClientFactory.getInstance().putProxy(
				new HttpHost("example.com", -1, "http"), director);
	}

	public void tearDown() throws Exception {
		HttpClientFactory.getInstance().removeProxy(
				new HttpHost("example.com", -1, "http"), director);
	}

	public void test200() throws Exception {
		responses.add(new BasicHttpResponse(_200));
		client.execute(new HttpGet("http://example.com/200"),
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		});
	}

	public void testTargetHost() throws Exception {
		HttpCoreContext localContext = HttpCoreContext.create();
		responses.add(new BasicHttpResponse(_200));
		client.execute(new HttpGet("http://example.com/200"),
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		}, localContext);
		HttpHost host = localContext.getTargetHost();
		assertEquals(ORIGIN, host.toString());
	}

	public void testTarget() throws Exception {
		HttpCoreContext localContext = HttpCoreContext.create();
		responses.add(new BasicHttpResponse(_200));
		client.execute(new HttpGet("http://example.com/200"),
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		}, localContext);
		HttpHost host = localContext.getTargetHost();
		HttpRequest req = localContext.getRequest();
		URI root = new URI(host.getSchemeName(), null, host.getHostName(), host.getPort(), "/", null, null);
		assertEquals("http://example.com/200", root.resolve(req.getRequestLine().getUri()).toASCIIString());
	}

	public void test302() throws Exception {
		responses.add(new BasicHttpResponse(_302));
		client.execute(new HttpGet("http://example.com/302"),
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_302.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		});
	}

	public void test302Redirect() throws Exception {
		HttpGet get = new HttpGet("http://example.com/302");
		BasicHttpResponse redirect = new BasicHttpResponse(_302);
		redirect.setHeader("Location", "http://example.com/200");
		responses.add(redirect);
		responses.add(new BasicHttpResponse(_200));
		client.execute(get,
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		});
	}

	public void test302RedirectTarget() throws Exception {
		HttpCoreContext localContext = HttpCoreContext.create();
		HttpGet get = new HttpGet("http://example.com/302");
		BasicHttpResponse redirect = new BasicHttpResponse(_302);
		redirect.setHeader("Location", "http://example.com/200");
		responses.add(redirect);
		responses.add(new BasicHttpResponse(_200));
		client.execute(get,
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				return null;
			}
		}, localContext);
		HttpHost host = localContext.getTargetHost();
		HttpRequest req = localContext.getRequest();
		URI root = new URI(host.getSchemeName(), null, host.getHostName(), host.getPort(), "/", null, null);
		assertEquals("http://example.com/200", root.resolve(req.getRequestLine().getUri()).toASCIIString());
	}

	public void test302CachedRedirectTarget() throws Exception {
		do {
			HttpGet get = new HttpGet("http://example.com/302");
			BasicHttpResponse redirect = new BasicHttpResponse(_302);
			redirect.setHeader("Location", "http://example.com/200");
			redirect.setHeader("Cache-Control", "public,max-age=3600");
			responses.add(redirect);
			BasicHttpResponse doc = new BasicHttpResponse(_200);
			doc.setHeader("Cache-Control", "public,max-age=3600");
			responses.add(doc);
			client.execute(get,
					new ResponseHandler<Void>() {
				public Void handleResponse(HttpResponse response)
						throws ClientProtocolException, IOException {
					assertEquals(_200.getStatusCode(), response
							.getStatusLine().getStatusCode());
					return null;
				}
			});
		} while (false);
		do {
			HttpCoreContext localContext = HttpCoreContext.create();
			HttpGet get = new HttpGet("http://example.com/302");
			get.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
			BasicHttpResponse redirect = new BasicHttpResponse(_302);
			redirect.setHeader("Location", "http://example.com/200");
			redirect.setHeader("Cache-Control", "public,max-age=3600");
			responses.add(redirect);
			client.execute(get,
					new ResponseHandler<Void>() {
				public Void handleResponse(HttpResponse response)
						throws ClientProtocolException, IOException {
					assertEquals(_302.getStatusCode(), response
							.getStatusLine().getStatusCode());
					return null;
				}
			}, localContext);
			HttpHost host = localContext.getTargetHost();
			HttpRequest req = localContext.getRequest();
			URI root = new URI(host.getSchemeName(), null, host.getHostName(), -1, "/", null, null);
			assertEquals("http://example.com/302", root.resolve(req.getRequestLine().getUri()).toASCIIString());
		} while (false);
	}

	public void testCookieStored() throws Exception {
		CookieStore cookieStore = new BasicCookieStore();
		do {
			HttpGet get = new HttpGet("http://example.com/setcookie");
			HttpClientContext localContext = HttpClientContext.create();
			localContext.setCookieStore(cookieStore);
			BasicHttpResponse setcookie = new BasicHttpResponse(_200);
			setcookie.addHeader("Set-Cookie", "oat=meal");
			setcookie.addHeader("Cache-Control", "no-store");
			responses.add(setcookie);
			client.execute(get,
					new ResponseHandler<Void>() {
				public Void handleResponse(HttpResponse response)
						throws ClientProtocolException, IOException {
					assertEquals(_200.getStatusCode(), response
							.getStatusLine().getStatusCode());
					assertTrue(response.containsHeader("Set-Cookie"));
					return null;
				}
			}, localContext);
		} while (false);
		do {
			HttpGet get = new HttpGet("http://example.com/getcookie");
			HttpClientContext localContext = HttpClientContext.create();
			localContext.setCookieStore(cookieStore);
			BasicHttpResponse getcookie = new BasicHttpResponse(_200);
			responses.add(getcookie);
			client.execute(get,
					new ResponseHandler<Void>() {
				public Void handleResponse(HttpResponse response)
						throws ClientProtocolException, IOException {
					assertEquals(_200.getStatusCode(), response
							.getStatusLine().getStatusCode());
					assertContains("oat=meal", asString(response.getEntity()));
					return null;
				}
			}, localContext);
		} while (false);
	}

	public void testAuthentication() throws Exception {
		HttpGet get = new HttpGet("http://example.com/protected");
		HttpClientContext localContext = HttpClientContext.create();
		AuthScope scope = new AuthScope("example.com", -1);
		UsernamePasswordCredentials cred = new UsernamePasswordCredentials("Aladdin", "open sesame");
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(scope, cred);
		localContext.setCredentialsProvider(credsProvider);
		BasicHttpResponse unauth = new BasicHttpResponse(_401);
		unauth.setHeader("WWW-Authenticate", "Basic realm=\"insert realm\"");
		responses.add(unauth);
		responses.add(new BasicHttpResponse(_200));
		client.execute(get,
				new ResponseHandler<Void>() {
			public Void handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				assertEquals(_200.getStatusCode(), response
						.getStatusLine().getStatusCode());
				assertContains("Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", asString(response.getEntity()));
				return null;
			}
		}, localContext);
	}

	byte[] asByteArray(HttpRequest request) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ChannelUtil.transfer(new HttpMessageWriter().serialize(request), baos);
		return baos.toByteArray();
	}

	String asString(HttpEntity entity) throws IOException {
		return EntityUtils.toString(entity);
	}

	void assertContains(String needle, String actual) {
		if (actual == null || !actual.contains(needle))
			throw new ComparisonFailure("", needle, actual);
	}

}
