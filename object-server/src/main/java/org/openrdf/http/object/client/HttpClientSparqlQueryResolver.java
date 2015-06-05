/*
 * Copyright (c) 2015 3 Round Stones Inc., Some rights reserved.
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
package org.openrdf.http.object.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.repository.object.advisers.SparqlQuery;
import org.openrdf.repository.object.advisers.SparqlQueryResolver;

/**
 * {@link SparqlQueryResolver} implementation that uses
 * {@link HttpClientFactory} to download sparql-query files.
 * 
 * @author James Leigh
 */
public class HttpClientSparqlQueryResolver extends SparqlQueryResolver {
	private final SparqlQueryResolver delegate;

	/**
	 * Creates a new {@link SparqlQueryResolver}.
	 * 
	 * @param nonHttpResolver
	 *            for non-http and non-https
	 */
	public HttpClientSparqlQueryResolver(SparqlQueryResolver nonHttpResolver) {
		this.delegate = nonHttpResolver;
	}

	@Override
	public SparqlQuery resolve(String systemId) throws IOException,
			MalformedQueryException {
		if (systemId == null || !systemId.startsWith("http://")
				&& !systemId.startsWith("https://"))
			return delegate.resolve(systemId);
		HttpClientFactory factory = HttpClientFactory.getInstance();
		HttpUriClient client = factory.createHttpClient();
		try {
			HttpGet get = new HttpGet(systemId);
			get.addHeader("Accept", "application/sparql-query");
			get.addHeader("Accept-encoding", "gzip");
			HttpUriResponse resp = client.getResponse(get);
			try {
				String base = resp.getSystemId();
				HttpEntity entity = resp.getEntity();
				InputStream in = entity.getContent();
				Header enc = entity.getContentEncoding();
				if (enc != null && enc.getValue().contains("gzip")) {
					in = new GZIPInputStream(in);
				}
				InputStreamReader reader = new InputStreamReader(in, "UTF-8");
				try {
					return new SparqlQuery(reader, base);
				} finally {
					reader.close();
				}
			} finally {
				resp.close();
			}
		} finally {
			client.close();
		}
	}
}