/*
 * Copyright 2009-2010, Zepheira LLC Some rights reserved.
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
package org.openrdf.http.object.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import org.openrdf.repository.object.ObjectFactory;

/**
 * Writes primitives and their wrappers.
 * 
 * @author James Leigh
 * 
 */
public class PrimitiveBodyWriter implements MessageBodyWriter<Object> {
	private StringBodyWriter delegate = new StringBodyWriter();
	private Set<Class<?>> wrappers = new HashSet<Class<?>>();
	{
		wrappers.add(Boolean.class);
		wrappers.add(Character.class);
		wrappers.add(Byte.class);
		wrappers.add(Short.class);
		wrappers.add(Integer.class);
		wrappers.add(Long.class);
		wrappers.add(Float.class);
		wrappers.add(Double.class);
		wrappers.add(Void.class);
	}

	public boolean isText(String mimeType, Class<?> type, Type genericType,
			ObjectFactory of) {
		return delegate.isText(mimeType, String.class, String.class, of);
	}

	public boolean isWriteable(String mimeType, Class<?> type,
			Type genericType, ObjectFactory of) {
		if (type.isPrimitive() || !type.isInterface()
				&& wrappers.contains(type))
			return delegate.isWriteable(mimeType, String.class, String.class,
					of);
		return false;
	}

	public long getSize(String mimeType, Class<?> type, Type genericType,
			ObjectFactory of, Object obj, Charset charset) {
		return delegate.getSize(mimeType, String.class, String.class, of,
				String.valueOf(obj), charset);
	}

	public String getContentType(String mimeType, Class<?> type,
			Type genericType, ObjectFactory of, Charset charset) {
		return delegate.getContentType(mimeType, String.class, String.class,
				of, charset);
	}

	public ReadableByteChannel write(String mimeType, Class<?> type,
			Type genericType, ObjectFactory of, Object result, String base,
			Charset charset) throws IOException {
		return delegate.write(mimeType, String.class, String.class, of, String
				.valueOf(result), base, charset);
	}

	public void writeTo(String mimeType, Class<?> type, Type genericType,
			ObjectFactory of, Object result, String base, Charset charset,
			OutputStream out, int bufSize) throws IOException {
		delegate.writeTo(mimeType, String.class, String.class, of, String
				.valueOf(result), base, charset, out, bufSize);
	}
}
