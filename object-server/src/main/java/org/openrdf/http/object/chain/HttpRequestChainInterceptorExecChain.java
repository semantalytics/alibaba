package org.openrdf.http.object.chain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Future;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.helpers.CompletedResponse;
import org.openrdf.http.object.helpers.ResponseCallback;

public class HttpRequestChainInterceptorExecChain implements AsyncExecChain {
	private final AsyncExecChain delegate;
	private final List<HttpRequestChainInterceptor> interceptors = new ArrayList<HttpRequestChainInterceptor>();

	public HttpRequestChainInterceptorExecChain(AsyncExecChain delegate) {
		this.delegate = delegate;
		ClassLoader cl = this.getClass().getClassLoader();
		ServiceLoader<HttpRequestChainInterceptor> ld = ServiceLoader.load(HttpRequestChainInterceptor.class, cl);
		Iterator<HttpRequestChainInterceptor> iter = ld.iterator();
		while (iter.hasNext()) {
			interceptors.add(iter.next());
		}
	}

	@Override
	public Future<HttpResponse> execute(HttpHost host, final HttpRequest request,
			final HttpContext context, FutureCallback<HttpResponse> callback) {
		try {
			callback = new ResponseCallback(callback) {
				public void completed(HttpResponse result) {
					try {
						process(request, result, context);
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
		} catch (RuntimeException ex) {
			callback.failed(ex);
		}
		return delegate.execute(host, request, context, callback);
	}

	private HttpResponse intercept(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		for (HttpRequestChainInterceptor interceptor : interceptors) {
			HttpResponse resp = interceptor.intercept(request, context);
			if (resp != null)
				return resp;
		}
		return null;
	}

	void process(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		for (HttpRequestChainInterceptor interceptor : interceptors) {
			interceptor.process(request, response, context);
		}
	}

}
