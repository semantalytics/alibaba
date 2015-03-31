package org.openrdf.http.object.chain;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.helpers.ResourceTarget;
import org.openrdf.http.object.helpers.ResponseCallback;

public class GETHeadResponseFilter implements AsyncExecChain {
	private static final Set<String> contentHeaders = new HashSet<String>(
			Arrays.asList("age", "cache-control", "content-encoding",
					"content-language", "content-length", "content-md5",
					"content-disposition", "content-range", "content-type",
					"transfer-encoding", "expires", "location", "pragma", "refresh"));
	final AsyncExecChain delegate;

	public GETHeadResponseFilter(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target, HttpRequest request,
			HttpContext context, FutureCallback<HttpResponse> callback) {
		ObjectContext ctx = ObjectContext.adapt(context);
		ResourceTarget resource = ctx.getResourceTarget();
		if (!"HEAD".equals(request.getRequestLine().getMethod()) || resource.getHandlerMethod(request) != null)
			return delegate.execute(target, request, context, callback);
		HttpRequest get = asGetRequest(request);
		if (resource.getHandlerMethod(get) == null) {
			BasicHttpResponse resp = new BasicHttpResponse(
					HttpVersion.HTTP_1_1, 404, "Not Found");
			BasicFuture<HttpResponse> future;
			future = new BasicFuture<HttpResponse>(callback);
			future.completed(resp);
			return future;
		} else if (ctx.getOriginalRequest() == null) {
			return delegate.execute(target, get, context, new ResponseCallback(callback) {
				public void completed(HttpResponse rb) {
					try {
						HttpEntity e = rb.getEntity();
						if (e != null) {
							EntityUtils.consume(e);
							rb.setEntity(null);
							if (!rb.containsHeader("Content-Encoding") && e.getContentEncoding() != null) {
								rb.setHeader(e.getContentEncoding());
							}
							if (!rb.containsHeader("Content-Type") && e.getContentType() != null) {
								rb.setHeader(e.getContentType());
							}
							if (!rb.containsHeader("Content-Length") && e.getContentLength() >= 0) {
								rb.setHeader("Content-Length", Long.toString(e.getContentLength()));
							}
						}
						super.completed(rb);
					} catch (RuntimeException ex) {
						super.failed(ex);
					} catch (IOException ex) {
						super.failed(ex);
					}
				}
			});
		} else {
			BasicFuture<HttpResponse> future;
			future = new BasicFuture<HttpResponse>(callback);
			try {
				future.completed(ctx.getResourceTarget().head(get));
			} catch (IOException e) {
				future.failed(e);
			} catch (HttpException e) {
				future.failed(e);
			} catch (RuntimeException e) {
				future.failed(e);
			}
			return future;
		}
	}

	private HttpRequest asGetRequest(HttpRequest request) {
		RequestLine line = request.getRequestLine();
		ProtocolVersion ver = line.getProtocolVersion();
		BasicHttpRequest get = new BasicHttpRequest("GET", line.getUri(), ver);
		for (Header header : request.getAllHeaders()) {
			if (!contentHeaders.contains(header.getName().toLowerCase())) {
				get.addHeader(header);
			}
		}
		return get;
	}

}
