/*
 * Copyright (c) 2010, Zepheira LLC, Some rights reserved.
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
package org.openrdf.repository.object.xslt;

import info.aduna.net.ParsedURI;

import java.io.InputStream;
import java.io.Reader;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.openrdf.repository.object.util.ObjectResolver;
import org.openrdf.repository.object.util.ObjectResolver.ObjectFactory;

/**
 * Reuse the same {@link Templates} object when {@link #newTemplates(Source)} is
 * called with a {@link StreamSource} using only a systemId.
 * 
 * @author James Leigh
 * 
 */
public class CachedTransformerFactory extends TransformerFactory {
	private final String systemId;
	private final TransformerFactory delegate;
	private final ObjectResolver<Templates> code;
	private final ObjectResolver<Source> xml;

	public CachedTransformerFactory(String base) {
		this(TransformerFactory.newInstance(), base);
	}

	public CachedTransformerFactory(final TransformerFactory delegate, String base) {
		this.delegate = delegate;
		this.systemId = base;
		ClassLoader cl = getClass().getClassLoader();
		this.xml = ObjectResolver.newInstance(cl, new ObjectFactory<Source>() {
			public String[] getContentTypes() {
				return new String[] { "application/xml",
						"application/xslt+xml", "text/xml", "text/xsl" };
			}

			public boolean isReusable() {
				return false;
			}

			public Source create(String systemId, Reader in) throws Exception {
				return new StreamSource(in, systemId);
			}

			public Source create(String systemId, InputStream in)
					throws Exception {
				return new StreamSource(in, systemId);
			}
		});
		delegate.setURIResolver(new URIResolver() {
			public Source resolve(String href, String base)
					throws TransformerException {
				try {
					return xml.resolve(resolveURI(href, base));
				} catch (Exception e) {
					throw new TransformerException(e);
				}
			}
		});
		this.code = ObjectResolver.newInstance(cl, new ObjectFactory<Templates>() {
			public String[] getContentTypes() {
				return new String[] { "application/xslt+xml", "text/xsl",
						"application/xml", "text/xml" };
			}

			public boolean isReusable() {
				return true;
			}

			public Templates create(String systemId, Reader in)
					throws Exception {
				ErrorCatcher error = new ErrorCatcher(systemId);
				delegate.setErrorListener(error);
				try {
					Source source = new StreamSource(in, systemId);
					return delegate.newTemplates(source);
				} finally {
					if (error.isFatal())
						throw error.getFatalError();
				}
			}

			public Templates create(String systemId, InputStream in)
					throws Exception {
				ErrorCatcher error = new ErrorCatcher(systemId);
				delegate.setErrorListener(error);
				try {
					Source source = new StreamSource(in, systemId);
					return delegate.newTemplates(source);
				} finally {
					if (error.isFatal())
						throw error.getFatalError();
				}
			}
		});
	}

	public Source getAssociatedStylesheet(Source source, String media,
			String title, String charset)
			throws TransformerConfigurationException {
		return delegate.getAssociatedStylesheet(source, media, title, charset);
	}

	public Object getAttribute(String name) {
		return delegate.getAttribute(name);
	}

	public ErrorListener getErrorListener() {
		return delegate.getErrorListener();
	}

	public boolean getFeature(String name) {
		return delegate.getFeature(name);
	}

	public Transformer newTransformer()
			throws TransformerConfigurationException {
		return delegate.newTransformer();
	}

	public Transformer newTransformer(Source source)
			throws TransformerConfigurationException {
		return delegate.newTransformer(source);
	}

	public void setAttribute(String name, Object value) {
		delegate.setAttribute(name, value);
	}

	public void setErrorListener(ErrorListener listener) {
		delegate.setErrorListener(listener);
	}

	public void setFeature(String name, boolean value)
			throws TransformerConfigurationException {
		delegate.setFeature(name, value);
	}

	public String toString() {
		return delegate.toString();
	}

	public URIResolver getURIResolver() {
		return delegate.getURIResolver();
	}

	public void setURIResolver(URIResolver resolver) {
		delegate.setURIResolver(resolver);
	}

	public Templates newTemplates(Source source)
			throws TransformerConfigurationException {
		if (source instanceof StreamSource) {
			StreamSource ss = (StreamSource) source;
			if (ss.getInputStream() == null && ss.getReader() == null
					&& ss.getSystemId() != null) {
				try {
					return code.resolve(ss.getSystemId());
				} catch (Exception e) {
					throw new TransformerConfigurationException(e);
				}
			}
		}
		return delegate.newTemplates(source);
	}

	private String resolveURI(String href, String base) {
		if (href != null && href.contains(":"))
			return href;
		ParsedURI abs = null;
		if (base != null && base.contains(":")) {
			abs = new ParsedURI(base);
		} else {
			abs = new ParsedURI(systemId);
			if (base != null) {
				abs = abs.resolve(base);
			}
		}
		if (href != null) {
			abs = abs.resolve(href);
		}
		return abs.toString();
	}

}
