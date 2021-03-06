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
package org.openrdf.http.object.fluid.consumers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

import org.openrdf.http.object.fluid.Consumer;
import org.openrdf.http.object.fluid.Fluid;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidException;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.fluid.Vapor;
import org.openrdf.http.object.io.ChannelUtil;

/**
 * Writes a percent encoded form from a {@link Map}.
 * 
 * @author James Leigh
 * 
 */
public class FormMapMessageWriter implements Consumer<Map<String, Object>> {

	public boolean isConsumable(FluidType mtype, FluidBuilder delegate) {
		if (!mtype.isMap())
			return false;
		if (!mtype.key().isUnknown()) {
			if (!delegate.isConsumable(mtype.key("text/plain")))
				return false;
		}
		FluidType vt = mtype.component("text/plain");
		if (vt.isSetOrArray()) {
			if (!delegate.isConsumable(vt.component()))
				return false;
		} else if (!vt.isUnknown()) {
			if (!delegate.isConsumable(vt))
				return false;
		}
		return mtype.is(getMediaType());
	}

	public Fluid consume(final Map<String, Object> result, final String base,
			final FluidType ftype, final FluidBuilder builder) {
		return new Vapor() {
			public String getSystemId() {
				return base;
			}

			public FluidType getFluidType() {
				return ftype;
			}

			public void asVoid() {
				// no-op
			}

			@Override
			protected String toChannelMedia(FluidType media) {
				return ftype.as(getMediaType()).as(media).preferred();
			}

			@Override
			protected ReadableByteChannel asChannel(FluidType media)
					throws UnsupportedEncodingException, IOException,
					FluidException {
				return write(ftype.as(getMediaType()), result, base, builder);
			}

			public String toString() {
				return String.valueOf(result);
			}
		};
	}

	String getMediaType() {
		return "application/x-www-form-urlencoded";
	}

	ReadableByteChannel write(FluidType mtype, Map<String, Object> result,
			String base, FluidBuilder builder)
			throws UnsupportedEncodingException, IOException, FluidException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		writeTo(mtype, result, base, builder, out, 1024);
		return ChannelUtil.newChannel(out.toByteArray());
	}

	private void writeTo(FluidType mtype, Map<String, Object> result,
			String base, FluidBuilder builder, OutputStream out, int bufSize)
			throws UnsupportedEncodingException, IOException, FluidException {
		Charset charset = mtype.getCharset();
		if (charset == null) {
			charset = Charset.forName("ISO-8859-1");
		}
		FluidType vtype = mtype.component("text/plain");
		if (vtype.isUnknown()) {
			vtype = vtype.as(String[].class);
		}
		FluidType vctype = vtype;
		if (vctype.isSetOrArray()) {
			vctype = vctype.component();
		}
		Writer writer = new OutputStreamWriter(out, charset);
		try {
			if (result == null)
				return;
			boolean first = true;
			for (Map.Entry<String, Object> e : result.entrySet()) {
				if (e.getKey() != null) {
					String name = enc(writeTo(mtype.key("text/plain"),
							e.getKey(), base, builder));
					Iterator<?> iter = vtype.iteratorOf(e.getValue());
					if (first) {
						first = false;
					} else {
						writer.append("&");
					}
					writer.append(name);
					while (iter.hasNext()) {
						Object value = iter.next();
						if (value != null) {
							String str = writeTo(vctype, value, base, builder);
							writer.append("=").append(enc(str));
						}
						if (iter.hasNext()) {
							writer.append("&").append(name);
						}
					}
				}
			}
		} finally {
			writer.close();
		}
	}

	private String enc(String value) throws UnsupportedEncodingException {
		return URLEncoder.encode(value, "UTF-8");
	}

	private String writeTo(FluidType mtype, Object value, String base,
			FluidBuilder delegate) throws IOException, FluidException {
		if (mtype.isUnknown() && value != null) {
			mtype = mtype.as(value.getClass());
		}
		ReadableByteChannel in = delegate.consume(value, base, mtype)
				.asChannel("text/plain;charset=ISO-8859-1");
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
			ChannelUtil.transfer(in, out);
			return out.toString("ISO-8859-1");
		} finally {
			in.close();
		}
	}
}
