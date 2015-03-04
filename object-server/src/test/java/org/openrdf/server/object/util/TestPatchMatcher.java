package org.openrdf.server.object.util;

import java.util.Map;

import org.openrdf.server.object.util.PathMatcher;

import junit.framework.TestCase;

public class TestPatchMatcher extends TestCase {
	private static String URI = "http://example.com/";
	private static String PATH = "http://example.com/path/";
	private static String QS = "http://example.com/?query";

	public void testIdentity() throws Exception {
		assertTrue(new PathMatcher(URI, URI.length()).matches(""));
		assertTrue(new PathMatcher(URI, URI.length()).matches("$"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("\\?|$"));
	}

	public void testQuery() throws Exception {
		assertTrue(new PathMatcher(QS, URI.length()).matches("?"));
		assertTrue(new PathMatcher(QS, URI.length()).matches("?query"));
		assertTrue(new PathMatcher(QS, URI.length()).matches("$|\\?"));
	}

	public void testPath() throws Exception {
		assertTrue(new PathMatcher(PATH, URI.length()).matches("path"));
		assertTrue(new PathMatcher(PATH, URI.length()).matches("path/"));
	}

	public void testLookBehind() throws Exception {
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=.)"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=/)"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=.*)"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=http://example.com/)"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=^http://example.com/)"));
		assertTrue(new PathMatcher(URI, URI.length()).matches("(?<=^http://[^/]{3,24}/)"));
	}

	public void testPathGroup() throws Exception {
		assertEquals("path", new PathMatcher(PATH, URI.length()).match("(path)").get("1"));
	}

	public void testIdentityGroup() throws Exception {
		assertEquals("path/", new PathMatcher(PATH, URI.length()).match(".*").get("0"));
	}

	public void testLookbehindHost() throws Exception {
		Map<String, String> m = new PathMatcher(PATH, URI.length()).match("(?<=^http://(?<host>[^/]{3,24})/)(.*)");
		assertEquals("example.com", m.get("host"));
		assertEquals("path/", m.get("0"));
	}
}
