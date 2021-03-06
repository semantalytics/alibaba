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
package org.openrdf.http.object.fluid.helpers;

import info.aduna.lang.FileFormat;
import info.aduna.lang.service.FileFormatServiceRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.fluid.Consumer;
import org.openrdf.http.object.fluid.Fluid;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidException;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.fluid.Vapor;
import org.openrdf.http.object.io.ChannelUtil;
import org.openrdf.http.object.io.ProducerStream;
import org.openrdf.http.object.io.ProducerStream.OutputProducer;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.rio.RDFHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		Consumer<T> {
	final Logger logger = LoggerFactory.getLogger(MessageWriterBase.class);
	private final FileFormatServiceRegistry<FF, S> registry;
	final String[] mimeTypes;
	private final Class<T> type;

	public MessageWriterBase(FileFormatServiceRegistry<FF, S> registry,
			Class<T> type) {
		this.registry = registry;
		this.type = type;
		Set<String> set = new LinkedHashSet<String>();
		for (FF format : registry.getKeys()) {
			set.addAll(format.getMIMETypes());
		}
		mimeTypes = set.toArray(new String[set.size()]);
	}

	public boolean isConsumable(FluidType ftype, FluidBuilder builder) {
		if (!isAssignableFrom(ftype.asClass()))
			return false;
		FluidType possible = new FluidType(ftype.asType(), mimeTypes).as(ftype);
		return getFactory(possible.preferred()) != null;
	}

	public Fluid consume(final T result, final String base,
			final FluidType ftype, final FluidBuilder builder) {
		return new Vapor() {
			public String getSystemId() {
				return base;
			}

			public FluidType getFluidType() {
				return ftype;
			}

			public void asVoid() throws FluidException {
				if (result != null) {
					try {
						close(result);
					} catch (OpenRDFException e) {
						throw new FluidException(e);
					}
				}
			}

			@Override
			protected String toChannelMedia(FluidType media) {
				FluidType possible = new FluidType(ftype.asType(), mimeTypes)
						.as(ftype).as(media);
				String contentType = possible.preferred();
				if (contentType == null)
					return null;
				FF format = getFormat(contentType);
				if (format.hasCharset() && contentType.startsWith("text/")
						&& !contentType.contains("charset=")) {
					Charset charset = possible.getCharset();
					charset = getCharset(format, charset);
					contentType += ";charset=" + charset.name();
				}
				return contentType;
			}

			@Override
			protected ReadableByteChannel asChannel(FluidType media)
					throws IOException, OpenRDFException, XMLStreamException,
					TransformerException, ParserConfigurationException {
				return write(ftype.as(toChannelMedia(media)), result, base);
			}

			public String toString() {
				return String.valueOf(result);
			}
		};
	}

	protected boolean isAssignableFrom(Class<?> type) {
		return this.type.isAssignableFrom(type);
	}

	protected void close(T result) throws OpenRDFException {
		// no-op
	}

	final ReadableByteChannel write(final FluidType mtype,
			final T result, final String base)
			throws IOException {
		return ChannelUtil.newChannel(new ProducerStream(new OutputProducer() {
			public void produce(OutputStream out) throws IOException {
				try {
					writeTo(mtype, result, base, out, 1024);
				} catch (OpenRDFException e) {
					throw new IOException(e);
				} finally {
					try {
						if (result != null) {
							close(result);
						}
					} catch (OpenRDFException e) {
						logger.error(e.toString(), e);
					} finally {
						out.close();
					}
				}
			}

			public String toString() {
				return String.valueOf(result);
			}
		}));
	}

	void writeTo(FluidType mtype, T result, String base,
			OutputStream out, int bufSize)
			throws IOException, OpenRDFException {
		Charset charset = mtype.getCharset();
		String mimeType = mtype.preferred();
		FF format = getFormat(mimeType);
		if (charset == null && format.hasCharset()) {
			charset = getCharset(format, charset);
		}
		try {
			writeTo(getFactory(mimeType), result, out, charset, base);
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

	protected abstract void writeTo(S factory, T result,
			OutputStream out, Charset charset, String base) throws IOException, RDFHandlerException,
			QueryEvaluationException, TupleQueryResultHandlerException;

	protected S getFactory(String mimeType) {
		if (mimeType == null)
			return null;
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
