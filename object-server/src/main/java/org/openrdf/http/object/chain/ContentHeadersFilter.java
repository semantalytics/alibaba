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
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.helpers.Request;
import org.openrdf.http.object.helpers.ResourceTarget;
import org.openrdf.http.object.helpers.ResponseCallback;

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
					"transfer-encoding", "expires", "location", "pragma", "refresh"));
	private final AsyncExecChain delegate;

	public ContentHeadersFilter(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target,
			final HttpRequest request, final HttpContext context,
			FutureCallback<HttpResponse> callback) {
		final ObjectContext ctx = ObjectContext.adapt(context);
		final Request req = new Request(request, ctx);
		return delegate.execute(target, request, context, new ResponseCallback(callback) {
			public void completed(HttpResponse result) {
				try {
					HttpResponse head = ctx.getDerivedFromHeadResponse();
					HttpRequest oreq = ctx.getOriginalRequest();
					ResourceTarget resource = ctx.getResourceTarget();
					if (head == null && oreq != null
							&& resource.getHandlerMethod(req) != null) {
						head = resource.head(oreq);
					}
					addHeaders(req, context, head, result);
					super.completed(result);
				} catch (RuntimeException ex) {
					super.failed(ex);
				} catch (IOException ex) {
					super.failed(ex);
				} catch (HttpException ex) {
					super.failed(ex);
				}
			}
		});
	}

	void addHeaders(Request req, HttpContext context, HttpResponse head,
			HttpResponse rb) {
		if (head != null) {
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
				boolean safe = "GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod());
				if (safe && code < 400 && code != 304
						|| !contentHeaders.contains(name.toLowerCase())) {
					addIfAbsent(name, head, rb);
				}
			}
		}
		HttpEntity entity = rb.getEntity();
		if (entity != null) {
			if (!rb.containsHeader("Content-Encoding") && entity.getContentEncoding() != null) {
				rb.setHeader(entity.getContentEncoding());
			}
			if (!rb.containsHeader("Content-Type") && entity.getContentType() != null) {
				rb.setHeader(entity.getContentType());
			}
			if (!rb.containsHeader("Content-Length") && entity.getContentLength() >= 0) {
				rb.setHeader("Content-Length", Long.toString(entity.getContentLength()));
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
