package org.openrdf.server.object.server.chain;

import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.openrdf.server.object.client.HttpUriResponse;
import org.openrdf.server.object.server.AsyncExecChain;
import org.openrdf.server.object.server.helpers.CalliContext;
import org.openrdf.server.object.server.helpers.ResourceOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DerivedFromHeadFilter implements AsyncExecChain {
	private final Logger logger = LoggerFactory.getLogger(DerivedFromHeadFilter.class);
	private AsyncExecChain delegate;

	public DerivedFromHeadFilter(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target, HttpRequest request,
			HttpContext context, FutureCallback<HttpResponse> callback) {
		CalliContext ctx = CalliContext.adapt(context);
		ResourceOperation trans = ctx.getResourceTransaction();
		RequestLine line = request.getRequestLine();
		try {
			ProtocolVersion ver = line.getProtocolVersion();
			BasicHttpRequest req = new BasicHttpRequest("HEAD", line.getUri(), ver);
			for (Header header : request.getAllHeaders()) {
				req.addHeader(header);
			}
			HttpUriResponse head = trans.invoke(req);
			ctx.setDerivedFromHeadResponse(head);
		} catch (IOException e) {
			logger.warn(e.toString(), e);
		} catch (HttpException e) {
			logger.warn(e.toString(), e);
		}
		return delegate.execute(target, request, context, callback);
	}

}
