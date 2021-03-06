/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.http.object.client;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.openrdf.http.object.util.URLUtil;

public class HttpUriResponse implements CloseableHttpResponse {
	private final HttpResponse delegate;
	private final String systemId;

	public HttpUriResponse(String systemId, HttpResponse delegate) {
		assert delegate != null;
		this.delegate = delegate;
		this.systemId = systemId;
	}

	public String toString() {
		return delegate.toString();
	}

	public String getSystemId() {
		return systemId;
	}

	public URI getURI() {
		return URI.create(systemId);
	}

	@Override
	public void close() throws IOException {
		if (delegate instanceof CloseableHttpResponse) {
			((CloseableHttpResponse) delegate).close();
		}
	}

	public String getRedirectLocation() throws IOException {
		int code = this.getStatusLine().getStatusCode();
		if (code == 301 || code == 302 || code == 307 || code == 308) {
			Header location = this.getFirstHeader("Location");
			if (location != null) {
				EntityUtils.consume(this.getEntity());
				String value = location.getValue();
				if (value.startsWith("/") || !value.contains(":")) {
					try {
						value = URLUtil.resolve(value, systemId);
					} catch (IllegalArgumentException e) {
						return value;
					}
				}
				return value;
			}
		}
		return null;
	}

	public HttpUriEntity getEntity() {
		HttpEntity entity = delegate.getEntity();
		if (entity == null)
			return null;
		return new HttpUriEntity(systemId, entity);
	}

	public StatusLine getStatusLine() {
		return delegate.getStatusLine();
	}

	public void setStatusLine(StatusLine statusline) {
		delegate.setStatusLine(statusline);
	}

	public ProtocolVersion getProtocolVersion() {
		return delegate.getProtocolVersion();
	}

	public void setStatusLine(ProtocolVersion ver, int code) {
		delegate.setStatusLine(ver, code);
	}

	public boolean containsHeader(String name) {
		return delegate.containsHeader(name);
	}

	public void setStatusLine(ProtocolVersion ver, int code, String reason) {
		delegate.setStatusLine(ver, code, reason);
	}

	public Header[] getHeaders(String name) {
		return delegate.getHeaders(name);
	}

	public void setStatusCode(int code) throws IllegalStateException {
		delegate.setStatusCode(code);
	}

	public Header getFirstHeader(String name) {
		return delegate.getFirstHeader(name);
	}

	public Header getLastHeader(String name) {
		return delegate.getLastHeader(name);
	}

	public void setReasonPhrase(String reason) throws IllegalStateException {
		delegate.setReasonPhrase(reason);
	}

	public Header[] getAllHeaders() {
		return delegate.getAllHeaders();
	}

	public void addHeader(Header header) {
		delegate.addHeader(header);
	}

	public void addHeader(String name, String value) {
		delegate.addHeader(name, value);
	}

	@Override
	public void setEntity(HttpEntity entity) {
		HttpEntity previously = getEntity();
		if (entity == null && previously != null) {
			EntityUtils.consumeQuietly(previously);
		}
		delegate.setEntity(entity);
	}

	public void setHeader(Header header) {
		delegate.setHeader(header);
	}

	public void setHeader(String name, String value) {
		delegate.setHeader(name, value);
	}

	public Locale getLocale() {
		return delegate.getLocale();
	}

	public void setHeaders(Header[] headers) {
		delegate.setHeaders(headers);
	}

	public void setLocale(Locale loc) {
		delegate.setLocale(loc);
	}

	public void removeHeader(Header header) {
		delegate.removeHeader(header);
	}

	public void removeHeaders(String name) {
		delegate.removeHeaders(name);
	}

	public HeaderIterator headerIterator() {
		return delegate.headerIterator();
	}

	public HeaderIterator headerIterator(String name) {
		return delegate.headerIterator(name);
	}

	public HttpParams getParams() {
		return delegate.getParams();
	}

	public void setParams(HttpParams params) {
		delegate.setParams(params);
	}

}
