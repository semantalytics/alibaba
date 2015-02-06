package org.openrdf.server.object.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import junit.framework.TestCase;

public class TestURLUtil extends TestCase {
	public static final String GEN = ":/?#[]@";
	public static final String SUB = "!$&'()*+,;=";
	public static final String UNRESERVED = "-._~";
	public static final String DIGIT = "0123456789";
	public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
	public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public static final String BASE = "http://example.com/base";
	public static final String QUERY = "http://example.com/?query";
	public static final String FRAGMENT = "http://example.com/#fragment";

	public void testGenDelimsInPath() throws Exception {
		for (char g : ":/@".toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testSubDelimsInPath() throws Exception {
		for (char g : SUB.toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testUnreservedInPath() throws Exception {
		for (char g : UNRESERVED.toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testDigitInPath() throws Exception {
		for (char g : DIGIT.toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testUpperInPath() throws Exception {
		for (char g : UPPER.toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testLowerInPath() throws Exception {
		for (char g : LOWER.toCharArray()) {
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + g));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g)));
			assertEquals(BASE + g, URLUtil.canonicalize(BASE + encode(g).toLowerCase()));
		}
	}

	public void testGenDelimsInQuery() throws Exception {
		for (char g : GEN.toCharArray()) {
			assertEquals(QUERY + g, URLUtil.canonicalize(QUERY + g));
			assertEquals(QUERY + encode(g), URLUtil.canonicalize(QUERY + encode(g)));
		}
	}

	public void testSubDelimsInQuery() throws Exception {
		for (char g : SUB.toCharArray()) {
			assertEquals(QUERY + g, URLUtil.canonicalize(QUERY + g));
			assertEquals(QUERY + encode(g), URLUtil.canonicalize(QUERY + encode(g)));
		}
	}

	public void testGenDelimsInFragment() throws Exception {
		for (char g : ":/?[]@".toCharArray()) {
			assertEquals(FRAGMENT + g, URLUtil.canonicalize(FRAGMENT + g));
			assertEquals(FRAGMENT + encode(g), URLUtil.canonicalize(FRAGMENT + encode(g)));
		}
	}

	public void testSubDelimsInFragment() throws Exception {
		for (char g : SUB.toCharArray()) {
			assertEquals(FRAGMENT + g, URLUtil.canonicalize(FRAGMENT + g));
			assertEquals(FRAGMENT + encode(g), URLUtil.canonicalize(FRAGMENT + encode(g)));
		}
	}

	private String encode(Character chr) throws UnsupportedEncodingException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] source = Character.toString(chr).getBytes(Charset.forName("UTF-8"));
		for (byte c : source) {
			out.write('%');
			char high = Character.forDigit((c >> 4) & 0xF, 16);
			char low = Character.forDigit(c & 0xF, 16);
			out.write(Character.toUpperCase(high));
			out.write(Character.toUpperCase(low));
		}
		return new String(out.toByteArray(), "UTF-8");
	}
}
