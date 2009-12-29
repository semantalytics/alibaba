/*
 * Copyright (c) 2009, James Leigh All rights reserved.
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
package org.openrdf.http.object.filters;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;

/**
 * Computes the MD5 sum of this stream and throws an exception if it is wrong.
 */
public class MD5ValidatingStream extends InputStream {
	private final InputStream delegate;
	private final String md5;
	private final MessageDigest digest;
	private boolean closed;

	public MD5ValidatingStream(InputStream delegate, String md5)
			throws NoSuchAlgorithmException {
		this.delegate = delegate;
		this.md5 = md5;
		digest = MessageDigest.getInstance("MD5");
	}

	public int available() throws IOException {
		return delegate.available();
	}

	public void close() throws IOException {
		if (!closed) {
			closed = true;
			delegate.close();
			byte[] hash = Base64.encodeBase64(digest.digest());
			String contentMD5 = new String(hash, "UTF-8");
			if (md5 != null && !md5.equals(contentMD5)) {
				throw new IOException(
						"Content-MD5 header does not match message body");
			}
		}
	}

	public int read() throws IOException {
		int read = delegate.read();
		if (read != -1) {
			digest.update((byte) read);
		}
		return read;
	}

	public int read(byte[] b, int off, int len) throws IOException {
		int read = delegate.read(b, off, len);
		if (read > 0) {
			digest.update(b, off, read);
		}
		return read;
	}

	public int read(byte[] b) throws IOException {
		int read = delegate.read(b);
		if (read > 0) {
			digest.update(b, 0, read);
		}
		return read;
	}

	public String toString() {
		return delegate.toString();
	}

}