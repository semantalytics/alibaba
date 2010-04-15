package org.openrdf.http.object;

import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.EMPTY_SET;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.openrdf.http.object.annotations.operation;
import org.openrdf.http.object.base.MetadataServerTestCase;
import org.openrdf.http.object.util.ChannelUtil;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.managers.helpers.XMLEventQueue;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;

public class RoundTripTest extends MetadataServerTestCase {

	public static class Trip {
		@operation("bool")
		public boolean bool(boolean param) {
			return param;
		}

		@operation("boolean")
		public Boolean boolObject(Boolean param) {
			return param;
		}

		@operation("setOfBoolean")
		public Set<Boolean> boolSet(Set<Boolean> param) {
			return param;
		}

		@operation("byteArray")
		public byte[] byteArray(byte[] param) {
			return param;
		}

		@operation("setOfbyteArray")
		public Set<byte[]> byteArraySet(Set<byte[]> param) {
			return param;
		}

		@operation("byteArrayStream")
		public ByteArrayOutputStream byteArrayStream(ByteArrayOutputStream param) {
			return param;
		}

		@operation("setOfbyteArrayStream")
		public Set<ByteArrayOutputStream> byteArrayStreamSet(
				Set<ByteArrayOutputStream> param) {
			return param;
		}

		@operation("int")
		public int integ(int param) {
			return param;
		}

		@operation("integer")
		public Integer integer(Integer param) {
			return param;
		}

		@operation("setOfInteger")
		public Set<Integer> setOfInteger(Set<Integer> param) {
			return param;
		}

		@operation("bigInteger")
		public BigInteger bigInteger(BigInteger param) {
			return param;
		}

		@operation("setOfBigInteger")
		public Set<BigInteger> setOfBigInteger(Set<BigInteger> param) {
			return param;
		}

		@operation("document")
		public Document document(Document param) {
			return param;
		}

		@operation("setOfDocument")
		public Set<Document> setOfDocument(Set<Document> param) {
			return param;
		}

		@operation("documentFragment")
		public DocumentFragment documentFragment(DocumentFragment param) {
			return param;
		}

		@operation("setOfDocumentFragment")
		public Set<DocumentFragment> setOfDocumentFragment(
				Set<DocumentFragment> param) {
			return param;
		}

		@operation("element")
		public Element element(Element param) {
			return param;
		}

		@operation("setOfElement")
		public Set<Element> setOfElement(Set<Element> param) {
			return param;
		}

		@operation("map")
		public Map<String, String> map(Map<String, String> param) {
			return param;
		}

		@operation("setOfMap")
		public Set<Map<String, String>> setOfMap(Set<Map<String, String>> param) {
			return param;
		}

		@operation("mapOfIntegerToSetOfInteger")
		public Map<Integer, Set<Integer>> mapOfIntegerToSetOfInteger(
				Map<Integer, Set<Integer>> param) {
			return param;
		}

		@operation("httpMessage")
		public HttpMessage httpMessage(HttpMessage param) {
			return param;
		}

		@operation("inputStream")
		public InputStream inputStream(InputStream param) throws IOException {
			if (param == null)
				return null;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(param, out);
			return new ByteArrayInputStream(out.toByteArray());
		}

		@operation("setOfInputStream")
		public Set<InputStream> setOfInputStream(Set<InputStream> param)
				throws IOException {
			if (param == null || param.isEmpty())
				return param;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(param.iterator().next(), out);
			return singleton((InputStream) new ByteArrayInputStream(out
					.toByteArray()));
		}

		@operation("readableByteChannel")
		public ReadableByteChannel readableByteChannel(ReadableByteChannel param)
				throws IOException {
			if (param == null)
				return null;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ChannelUtil.transfer(param, out);
			return ChannelUtil.newChannel(out.toByteArray());
		}

		@operation("string")
		public String string(String param) throws IOException {
			return param;
		}

		@operation("xmlEventReader")
		public XMLEventReader xmlEventReader(XMLEventReader param)
				throws XMLStreamException {
			if (param == null)
				return null;
			try {
				XMLEventQueue queue = new XMLEventQueue();
				while (param.hasNext()) {
					queue.add(param.nextEvent());
				}
				return queue.getXMLEventReader();
			} finally {
				param.close();
			}
		}
	}

	private ObjectConnection con;
	private Trip trip;

	public void setUp() throws Exception {
		config.addConcept(Trip.class, "urn:test:Trip");
		super.setUp();
		con = repository.getConnection();
		trip = con.addDesignation(con.getObject("http://" + host + "/trip"),
				Trip.class);
	}

	public void tearDown() throws Exception {
		con.close();
		super.tearDown();
	}

	public void testBool() throws Exception {
		assertEquals(true, trip.bool(true));
		assertEquals(false, trip.bool(false));
	}

	public void testBoolean() throws Exception {
		assertEquals(Boolean.TRUE, trip.boolObject(Boolean.TRUE));
		assertEquals(Boolean.FALSE, trip.boolObject(Boolean.FALSE));
		assertEquals(null, trip.boolObject(null));
	}

	public void testBooleanSet() throws Exception {
		assertEquals(singleton(Boolean.TRUE), trip
				.boolSet(singleton(Boolean.TRUE)));
		assertEquals(singleton(Boolean.FALSE), trip
				.boolSet(singleton(Boolean.FALSE)));
		assertEquals(EMPTY_SET, trip.boolSet(EMPTY_SET));
	}

	public void testByteArray() throws Exception {
		assertEquals("byte", new String(trip.byteArray("byte".getBytes())));
		assertEquals("", new String(trip.byteArray("".getBytes())));
		assertEquals(null, trip.byteArray(null));
	}

	public void testByteArraySet() throws Exception {
		assertEquals("byte", new String(trip.byteArraySet(
				singleton("byte".getBytes())).iterator().next()));
		assertEquals("", new String(trip.byteArraySet(singleton("".getBytes()))
				.iterator().next()));
		assertEquals(EMPTY_SET, trip.byteArraySet(EMPTY_SET));
	}

	public void testByteArrayStream() throws Exception {
		ByteArrayOutputStream param = new ByteArrayOutputStream();
		param.write("byte".getBytes());
		assertEquals("byte", trip.byteArrayStream(param).toString());
		assertEquals("", trip.byteArrayStream(new ByteArrayOutputStream())
				.toString());
		assertEquals(null, trip.byteArrayStream(null));
	}

	public void testByteArrayStreamSet() throws Exception {
		ByteArrayOutputStream param = new ByteArrayOutputStream();
		param.write("byte".getBytes());
		assertEquals("[byte]", trip.byteArrayStreamSet(singleton(param))
				.toString());
		assertEquals("[]", trip.byteArrayStreamSet(
				singleton(new ByteArrayOutputStream())).toString());
		assertEquals(EMPTY_SET, trip.byteArrayStreamSet(EMPTY_SET));
	}

	public void testInt() throws Exception {
		assertEquals(1, trip.integ(1));
		assertEquals(0, trip.integ(0));
	}

	public void testInteger() throws Exception {
		assertEquals(new Integer(1), trip.integer(1));
		assertEquals(new Integer(0), trip.integer(0));
		assertEquals(null, trip.bigInteger(null));
	}

	public void testSetOfInteger() throws Exception {
		assertEquals(singleton(new Integer("1")), trip
				.setOfInteger(singleton(1)));
		assertEquals(singleton(new Integer("0")), trip
				.setOfInteger(singleton(0)));
		assertEquals(EMPTY_SET, trip.setOfInteger(EMPTY_SET));
	}

	public void testBigInteger() throws Exception {
		assertEquals(new BigInteger("1"), trip.bigInteger(new BigInteger("1")));
		assertEquals(new BigInteger("0"), trip.bigInteger(new BigInteger("0")));
		assertEquals(null, trip.bigInteger(null));
	}

	public void testSetOfBigInteger() throws Exception {
		assertEquals(singleton(new BigInteger("1")), trip
				.setOfBigInteger(singleton(new BigInteger("1"))));
		assertEquals(singleton(new BigInteger("0")), trip
				.setOfBigInteger(singleton(new BigInteger("0"))));
		assertEquals(EMPTY_SET, trip.setOfBigInteger(EMPTY_SET));
	}

	public void testDocument() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		doc.appendChild(doc.createElement("root"));
		trip.document(doc);
		assertEquals(null, trip.document(null));
	}

	public void testSetOfDocument() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		doc.appendChild(doc.createElement("root"));
		trip.setOfDocument(singleton(doc));
		assertEquals(EMPTY_SET, trip.setOfDocument(EMPTY_SET));
	}

	public void testDocumentFragment() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		DocumentFragment frag = doc.createDocumentFragment();
		frag.appendChild(doc.createElement("root"));
		trip.documentFragment(frag);
		assertEquals(null, trip.documentFragment(null));
	}

	public void testSetOfDocumentFragment() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		DocumentFragment frag = doc.createDocumentFragment();
		frag.appendChild(doc.createElement("root"));
		trip.setOfDocumentFragment(singleton(frag));
		assertEquals(EMPTY_SET, trip.setOfDocumentFragment(EMPTY_SET));
	}

	public void testElement() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		trip.element(doc.createElement("root"));
		assertEquals(null, trip.element(null));
	}

	public void testSetOfElement() throws Exception {
		DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
		Document doc = builder.newDocumentBuilder().newDocument();
		trip.setOfElement(singleton(doc.createElement("root")));
		assertEquals(EMPTY_SET, trip.setOfElement(EMPTY_SET));
	}

	public void testMap() throws Exception {
		assertEquals(singletonMap("key", "value"), trip.map(singletonMap("key",
				"value")));
		assertEquals(EMPTY_MAP, trip.map(EMPTY_MAP));
		assertEquals(null, trip.map(null));
	}

	public void testSetOfMap() throws Exception {
		assertEquals(singleton(singletonMap("key", "value")), trip
				.setOfMap(singleton(singletonMap("key", "value"))));
		assertEquals(singleton(EMPTY_MAP), trip
				.setOfMap(singleton((Map<String, String>) EMPTY_MAP)));
		assertEquals(EMPTY_SET, trip.setOfMap(EMPTY_SET));
	}

	public void testMapOfIntegerToSetOfInteger() throws Exception {
		assertEquals(singletonMap(1, singleton(2)), trip
				.mapOfIntegerToSetOfInteger(singletonMap(1, singleton(2))));
		assertEquals(EMPTY_MAP, trip.mapOfIntegerToSetOfInteger(EMPTY_MAP));
		assertEquals(null, trip.mapOfIntegerToSetOfInteger(null));
	}

	public void testHttpMessage() throws Exception {
		HttpRequest req = new BasicHttpRequest("GET", "urn:test:request");
		HttpResponse resp = new BasicHttpResponse(new ProtocolVersion("HTTP",
				1, 1), 204, "No Content");
		assertEquals(req.getRequestLine().toString(), ((HttpRequest) trip
				.httpMessage(req)).getRequestLine().toString());
		assertEquals(resp.getStatusLine().toString(), ((HttpResponse) trip
				.httpMessage(resp)).getStatusLine().toString());
		assertEquals(null, trip.httpMessage(null));
	}

	public void testInputStream() throws Exception {
		assertEquals("byte", new String(ChannelUtil.newByteArray(trip
				.inputStream(new ByteArrayInputStream("byte".getBytes())))));
		assertEquals("", new String(ChannelUtil.newByteArray(trip
				.inputStream(new ByteArrayInputStream("".getBytes())))));
		assertEquals(null, trip.inputStream(null));
	}

	public void testSetOfInputStream() throws Exception {
		assertEquals("byte", new String(ChannelUtil.newByteArray(trip
				.setOfInputStream(
						singleton((InputStream) new ByteArrayInputStream("byte"
								.getBytes()))).iterator().next())));
		assertEquals("", new String(ChannelUtil.newByteArray(trip
				.setOfInputStream(
						singleton((InputStream) new ByteArrayInputStream(""
								.getBytes()))).iterator().next())));
		assertEquals(EMPTY_SET, trip.setOfInputStream(EMPTY_SET));
	}

	public void testXMLEventReader() throws Exception {
		assertEquals(null, trip.xmlEventReader(null));
	}
}
