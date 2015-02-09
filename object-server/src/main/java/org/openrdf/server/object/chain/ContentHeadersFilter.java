/*
 * Copyright 2010, Zepheira LLC Some rights reserved.
 * Copyright (c) 2011 Talis Inc., Some rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution. 
 * - Neither the name of the openrdf.org nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.openrdf.server.object.chain;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
import org.openrdf.server.object.helpers.ObjectContext;
import org.openrdf.server.object.helpers.Request;
import org.openrdf.server.object.helpers.ResourceTarget;
import org.openrdf.server.object.helpers.ResponseCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies HEAD response responseHeaders to other responses.
 * 
 * @author James Leigh
 * 
 */
public class ContentHeadersFilter implements AsyncExecChain {
	private static final Set<String> contentHeaders = new HashSet<String>(
			Arrays.asList("age", "cache-control", "content-encoding",
					"content-language", "content-length", "content-md5",
					"content-disposition", "content-range", "content-type",
					"expires", "location", "pragma", "refresh"));
	private final Logger logger = LoggerFactory.getLogger(ContentHeadersFilter.class);
	private final AsyncExecChain delegate;

	public ContentHeadersFilter(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target,
			final HttpRequest request, final HttpContext context,
			FutureCallback<HttpResponse> callback) {
		ObjectContext ctx = ObjectContext.adapt(context);
		final Request req = new Request(request, ctx);
		if ("HEAD".equals(req.getMethod()))
			delegate.execute(target, request, context, callback);
		final HttpUriResponse head = getHeadResponse(request, ctx);
		ctx.setDerivedFromHeadResponse(head);
		return delegate.execute(target, request, context, new ResponseCallback(callback) {
			public void completed(HttpResponse result) {
				try {
					addHeaders(req, context, head, result);
					super.completed(result);
				} catch (RuntimeException ex) {
					super.failed(ex);
				}
			}
		});
	}

	private HttpUriResponse getHeadResponse(final HttpRequest request, ObjectContext ctx) {
		ResourceTarget trans = ctx.getResourceTarget();
		RequestLine line = request.getRequestLine();
		try {
			ProtocolVersion ver = line.getProtocolVersion();
			BasicHttpRequest head = new BasicHttpRequest("HEAD", line.getUri(), ver);
			for (Header header : request.getAllHeaders()) {
				head.addHeader(header);
			}
			if (trans.isHandled(head))
				return trans.invoke(head);
			else
				return trans.head(request);
		} catch (IOException e) {
			logger.warn(e.toString(), e);
		} catch (HttpException e) {
			logger.warn(e.toString(), e);
		}
		return null;
	}

	void addHeaders(Request req, HttpContext context, HttpResponse head,
			HttpResponse rb) {
		Header derivedFrom = head.getFirstHeader("Content-Version");
		Header version = rb.getFirstHeader("Content-Version");
		if (version != null && derivedFrom != null
				&& !version.getValue().equals(derivedFrom.getValue())) {
			for (Header hd : head.getHeaders("Content-Version")) {
				rb.addHeader("Derived-From", hd.getValue());
			}
		}
		int code = rb.getStatusLine().getStatusCode();
		for (Header hd : head.getAllHeaders()) {
			String name = hd.getName();
			if ("GET".equals(req.getMethod()) && code < 400 && code != 304
					|| !contentHeaders.contains(name.toLowerCase())) {
				addIfAbsent(name, head, rb);
			}
		}
	}

	private void addIfAbsent(String name, HttpResponse head, HttpResponse rb) {
		if (!rb.containsHeader(name) && head.containsHeader(name)) {
			for (Header hd : head.getHeaders(name)) {
				rb.addHeader(hd);
			}
		}
	}

}
