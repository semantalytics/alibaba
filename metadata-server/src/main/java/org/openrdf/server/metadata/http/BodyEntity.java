/*
 * Copyright (c) 2009, Zepheira All rights reserved.
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
package org.openrdf.server.metadata.http;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.resultio.QueryResultParseException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.server.metadata.readers.AggregateReader;
import org.openrdf.server.metadata.readers.MessageBodyReader;
import org.xml.sax.SAXException;

public abstract class BodyEntity implements Entity {
	private MessageBodyReader reader = AggregateReader.getInstance();
	private String mimeType;
	private boolean stream;
	private Charset charset;
	private String base;
	private String location;
	private ObjectConnection con;

	public BodyEntity(String mimeType, boolean stream, Charset charset,
			String base, String location, ObjectConnection con) {
		this.mimeType = mimeType;
		this.stream = stream;
		this.charset = charset;
		this.base = base;
		this.location = location;
		this.con = con;
	}

	public boolean isReadable(Class<?> type, Type genericType) {
		if (!stream && location == null)
			return true; // reads null
		if (stream && InputStream.class.equals(type))
			return true;
		return mimeType == null
				|| reader.isReadable(type, genericType, mimeType, con);
	}

	public <T> T read(Class<T> type, Type genericType)
			throws QueryResultParseException, TupleQueryResultHandlerException,
			QueryEvaluationException, RepositoryException,
			TransformerConfigurationException, IOException, XMLStreamException,
			ParserConfigurationException, SAXException, TransformerException {
		if (location == null && !stream)
			return null;
		if (stream && InputStream.class.equals(type))
			return type.cast(getInputStream());
		return (T) (reader.readFrom(type, genericType, mimeType,
				getInputStream(), charset, base, location, con));
	}

	protected abstract InputStream getInputStream() throws IOException;

}