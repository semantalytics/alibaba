/*
 * Copyright (c) 2009, Zepheira All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.fluid.Producer;
import org.openrdf.http.object.io.ChannelUtil;
import org.xml.sax.SAXException;

/**
 * Reads primitive types and their wrappers.
 * 
 * @author James Leigh
 * 
 */
public class PrimitiveBodyReader implements Producer {

	@Override
	public boolean isProducable(FluidType ftype, FluidBuilder builder) {
		return isPrimitive(ftype.asClass()) && ftype.is("text/*");
	}

	@Override
	public Object produce(FluidType ftype, ReadableByteChannel in,
			Charset charset, String base, FluidBuilder builder)
			throws OpenRDFException, IOException, XMLStreamException,
			ParserConfigurationException, SAXException,
			TransformerConfigurationException, TransformerException {
		if (in == null && ftype.is(Boolean.TYPE))
			return Boolean.FALSE;
		if (in == null && ftype.isPrimitive())
			return valueOf("0", ftype.asClass());
		if (in == null)
			return null;
		if (charset == null) {
			charset = Charset.defaultCharset();
		}
		InputStream stream = ChannelUtil.newInputStream(in);
		InputStreamReader isr = new InputStreamReader(stream, charset);
		BufferedReader reader = new BufferedReader(isr);
		try {
			String value = reader.readLine();
			return valueOf(value, ftype.asClass());
		} finally {
			reader.close();
		}
	}

	private boolean isPrimitive(Class<?> asClass) {
		return asClass.isPrimitive() || isPrimitiveWrapper(asClass);
	}

	private boolean isPrimitiveWrapper(Class<?> asClass) {
		if (Boolean.class.equals(asClass))
			return true;
		if (Byte.class.equals(asClass))
			return true;
		if (Short.class.equals(asClass))
			return true;
		if (Character.class.equals(asClass))
			return true;
		if (Integer.class.equals(asClass))
			return true;
		if (Long.class.equals(asClass))
			return true;
		if (Float.class.equals(asClass))
			return true;
		if (Double.class.equals(asClass))
			return true;
		return false;
	}

	private Object valueOf(String value, Class<?> type) {
		if (Boolean.TYPE.equals(type) || Boolean.class.equals(type))
			return (Boolean.valueOf(value));
		if (Character.TYPE.equals(type) || Character.class.equals(type))
			return (Character.valueOf(value.charAt(0)));
		if (Byte.TYPE.equals(type) || Byte.class.equals(type))
			return (Byte.valueOf(value));
		if (Short.TYPE.equals(type) || Short.class.equals(type))
			return (Short.valueOf(value));
		if (Integer.TYPE.equals(type) || Integer.class.equals(type))
			return (Integer.valueOf(value));
		if (Long.TYPE.equals(type) || Long.class.equals(type))
			return (Long.valueOf(value));
		if (Float.TYPE.equals(type) || Float.class.equals(type))
			return (Float.valueOf(value));
		if (Double.TYPE.equals(type) || Double.class.equals(type))
			return (Double.valueOf(value));
		return null;
	}

}
