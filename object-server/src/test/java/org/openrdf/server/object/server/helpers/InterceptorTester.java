package org.openrdf.server.object.server.helpers;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.openrdf.server.object.server.HttpRequestChainInterceptor;

public class InterceptorTester implements HttpRequestChainInterceptor {
	public static int interceptCount;
	public static int processCount;
	public static HttpResponse response;

	@Override
	public HttpResponse intercept(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		interceptCount++;
		try {
			return response;
		} finally {
			response = null;
		}
	}

	@Override
	public void process(HttpResponse response, HttpContext context)
			throws HttpException, IOException {
		processCount++;
	}

}
