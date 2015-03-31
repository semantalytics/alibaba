package org.openrdf.http.object.chain;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.chain.HttpRequestChainInterceptor;

public class InterceptorTester implements HttpRequestChainInterceptor {
	public static int interceptCount;
	public static int processCount;
	public static HttpResponse response;
	public static Header[] responseHeaders;
	public static HttpRequestChainInterceptor callback;

	@Override
	public HttpResponse intercept(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		interceptCount++;
		if (callback == null)
			return response;
		else
			return callback.intercept(request, context);
	}

	@Override
	public void process(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		processCount++;
		if (callback != null)
			callback.process(request, response, context);
		if (responseHeaders != null && responseHeaders.length > 0) {
			for (Header hd : responseHeaders) {
				response.addHeader(hd);
			}
		}
	}

}
