/*
 * Copyright 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.model.ReadableHttpEntityChannel;
import org.openrdf.http.object.readers.AggregateReader;
import org.openrdf.http.object.readers.MessageBodyReader;
import org.openrdf.http.object.util.ChannelUtil;
import org.openrdf.http.object.util.ErrorWritableByteChannel;
import org.openrdf.http.object.util.MessageType;
import org.openrdf.http.object.writers.AggregateWriter;
import org.openrdf.http.object.writers.MessageBodyWriter;
import org.openrdf.repository.object.ObjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A light weight abstraction that can convert message bodies.
 */
public class RemoteConnection {
	private static final Executor executor = HTTPObjectClient.executor;
	private Logger logger = LoggerFactory.getLogger(RemoteConnection.class);
	private MessageBodyReader reader = AggregateReader.getInstance();
	private MessageBodyWriter writer = AggregateWriter.getInstance();
	private String uri;
	private ObjectConnection oc;
	private HttpRequest req;
	private HttpResponse resp;
	private InetSocketAddress addr;
	private HTTPObjectClient client;

	public RemoteConnection(InetSocketAddress remoteAddress, String method,
			String uri, String qs, ObjectConnection oc) throws IOException {
		this.addr = remoteAddress;
		this.uri = uri;
		this.oc = oc;
		String url = qs == null ? uri : (uri + '?' + qs);
		req = new BasicHttpRequest(method, url);
		String host = remoteAddress.getHostName();
		if (remoteAddress.getPort() != 80) {
			host += ":" + remoteAddress.getPort();
		}
		req.addHeader("Host", host);
		req.addHeader("Accept-Encoding", "gzip");
		client = HTTPObjectClient.getInstance();
	}

	public String toString() {
		return req.getRequestLine().toString();
	}

	public String getEnvelopeType() {
		return client.getEnvelopeType();
	}

	public void addHeader(String name, String value) {
		req.addHeader(name, value);
	}

	public OutputStream writeStream() throws IOException {
		Pipe pipe = Pipe.open();
		final ErrorWritableByteChannel sink = new ErrorWritableByteChannel(pipe.sink());
		final HttpEntityEnclosingRequest req = getHttpEntityRequest();
		req.setEntity(new ReadableHttpEntityChannel(null, -1, pipe.source()));
		executor.execute(new Runnable() {
			public void run() {
				try {
					HttpResponse resp = getHttpResponse();
					if (resp.getStatusLine().getStatusCode() >= 400) {
						Exception cause = ResponseException.create(resp);
						sink.error(new IOException(cause));
					}
				} catch (IOException e) {
					sink.error(e);
				} catch (Exception e) {
					sink.error(new IOException(e));
				}
			}
		});
		return ChannelUtil.newOutputStream(sink);
	}

	public void write(String media, Class<?> ptype, Type gtype, Object result)
			throws Exception {
		String mediaType = writer.getContentType(new MessageType(media, ptype, gtype, oc), null);
		if (mediaType != null && !req.containsHeader("Content-Type")) {
			req.addHeader("Content-Type", mediaType);
		}
		long size = writer.getSize(new MessageType(null, ptype, gtype, oc), result, null);
		if (size >= 0 && !req.containsHeader("Content-Length")) {
			req.addHeader("Content-Length", String.valueOf(size));
		} else if (size < 0) {
			req.addHeader("Transfer-Encoding", "chunked");
		}
		if (size > 500) {
			req.addHeader("Expect", "100-continue");
		}
		HttpEntityEnclosingRequest heer = getHttpEntityRequest();
		ReadableByteChannel in = writer.write(new MessageType(mediaType, ptype, gtype, oc),
				result, uri, null);
		heer.setEntity(new ReadableHttpEntityChannel(mediaType, size, in));
	}

	public int getResponseCode() throws IOException {
		return getHttpResponse().getStatusLine().getStatusCode();
	}

	public String getResponseMessage() throws IOException {
		return getHttpResponse().getStatusLine().getReasonPhrase();
	}

	/**
	 * Called if not reading body.
	 */
	public void close() throws IOException {
		if (resp != null) {
			try {
				HttpEntity entity = resp.getEntity();
				if (entity != null) {
					entity.consumeContent();
				}
			} catch (RuntimeException cause) {
				throw cause;
			} catch (IOException cause) {
				throw cause;
			} catch (Error cause) {
				throw cause;
			} catch (Throwable cause) {
				throw new IOException(cause);
			}
		}
	}

	public InputStream readStream() throws IOException {
		String encoding = getHeaderField("Content-Encoding");
		InputStream in = getInputStream();
		if (in == null)
			return null;
		if ("gzip".equals(encoding))
			return new GZIPInputStream(in);
		return new FilterInputStream(in) {
			public void close() throws IOException {
				super.close();
				getHttpResponse().getEntity().consumeContent();
			}
		};
	}

	public Object read(Type gtype, Class<?> rtype) throws Exception {
		String loc = getHeaderField("Location");
		InputStream in = getInputStream();
		String media = getHeaderField("Content-Type");
		ReadableByteChannel cin = null;
		if (in != null) {
			String encoding = getHeaderField("Content-Encoding");
			if ("gzip".equals(encoding)) {
				in = new GZIPInputStream(in);
			}
			final ReadableByteChannel delegate = ChannelUtil.newChannel(in);
			cin = new ReadableByteChannel() {

				public boolean isOpen() {
					return delegate.isOpen();
				}

				public void close() throws IOException {
					delegate.close();
					getHttpResponse().getEntity().consumeContent();
				}

				public int read(ByteBuffer dst) throws IOException {
					return delegate.read(dst);
				}

				@Override
				public String toString() {
					return delegate.toString();
				}
			};
		}
		return reader.readFrom(new MessageType(rtype, gtype, media, oc), cin, null, uri, loc);
	}

	private HttpEntityEnclosingRequest getHttpEntityRequest() {
		HttpEntityEnclosingRequest heer = new BasicHttpEntityEnclosingRequest(
				req.getRequestLine());
		heer.setHeaders(req.getAllHeaders());
		req = heer;
		return heer;
	}

	private InputStream getInputStream() throws IOException {
		HttpEntity entity = getHttpResponse().getEntity();
		if (entity == null)
			return null;
		return entity.getContent();
	}

	public HttpResponse getHttpResponse() throws IOException {
		if (resp == null) {
			resp = client.service(addr, req);
		}
		try {
			return resp;
		} catch (RuntimeException cause) {
			throw cause;
		} catch (Error cause) {
			throw cause;
		} catch (Throwable cause) {
			throw new IOException(cause);
		}
	}

	private String getHeaderField(String name) throws IOException {
		Header hd = getHttpResponse().getFirstHeader(name);
		if (hd == null)
			return null;
		return hd.getValue();
	}
}
