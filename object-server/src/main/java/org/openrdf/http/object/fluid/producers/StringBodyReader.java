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
package org.openrdf.http.object.fluid.producers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.fluid.Producer;
import org.openrdf.http.object.io.ChannelUtil;

/**
 * Reads a {@link String}.
 * 
 * @author James Leigh
 * 
 */
public class StringBodyReader implements Producer {

	public boolean isProducable(FluidType ftype, FluidBuilder builder) {
		Class<?> cls = ftype.asClass();
		if (Object.class.equals(cls))
			return false;
		return cls.isAssignableFrom(String.class) && ftype.is("text/*");
	}

	public String produce(FluidType ftype, ReadableByteChannel in,
			Charset charset, String base, FluidBuilder builder) throws IOException {
		if (in == null)
			return null;
		if (charset == null) {
			charset = Charset.defaultCharset();
		}
		Reader reader = ChannelUtil.newReader(in, charset);
		try {
			StringWriter writer = new StringWriter();
			char[] cbuf = new char[512];
			int read;
			while ((read = reader.read(cbuf)) >= 0) {
				writer.write(cbuf, 0, read);
			}
			String str = writer.toString();
			if (str.length() == 0)
				return null;
			return str;
		} finally {
			reader.close();
		}
	}
}
