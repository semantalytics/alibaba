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
package org.callimachusproject.fluid.producers.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import org.callimachusproject.engine.model.TermFactory;
import org.callimachusproject.fluid.FluidType;
import org.callimachusproject.fluid.Producer;
import org.callimachusproject.server.util.ChannelUtil;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.resultio.QueryResultParseException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;

/**
 * Parses text/uri-list messages.
 */
public abstract class URIListReader<URI> implements Producer<Object> {
	private Class<URI> componentType;

	public URIListReader(Class<URI> componentType) {
		assert componentType != null;
		this.componentType = componentType;
	}

	@Override
	public String toString() {
		return componentType.getName();
	}

	public boolean isReadable(FluidType mtype, ObjectConnection con) {
		Class<?> ctype = mtype.asClass();
		String mediaType = mtype.getMediaType();
		if (componentType != null) {
			if (!componentType.equals(ctype) && Object.class.equals(ctype))
				return false;
			if (mtype.isSetOrArray()) {
				Class<?> component = mtype.component().asClass();
				if (!componentType.equals(component)
						&& Object.class.equals(component))
					return false;
				if (!component.isAssignableFrom(componentType))
					return false;
			} else if (!ctype.isAssignableFrom(componentType)) {
				return false;
			}
		}
		return mediaType != null && (mediaType.startsWith("text/") || mediaType.startsWith("*/"));
	}

	public Object readFrom(FluidType mtype, ObjectConnection con,
			ReadableByteChannel in, Charset charset, String base, String location)
			throws QueryResultParseException, TupleQueryResultHandlerException,
			IOException, QueryEvaluationException, RepositoryException, URISyntaxException {
		if (charset == null) {
			charset = Charset.forName("ISO-8859-1");
		}
		BufferedReader reader = ChannelUtil.newReader(in, charset);
		try {
			if (location != null && in == null) {
				URI url;
				if (base == null) {
					url = create(con, canonicalize(location));
				} else {
					url = create(con, resolve(base, location));
				}
				return mtype.castComponent(url);
			}
			TermFactory rel = null;
			if (base != null) {
				rel = TermFactory.newInstance(base);
				if (location != null) {
					rel.base(location);
				}
			} else if (location != null) {
				rel = TermFactory.newInstance(location);
			}
			Set<URI> set = new LinkedHashSet<URI>();
			String str;
			while ((str = reader.readLine()) != null) {
				if (str.startsWith("#") || str.isEmpty())
					continue;
				URI url;
				if (rel != null) {
					url = create(con, rel.reference(str.trim()).stringValue());
				} else {
					url = create(con, canonicalize(str.trim()));
				}
				set.add(url);
			}
			return mtype.castSet(set);
		} finally {
			reader.close();
		}
	}

	protected abstract URI create(ObjectConnection con, String uri)
			throws MalformedURLException, RepositoryException;

	private String resolve(String base, String location) {
		return TermFactory.newInstance(base).reference(location).stringValue();
	}

	private String canonicalize(String uri) {
		return TermFactory.newInstance(uri).getSystemId();
	}

}
