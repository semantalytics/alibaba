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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.client.HttpUriResponse;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.helpers.Request;
import org.openrdf.http.object.helpers.ResourceTarget;
import org.openrdf.http.object.helpers.ResponseBuilder;
import org.openrdf.http.object.helpers.ResponseCallback;

/**
 * Responds for OPTIONS requests.
 * 
 * @author James Leigh
 * 
 */
public class OptionsHandler implements AsyncExecChain {
	private static final String REQUEST_METHOD = "Access-Control-Request-Method";
	private static final Set<String> ALLOW_HEADERS = new TreeSet<String>(
			Arrays.asList("Authorization", "Cache-Control", "Location",
					"Range", "Accept", "Accept-Charset", "Accept-Encoding",
					"Accept-Language", "Content-Encoding", "Content-Language",
					"Content-Length", "Content-Location", "Content-MD5",
					"Content-Type", "If-Match", "If-Modified-Since",
					"If-None-Match", "If-Range", "If-Unmodified-Since"));
	private final AsyncExecChain delegate;

	public OptionsHandler(AsyncExecChain delegate) {
		this.delegate = delegate;
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target,
			final HttpRequest request, final HttpContext context,
			final FutureCallback<HttpResponse> callback) {
		final ResourceTarget trans = ObjectContext.adapt(context).getResourceTarget();
		final Request req = new Request(request, context);
		final String m = req.getMethod();
		if ("OPTIONS".equals(m) && trans.getHandlerMethod(request) == null) {
			HttpUriResponse rb = new ResponseBuilder(request, context).noContent();
			addPreflightHeaders(trans, req, rb);
			addDiscoveryHeaders(trans, req, rb);
			BasicFuture<HttpResponse> future;
			future = new BasicFuture<HttpResponse>(callback);
			future.completed(rb);
			return future;
		} else {
			return delegate.execute(target, request, context, new ResponseCallback(callback) {
				public void completed(HttpResponse result) {
					try {
						if (result != null) {
							int status = result.getStatusLine().getStatusCode();
							if ("GET".equals(m) || "HEAD".equals(m) || status >= 400) {
								addDiscoveryHeaders(trans, req, result);
							} else if ("OPTIONS".equals(m)) {
								addPreflightHeaders(trans, req, result);
								addDiscoveryHeaders(trans, req, result);
							}
						}
						super.completed(result);
					} catch (RuntimeException ex) {
						super.failed(ex);
					}
				}
			});
		}
	}

	void addDiscoveryHeaders(ResourceTarget trans,
			Request request, HttpResponse rb) {
		StringBuilder sb = new StringBuilder();
		sb.append("OPTIONS");
		for (String method : trans.getAllowedMethods(request.getRequestURL())) {
			sb.append(", ").append(method);
		}
		if (!rb.containsHeader("Allow")) {
			rb.addHeader("Allow", sb.toString());
		}
		String acceptPost = trans.getAccept("POST", request.getRequestURL());
		if (acceptPost != null && acceptPost.length() > 0 && !rb.containsHeader("Accept-Post")) {
			rb.addHeader("Accept-Post", acceptPost);
		}
		String acceptPatch = trans.getAccept("PATCH", request.getRequestURL());
		if (acceptPatch != null && acceptPatch.length() > 0 && !rb.containsHeader("Accept-Patch")) {
			rb.addHeader("Accept-Patch", acceptPatch);
		}
	}

	void addPreflightHeaders(ResourceTarget trans, Request req,
			HttpResponse rb) {
		org.apache.http.Header m = req.getFirstHeader(REQUEST_METHOD);
		if (!rb.containsHeader("Access-Control-Allow-Methods")) {
			if (m == null) {
				StringBuilder sb = new StringBuilder();
				sb.append("OPTIONS");
				for (String method : trans.getAllowedMethods(req.getRequestURL())) {
					sb.append(", ").append(method);
				}
				rb.addHeader("Access-Control-Allow-Methods", sb.toString());
			} else {
				rb.addHeader("Access-Control-Allow-Methods", m.getValue());
			}
		}
		if (!rb.containsHeader("Access-Control-Allow-Headers")) {
			StringBuilder headers = new StringBuilder();
			for (String header : ALLOW_HEADERS) {
				if (headers.length() > 0) {
					headers.append(",");
				}
				headers.append(header);
			}
			String method = m == null ? null : m.getValue();
			String url = req.getRequestURL();
			for (String header : trans.getAllowedHeaders(method, url)) {
				if (!ALLOW_HEADERS.contains(header)) {
					headers.append(",");
					headers.append(header);
				}
			}
			rb.addHeader("Access-Control-Allow-Headers", headers.toString());
		}
	}

}
