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
package org.openrdf.http.object.chain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.helpers.ChainedFutureCallback;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.helpers.ResponseCallback;

/**
 * Stores HEAD response response for use with other filters.
 * 
 * @author James Leigh
 * 
 */
public class DerivedFromHeadFilter implements AsyncExecChain {
	private static final Set<String> contentHeaders = new HashSet<String>(
			Arrays.asList("age", "cache-control", "content-encoding",
					"content-language", "content-length", "content-md5",
					"content-disposition", "content-range", "content-type",
					"transfer-encoding", "expires", "location", "pragma", "refresh"));
	final AsyncExecChain delegate;

	public DerivedFromHeadFilter(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(final HttpHost target,
			final HttpRequest request, final HttpContext context,
			final FutureCallback<HttpResponse> callback) {
		if ("HEAD".equals(request.getRequestLine().getMethod()))
			return delegate.execute(target, request, context, callback);
		HttpRequest head = asHeadRequest(request);
		final ObjectContext ctx = ObjectContext.adapt(context);
		final BasicFuture<HttpResponse> future = new BasicFuture<HttpResponse>(callback);
		final ChainedFutureCallback chained = new ChainedFutureCallback(future);
		ctx.setOriginalRequest(request);
		delegate.execute(target, head, context, new ResponseCallback(chained) {
			public void completed(HttpResponse headResponse) {
				try {
					ctx.setDerivedFromHeadResponse(headResponse);
					ctx.setOriginalRequest(null);
					delegateRequest(target, request, context, chained);
				} catch (RuntimeException ex) {
					failed(ex);
				}
			}

			public void failed(Exception ex) {
				ctx.setOriginalRequest(null);
				super.failed(ex);
			}

			public void cancelled() {
				ctx.setOriginalRequest(null);
				super.cancelled();
			}
		});
		return future;
	}

	Future<HttpResponse> delegateRequest(HttpHost target,
			HttpRequest request, HttpContext context,
			FutureCallback<HttpResponse> callback) {
		final ObjectContext ctx = ObjectContext.adapt(context);
		return delegate.execute(target, request, context, new ResponseCallback(callback) {
			public void completed(HttpResponse rb) {
				try {
					ctx.setDerivedFromHeadResponse(null);
					super.completed(rb);
				} catch (RuntimeException ex) {
					failed(ex);
				}
			}

			public void failed(Exception ex) {
				ctx.setDerivedFromHeadResponse(null);
				super.failed(ex);
			}

			public void cancelled() {
				ctx.setDerivedFromHeadResponse(null);
				super.cancelled();
			}
		});
	}

	private HttpRequest asHeadRequest(HttpRequest request) {
		RequestLine line = request.getRequestLine();
		ProtocolVersion ver = line.getProtocolVersion();
		BasicHttpRequest head = new BasicHttpRequest("HEAD", line.getUri(), ver);
		for (Header header : request.getAllHeaders()) {
			if (!contentHeaders.contains(header.getName().toLowerCase())) {
				head.addHeader(header);
			}
		}
		if ("PUT".equals(line.getMethod()) && request.containsHeader("Content-Type")) {
			head.setHeader("Accept", request.getFirstHeader("Content-Type").getValue());
		}
		return head;
	}

}
