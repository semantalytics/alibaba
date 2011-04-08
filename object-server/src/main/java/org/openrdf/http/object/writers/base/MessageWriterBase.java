/*
 * Copyright 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
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
package org.openrdf.http.object.writers.base;

import info.aduna.lang.FileFormat;
import info.aduna.lang.service.FileFormatServiceRegistry;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.util.ManagedExecutors;
import org.openrdf.http.object.util.MessageType;
import org.openrdf.http.object.util.PipeErrorSource;
import org.openrdf.http.object.writers.MessageBodyWriter;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.rio.RDFHandlerException;

/**
 * Base class for writers that use a {@link FileFormat}.
 * 
 * @author James Leigh
 * 
 * @param <FF>
 *            file format
 * @param <S>
 *            reader factory
 * @param <T>
 *            Java type returned
 */
public abstract class MessageWriterBase<FF extends FileFormat, S, T> implements
		MessageBodyWriter<T> {
	private static Executor executor = ManagedExecutors.getWriterThreadPool();
	private FileFormatServiceRegistry<FF, S> registry;
	private Class<T> type;

	public MessageWriterBase(FileFormatServiceRegistry<FF, S> registry,
			Class<T> type) {
		this.registry = registry;
		this.type = type;
	}

	public boolean isText(MessageType mtype) {
		return getFormat(mtype.getMimeType()).hasCharset();
	}

	public long getSize(MessageType mtype, T result, Charset charset) {
		return -1;
	}

	public boolean isWriteable(MessageType mtype) {
		if (!this.type.isAssignableFrom((Class<?>) mtype.clas()))
			return false;
		return getFactory(mtype.getMimeType()) != null;
	}

	public String getContentType(MessageType mtype, Charset charset) {
		String mimeType = mtype.getMimeType();
		FF format = getFormat(mimeType);
		String contentType = null;
		if (mimeType != null) {
			for (String content : format.getMIMETypes()) {
				if (mimeType.startsWith(content)) {
					contentType = content;
				}
			}
		}
		if (contentType == null) {
			contentType = format.getDefaultMIMEType();
		}
		if (contentType.startsWith("text/") && format.hasCharset()) {
			charset = getCharset(format, charset);
			contentType += ";charset=" + charset.name();
		}
		return contentType;
	}

	public ReadableByteChannel write(final MessageType mtype, final T result,
			final String base, final Charset charset) throws IOException {
		Pipe pipe = Pipe.open();
		final SinkChannel out = pipe.sink();
		final PipeErrorSource in = new PipeErrorSource(pipe) {
			public String toString() {
				return result.toString();
			}
		};
		executor.execute(new Runnable() {
			public String toString() {
				return "writing " + result.toString();
			}

			public void run() {
				try {
					try {
						writeTo(mtype, result, base, charset, out, 1024);
					} finally {
						out.close();
					}
				} catch (IOException e) {
					in.error(e);
				} catch (Exception e) {
					in.error(new IOException(e));
				} catch (Error e) {
					in.error(new IOException(e));
				}
			}
		});
		return in;
	}

	public void writeTo(MessageType mtype, T result, String base,
			Charset charset, WritableByteChannel out, int bufSize)
			throws IOException, OpenRDFException {
		String mimeType = mtype.getMimeType();
		FF format = getFormat(mimeType);
		if (format.hasCharset()) {
			charset = getCharset(format, charset);
		}
		try {
			ObjectConnection con = mtype.getObjectConnection();
			writeTo(getFactory(mimeType), result, out, charset, base, con);
		} catch (RDFHandlerException e) {
			Throwable cause = e.getCause();
			try {
				if (cause != null)
					throw cause;
			} catch (IOException c) {
				throw c;
			} catch (OpenRDFException c) {
				throw c;
			} catch (Throwable c) {
				throw e;
			}
		} catch (TupleQueryResultHandlerException e) {
			Throwable cause = e.getCause();
			try {
				if (cause != null)
					throw cause;
			} catch (IOException c) {
				throw c;
			} catch (OpenRDFException c) {
				throw c;
			} catch (Throwable c) {
				throw e;
			}
		}
	}

	protected Charset getCharset(FF format, Charset charset) {
		if (charset == null) {
			charset = format.getCharset();
		}
		return charset;
	}

	public abstract void writeTo(S factory, T result, WritableByteChannel out,
			Charset charset, String base, ObjectConnection con) throws IOException,
			RDFHandlerException, QueryEvaluationException,
			TupleQueryResultHandlerException;

	protected S getFactory(String mimeType) {
		FF format = getFormat(mimeType);
		if (format == null)
			return null;
		return registry.get(format);
	}

	protected FF getFormat(String mimeType) {
		if (mimeType == null || mimeType.contains("*")
				|| "application/octet-stream".equals(mimeType)) {
			for (FF format : registry.getKeys()) {
				if (registry.get(format) != null)
					return format;
			}
			return null;
		}
		int idx = mimeType.indexOf(';');
		if (idx > 0) {
			mimeType = mimeType.substring(0, idx);
		}
		return registry.getFileFormatForMIMEType(mimeType);
	}
}
