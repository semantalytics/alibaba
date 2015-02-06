package org.openrdf.server.object.server.helpers;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Future;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;
import org.openrdf.server.object.server.AsyncExecChain;
import org.openrdf.server.object.server.HttpRequestChainInterceptor;

public class HttpRequestChainInterceptorExecChain implements AsyncExecChain {
	private final AsyncExecChain delegate;
	private final ServiceLoader<HttpRequestChainInterceptor> loader;

	public HttpRequestChainInterceptorExecChain(AsyncExecChain delegate) {
		this.delegate = delegate;
		ClassLoader cl = this.getClass().getClassLoader();
		this.loader = ServiceLoader.load(HttpRequestChainInterceptor.class, cl);
	}

	@Override
	public Future<HttpResponse> execute(HttpHost host, HttpRequest request,
			final HttpContext context, FutureCallback<HttpResponse> callback) {
		try {
			callback = new ResponseCallback(callback) {
				public void completed(HttpResponse result) {
					try {
						process(result, context);
						super.completed(result);
					} catch (IOException ex) {
						super.failed(ex);
					} catch (RuntimeException ex) {
						super.failed(ex);
					} catch (HttpException ex) {
						super.failed(ex);
					}
				}
			};
			HttpResponse response = intercept(request, context);
			if (response != null) {
				return new CompletedResponse(callback, response);
			}
		} catch (IOException ex) {
			callback.failed(ex);
		} catch (HttpException ex) {
			callback.failed(ex);
		}
		return delegate.execute(host, request, context, callback);
	}

	private HttpResponse intercept(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		Iterator<HttpRequestChainInterceptor> iter = loader.iterator();
		while (iter.hasNext()) {
			HttpRequestChainInterceptor interceptor = iter.next();
			HttpResponse resp = interceptor.intercept(request, context);
			if (resp != null)
				return resp;
		}
		return null;
	}

	void process(HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		Iterator<HttpRequestChainInterceptor> iter = loader.iterator();
		while (iter.hasNext()) {
			HttpRequestChainInterceptor interceptor = iter.next();
			interceptor.process(response, context);
		}
	}

}
