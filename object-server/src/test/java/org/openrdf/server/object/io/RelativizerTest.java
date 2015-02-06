/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.server.object.io;


import java.net.URISyntaxException;

import junit.framework.TestCase;


public class RelativizerTest extends TestCase {

	public void testParentFile() throws URISyntaxException {
		assertRelative("../dir", "http://example.com/dir/dir/file", "http://example.com/dir/dir");
	}

	public void testRootFile() throws URISyntaxException {
		assertRelative("/dir", "http://example.com/dir/dir", "http://example.com/dir");
	}

	public void testFrag() throws URISyntaxException {
		assertRelative("#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/file?qs#frag");
	}

	public void testIdentity() throws URISyntaxException {
		assertRelative("", "http://example.com/dir/dir/file?qs", "http://example.com/dir/dir/file?qs");
	}

	public void testOpaque() throws URISyntaxException {
		assertRelative("urn:test", "http://example.com/dir/dir/file?qs#frag", "urn:test");
	}

	public void testFragment() throws URISyntaxException {
		assertRelative("#frag2", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/file?qs#frag2");
	}

	public void testQueryString() throws URISyntaxException {
		assertRelative("?qs2#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/file?qs2#frag");
	}

	public void testDirectory() throws URISyntaxException {
		assertRelative(".", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/");
	}

	public void testSameDirectory() throws URISyntaxException {
		assertRelative("file2?qs#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/file2?qs#frag");
	}

	public void testNestedDirectory() throws URISyntaxException {
		assertRelative("nested/file?qs#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir/nested/file?qs#frag");
	}

	public void testParentDirectory() throws URISyntaxException {
		assertRelative("../file?qs#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/file?qs#frag");
	}

	public void testOtherDirectory() throws URISyntaxException {
		assertRelative("../dir2/file?qs#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir/dir2/file?qs#frag");
	}

	public void testSameAuthority() throws URISyntaxException {
		assertRelative("/dir2/dir/file?qs#frag", "http://example.com/dir/dir/file?qs#frag", "http://example.com/dir2/dir/file?qs#frag");
	}

	public void testIdentityDir() throws URISyntaxException {
		assertRelative("", "http://example.com/dir/dir/", "http://example.com/dir/dir/");
	}

	public void testOpaqueDir() throws URISyntaxException {
		assertRelative("urn:test", "http://example.com/dir/dir/", "urn:test");
	}

	public void testFragmentDir() throws URISyntaxException {
		assertRelative("#frag2", "http://example.com/dir/dir/", "http://example.com/dir/dir/#frag2");
	}

	public void testQueryStringDir() throws URISyntaxException {
		assertRelative("?qs2", "http://example.com/dir/dir/", "http://example.com/dir/dir/?qs2");
	}

	public void testDirectoryDir() throws URISyntaxException {
		assertRelative("file", "http://example.com/dir/dir/", "http://example.com/dir/dir/file");
	}

	public void testSameDirectoryDir() throws URISyntaxException {
		assertRelative("file2?qs#frag", "http://example.com/dir/dir/", "http://example.com/dir/dir/file2?qs#frag");
	}

	public void testNestedDirectoryDir() throws URISyntaxException {
		assertRelative("nested/", "http://example.com/dir/dir/", "http://example.com/dir/dir/nested/");
	}

	public void testNestedDirectoryFileDir() throws URISyntaxException {
		assertRelative("nested/file?qs#frag", "http://example.com/dir/dir/", "http://example.com/dir/dir/nested/file?qs#frag");
	}

	public void testParentDirectoryDir() throws URISyntaxException {
		assertRelative("../file?qs#frag", "http://example.com/dir/dir/", "http://example.com/dir/file?qs#frag");
	}

	public void testOtherDirectoryDir() throws URISyntaxException {
		assertRelative("../dir2/", "http://example.com/dir/dir/", "http://example.com/dir/dir2/");
	}

	public void testOtherDirectoryFileDir() throws URISyntaxException {
		assertRelative("../dir2/file?qs#frag", "http://example.com/dir/dir/", "http://example.com/dir/dir2/file?qs#frag");
	}

	public void testSameAuthorityDir() throws URISyntaxException {
		assertRelative("/dir2/dir/file?qs#frag", "http://example.com/dir/dir/", "http://example.com/dir2/dir/file?qs#frag");
	}

	private void assertRelative(String relative, String base, String absolute) throws URISyntaxException {
		assertEquals(relative, new Relativizer(base).relativize(absolute));
	}

}
