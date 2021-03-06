/*
 * Copyright (c) 2009, James Leigh All rights reserved.
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
package org.openrdf.http.object.fluid.producers.base;

import info.aduna.lang.FileFormat;
import info.aduna.lang.service.FileFormatServiceRegistry;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.fluid.Producer;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResultHandlerException;
import org.openrdf.query.resultio.QueryResultParseException;

/**
 * Base class for readers that use a {@link FileFormat}.
 * 
 * @author James Leigh
 * 
 * @param <FF>
 *            file format
 * @param <S>
 *            parser factory
 * @param <T>
 *            Java type returned
 */
public abstract class MessageReaderBase<FF extends FileFormat, S, T> implements
		Producer {
	private FileFormatServiceRegistry<FF, S> registry;
	private final String[] mimeTypes;
	private Class<T> type;

	public MessageReaderBase(FileFormatServiceRegistry<FF, S> registry,
			Class<T> type) {
		this.registry = registry;
		this.type = type;
		Set<String> set = new LinkedHashSet<String>();
		for (FF format : registry.getKeys()) {
			set.addAll(format.getMIMETypes());
		}
		mimeTypes = set.toArray(new String[set.size()]);
	}

	public boolean isProducable(FluidType ftype, FluidBuilder builder) {
		Class<?> type = ftype.asClass();
		if (Object.class.equals(type))
			return false;
		if (!classEquals(type))
			return false;
		FluidType possible = new FluidType(ftype.asType(), mimeTypes).as(ftype);
		return getFactory(possible.preferred()) != null;
	}

	public Object produce(FluidType ftype, ReadableByteChannel in,
			Charset charset, String base, FluidBuilder builder)
			throws QueryResultParseException, QueryResultHandlerException,
			IOException, QueryEvaluationException {
		FluidType possible = new FluidType(ftype.asType(), mimeTypes).as(ftype);
		return readFrom(getFactory(possible.preferred()), in, charset, base);
	}

	public abstract T readFrom(S factory, ReadableByteChannel in,
			Charset charset, String base) throws QueryResultParseException,
			QueryResultHandlerException, IOException,
			QueryEvaluationException;

	protected boolean classEquals(Class<?> type) {
		return type.equals(this.type);
	}

	protected S getFactory(String mime) {
		if (mime == null)
			return null;
		FF format = getFormat(mime);
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
