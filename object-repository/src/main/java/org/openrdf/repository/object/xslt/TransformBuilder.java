/*
 * Copyright (c) 2010, Zepheira LLC, Some rights reserved.
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
package org.openrdf.repository.object.xslt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Helper class to run XSLT with parameters.
 * 
 * @author James Leigh
 */
public abstract class TransformBuilder {
	private final XMLOutputFactory factory = XMLOutputFactory.newInstance();
	private final DocumentFactory builder = DocumentFactory.newInstance();

	public final TransformBuilder with(String name, Object value)
			throws TransformerException {
		if (value == null)
			return this;
		if (value instanceof CharSequence)
			return with(name, (CharSequence) value);
		if (value instanceof Character)
			return with(name, (Character) value);
		if (value instanceof Boolean)
			return with(name, (Boolean) value);
		if (value instanceof Number)
			return with(name, (Number) value);
		if (value instanceof Node)
			return with(name, (Node) value);
		if (value instanceof NodeList)
			return with(name, (NodeList) value);
		if (value instanceof ReadableByteChannel)
			return with(name, (ReadableByteChannel) value);
		if (value instanceof ByteArrayOutputStream)
			return with(name, (ByteArrayOutputStream) value);
		if (value instanceof XMLEventReader)
			return with(name, (XMLEventReader) value);
		if (value instanceof InputStream)
			return with(name, (InputStream) value);
		if (value instanceof Reader)
			return with(name, (Reader) value);
		throw new TransformerException("Unsupported value type: "
				+ value.getClass().getName());
	}

	public final TransformBuilder with(String name, String value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, CharSequence value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, boolean value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, byte value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, short value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, int value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, long value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, float value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, double value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Boolean value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Byte value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Short value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Integer value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Long value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Float value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Double value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, BigInteger value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, BigDecimal value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Document value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Element value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, DocumentFragment value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Node value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, NodeList value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Number value)
			throws TransformerException {
		try {
			setParameter(name, value);
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Character value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			setParameter(name, String.valueOf(value));
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, char value)
			throws TransformerException {
		try {
			setParameter(name, String.valueOf(value));
			return this;
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Readable value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			if (value instanceof Reader)
				return with(name, (Reader) value);
			return with(name, new ReadableReader(value));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, byte[] value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			return with(name, new ByteArrayInputStream(value));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, ReadableByteChannel value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			return with(name, Channels.newInputStream(value));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, ByteArrayOutputStream value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			return with(name, value.toByteArray());
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(final String name,
			final XMLEventReader value) throws TransformerException {
		try {
			if (value == null)
				return this;
			ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
			XMLEventWriter writer = factory.createXMLEventWriter(output);
			try {
				writer.add(value);
			} finally {
				value.close();
				writer.close();
				output.close();
			}
			return with(name, output);
		} catch (IOException e) {
			throw handle(new TransformerException(e));
		} catch (XMLStreamException e) {
			throw handle(new TransformerException(e));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, InputStream value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			try {
				return with(name, builder.parse(value));
			} finally {
				value.close();
			}
		} catch (SAXException e) {
			throw handle(new TransformerException(e));
		} catch (ParserConfigurationException e) {
			throw handle(new TransformerException(e));
		} catch (IOException e) {
			throw handle(new TransformerException(e));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final TransformBuilder with(String name, Reader value)
			throws TransformerException {
		try {
			if (value == null)
				return this;
			try {
				return with(name, builder.parse(value));
			} finally {
				value.close();
			}
		} catch (SAXException e) {
			throw handle(new TransformerException(e));
		} catch (ParserConfigurationException e) {
			throw handle(new TransformerException(e));
		} catch (IOException e) {
			throw handle(new TransformerException(e));
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final String asString() throws TransformerException {
		try {
			CharSequence seq = asCharSequence();
			if (seq == null)
				return null;
			return seq.toString();
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final CharSequence asCharSequence() throws TransformerException {
		try {
			StringWriter output = new StringWriter();
			try {
				toWriter(output);
			} catch (IOException e) {
				throw handle(new TransformerException(e));
			}
			StringBuffer buffer = output.getBuffer();
			if (buffer.length() < 100 && isEmpty(buffer.toString()))
				return null;
			return buffer;
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final Readable asReadable() throws TransformerException {
		return asReader();
	}

	public final byte[] asByteArray() throws TransformerException {
		try {
			return asByteArrayOutputStream().toByteArray();
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final ReadableByteChannel asReadableByteChannel()
			throws TransformerException {
		try {
			return Channels.newChannel(asInputStream());
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final ByteArrayOutputStream asByteArrayOutputStream()
			throws TransformerException {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
			toOutputStream(output);
			if (output.size() < 200
					&& isEmpty(output.toByteArray(), output.size()))
				return null;
			return output;
		} catch (TransformerException e) {
			throw handle(e);
		} catch (IOException e) {
			throw handle(new TransformerException(e));
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public final Object asObject() throws TransformerException {
		return asDocumentFragment();
	}

	public final Node asNode() throws TransformerException {
		return asDocument();
	}

	public final Element asElement() throws TransformerException {
		return asDocument().getDocumentElement();
	}

	public DocumentFragment asDocumentFragment() throws TransformerException {
		try {
			Document doc = asDocument();
			DocumentFragment frag = doc.createDocumentFragment();
			frag.appendChild(doc.getDocumentElement());
			return frag;
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public abstract Document asDocument() throws TransformerException;

	public abstract XMLEventReader asXMLEventReader()
			throws TransformerException;

	public abstract InputStream asInputStream() throws TransformerException;

	public abstract Reader asReader() throws TransformerException;

	public void toOutputStream(OutputStream out) throws IOException,
			TransformerException {
		try {
			InputStream in = asInputStream();
			try {
				int read;
				byte[] buf = new byte[1024];
				while ((read = in.read(buf)) >= 0) {
					out.write(buf, 0, read);
				}
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw handle(e);
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public void toWriter(Writer writer) throws IOException,
			TransformerException {
		try {
			Reader reader = asReader();
			try {
				int read;
				char[] cbuf = new char[1024];
				while ((read = reader.read(cbuf)) >= 0) {
					writer.write(cbuf, 0, read);
				}
			} finally {
				reader.close();
			}
		} catch (IOException e) {
			throw handle(e);
		} catch (TransformerException e) {
			throw handle(e);
		} catch (RuntimeException e) {
			throw handle(e);
		} catch (Error e) {
			throw handle(e);
		}
	}

	public abstract void close() throws TransformerException;

	protected IOException handle(IOException cause) throws TransformerException {
		try {
			close();
			return cause;
		} catch (TransformerException e) {
			e.initCause(cause);
			throw e;
		} catch (RuntimeException e) {
			e.initCause(cause);
			throw e;
		} catch (Error e) {
			e.initCause(cause);
			throw e;
		}
	}

	protected TransformerException handle(TransformerException cause)
			throws TransformerException {
		try {
			close();
			return cause;
		} catch (TransformerException e) {
			e.initCause(cause);
			throw e;
		} catch (RuntimeException e) {
			e.initCause(cause);
			throw e;
		} catch (Error e) {
			e.initCause(cause);
			throw e;
		}
	}

	protected RuntimeException handle(RuntimeException cause)
			throws TransformerException {
		try {
			close();
			return cause;
		} catch (TransformerException e) {
			e.initCause(cause);
			throw e;
		} catch (RuntimeException e) {
			e.initCause(cause);
			throw e;
		} catch (Error e) {
			e.initCause(cause);
			throw e;
		}
	}

	protected Error handle(Error cause) throws TransformerException {
		try {
			close();
			return cause;
		} catch (TransformerException e) {
			e.initCause(cause);
			throw e;
		} catch (RuntimeException e) {
			e.initCause(cause);
			throw e;
		} catch (Error e) {
			e.initCause(cause);
			throw e;
		}
	}

	protected abstract void setParameter(String name, Object value);

	private boolean isEmpty(byte[] buf, int len) {
		if (len == 0)
			return true;
		String xml = decodeXML(buf, len);
		if (xml == null)
			return false; // Don't start with < in UTF-8 or UTF-16
		return isEmpty(xml);
	}

	private boolean isEmpty(String xml) {
		if (xml == null || xml.length() < 1 || xml.trim().length() < 1)
			return true;
		if (xml.length() < 2)
			return false;
		if (xml.charAt(0) != '<' || xml.charAt(1) != '?')
			return false;
		if (xml.charAt(xml.length() - 2) != '?'
				|| xml.charAt(xml.length() - 1) != '>')
			return false;
		for (int i = 1, n = xml.length() - 2; i < n; i++) {
			if (xml.charAt(i) == '<')
				return false;
		}
		return true;
	}

	/**
	 * Decodes the stream just enough to read the &lt;?xml declaration. This
	 * method can distinguish between UTF-16, UTF-8, and EBCDIC xml files, but
	 * not UTF-32.
	 * 
	 * @return a string starting with &lt; or null
	 */
	private String decodeXML(byte[] buf, int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append((char) buf[i]);
		}
		String s = sb.toString();
		String APPFcharset = null; // 'charset' according to XML APP. F
		int byteOrderMark = 0;
		if (s.startsWith("\u00FE\u00FF")) {
			APPFcharset = "UTF-16BE";
			byteOrderMark = 2;
		} else if (s.startsWith("\u00FF\u00FE")) {
			APPFcharset = "UTF-16LE";
			byteOrderMark = 2;
		} else if (s.startsWith("\u00EF\u00BB\u00BF")) {
			APPFcharset = "UTF-8";
			byteOrderMark = 3;
		} else if (s.startsWith("\u0000<")) {
			APPFcharset = "UTF-16BE";
		} else if (s.startsWith("<\u0000")) {
			APPFcharset = "UTF-16LE";
		} else if (s.startsWith("<")) {
			APPFcharset = "US-ASCII";
		} else if (s.startsWith("\u004C\u006F\u00A7\u0094")) {
			APPFcharset = "CP037"; // EBCDIC
		} else {
			return null;
		}
		try {
			byte[] bytes = s.substring(byteOrderMark).getBytes("iso-8859-1");
			String xml = new String(bytes, APPFcharset);
			if (xml.startsWith("<"))
				return xml;
			return null;
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}
}
