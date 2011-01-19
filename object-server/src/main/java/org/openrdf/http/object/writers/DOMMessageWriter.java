/*
 * Copyright 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
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

import static javax.xml.transform.OutputKeys.ENCODING;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.threads.ManagedExecutors;
import org.openrdf.http.object.util.ChannelUtil;
import org.openrdf.http.object.util.ErrorReadableByteChannel;
import org.openrdf.http.object.util.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Prints DOM Node into an OutputStream.
 */
public class DOMMessageWriter implements MessageBodyWriter<Node> {
	private static Executor executor = ManagedExecutors.getWriterThreadPool();

	private static class ErrorCatcher implements ErrorListener {
		private Logger logger = LoggerFactory.getLogger(ErrorCatcher.class);
		private TransformerException fatal;

		public boolean isFatal() {
			return fatal != null;
		}

		public TransformerException getFatalError() {
			return fatal;
		}

		public void error(TransformerException exception) {
			logger.warn(exception.toString(), exception);
		}

		public void fatalError(TransformerException exception) {
			if (this.fatal == null) {
				this.fatal = exception;
			}
			logger.error(exception.toString(), exception);
		}

		public void warning(TransformerException exception) {
			logger.info(exception.toString(), exception);
		}
	}

	private TransformerFactory factory = TransformerFactory.newInstance();
	private DocumentBuilderFactory builder;

	public DOMMessageWriter() throws TransformerConfigurationException {
		builder = DocumentBuilderFactory.newInstance();
		builder.setNamespaceAware(true);
	}

	public boolean isText(MessageType mtype) {
		return true;
	}

	public long getSize(MessageType mtype, Node result, Charset charset) {
		return -1;
	}

	public boolean isWriteable(MessageType mtype) {
		String mediaType = mtype.getMimeType();
		Class<?> type = mtype.clas();
		if (!Document.class.isAssignableFrom(type)
				&& !Element.class.isAssignableFrom(type))
			return false;
		if (mediaType != null && !mediaType.startsWith("*")
				&& !mediaType.startsWith("text/")
				&& !mediaType.startsWith("application/"))
			return false;
		return true;
	}

	public String getContentType(MessageType mtype, Charset charset) {
		String mimeType = mtype.getMimeType();
		if (mimeType == null || mimeType.startsWith("*")
				|| mimeType.startsWith("application/*"))
			return "application/xml";
		if (mimeType.startsWith("text/")) {
			if (charset == null) {
				charset = Charset.defaultCharset();
			}
			if (mimeType.startsWith("text/*"))
				return "text/xml;charset=" + charset.name();
			return mimeType + ";charset=" + charset.name();
		}
		return mimeType;
	}

	public ReadableByteChannel write(final MessageType mtype,
			final Node result, final String base, final Charset charset)
			throws IOException, OpenRDFException, XMLStreamException,
			TransformerException, ParserConfigurationException {
		Pipe pipe = Pipe.open();
		final SinkChannel out = pipe.sink();
		final ErrorReadableByteChannel in = new ErrorReadableByteChannel(pipe) {
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

	public void writeTo(MessageType mtype, Node node, String base,
			Charset charset, WritableByteChannel out, int bufSize)
			throws IOException, TransformerException,
			ParserConfigurationException {
		if (charset == null) {
			charset = Charset.defaultCharset();
		}
		Source source = new DOMSource(node, base);
		Result result = new StreamResult(ChannelUtil.newOutputStream(out));
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(ENCODING, charset.name());
		ErrorCatcher listener = new ErrorCatcher();
		transformer.setErrorListener(listener);
		transformer.transform(source, result);
		if (listener.isFatal())
			throw listener.getFatalError();
	}
}
