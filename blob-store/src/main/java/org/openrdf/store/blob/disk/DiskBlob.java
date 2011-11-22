/*
 * Copyright (c) 2011, 3 Round Stones Inc. Some rights reserved.
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
package org.openrdf.store.blob.disk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.openrdf.store.blob.BlobObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskBlob extends BlobObject implements DiskListener {
	private static final int MAX_HISTORY = 1000;

	private interface Closure<V> {
		V call(String name, long length, String iri);
	};

	private final Logger logger = LoggerFactory.getLogger(DiskBlob.class);
	private final DiskBlobVersion disk;
	private final String uri;
	private final File dir;
	private String readFileName;
	private String writeFileName;
	private boolean readCompressed;
	private boolean writeCompressed;
	private File readFile;
	private File writeFile;
	private String version;
	/** listening for changes by other transactions */
	private boolean open;
	/** readFile and writeFile have been initialised */
	private boolean initialised;
	/** Blob was changed and committed by another transaction */
	private volatile boolean changed;
	/** uncommitted delete of readFile */
	private boolean deleted;
	/** uncommitted version in writeFile */
	private boolean written;
	/** length of uncompressed writeFile */
	private long length;

	protected DiskBlob(DiskBlobVersion disk, String uri) {
		super(uri);
		assert disk != null;
		assert uri != null;
		this.disk = disk;
		this.uri = uri;
		this.dir = new File(disk.getDirectory(), safe(uri));
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DiskBlob other = (DiskBlob) obj;
		if (!uri.equals(other.uri))
			return false;
		if (!disk.equals(other.disk))
			return false;
		return true;
	}

	public synchronized String getCommittedVersion() throws IOException {
		init(false);
		return version;
	}

	public synchronized String[] getRecentVersions() throws IOException {
		init(false);
		final LinkedList<String> history = new LinkedList<String>();
		eachVersion(new Closure<Void>() {
			public Void call(String name, long length, String iri) {
				history.addFirst(iri);
				if (history.size() > MAX_HISTORY) {
					history.removeLast();
				}
				return null;
			}
		});
		return history.toArray(new String[history.size()]);
	}

	public synchronized boolean delete() {
		try {
			init(true);
		} catch (IOException e) {
			logger.error(e.toString(), e);
			return false;
		}
		Lock read = disk.readLock();
		try {
			read.lock();
			deleted = readFile != null && readFile.exists()
					&& readFile.getParentFile().canWrite();
			length = 0;
			if (written) {
				written = false;
				return writeFile.delete();
			} else {
				return deleted;
			}
		} finally {
			read.unlock();
		}
	}

	public synchronized long getLength() throws IOException {
		init(false);
		if (deleted)
			return 0;
		return length;
	}

	public synchronized long getLastModified() {
		try {
			init(false);
		} catch (IOException e) {
			logger.error(e.toString(), e);
			return 0;
		}
		if (deleted)
			return 0;
		if (written)
			return writeFile.lastModified();
		if (readFile == null)
			return 0;
		Lock read = disk.readLock();
		try {
			read.lock();
			return readFile.lastModified();
		} finally {
			read.unlock();
		}
	}

	public synchronized InputStream openInputStream() throws IOException {
		init(false);
		if (deleted)
			return null;
		if (written && writeCompressed)
			return new GZIPInputStream(new FileInputStream(writeFile));
		if (written)
			return new FileInputStream(writeFile);
		if (readFile == null)
			return null;
		Lock read = disk.readLock();
		try {
			read.lock();
			FileInputStream fin = new FileInputStream(readFile);
			if (readCompressed)
				return new GZIPInputStream(fin);
			return fin;
		} finally {
			read.unlock();
		}
	}

	public synchronized OutputStream openOutputStream() throws IOException {
		init(true);
		File dir = writeFile.getParentFile();
		dir.mkdirs();
		if (!dir.canWrite() || writeFile.exists() && !writeFile.canWrite())
			throw new IOException("Cannot open blob file for writting");
		OutputStream out = new FileOutputStream(writeFile);
		if (readCompressed || readFile.length() <= 512) {
			out = new GZIPOutputStream(out);
			writeCompressed = true;
		}
		return new FilterOutputStream(out) {
			private long size = 0;
			private IOException fatal;

			public void write(int b) throws IOException {
				try {
					out.write(b);
					size++;
				} catch (IOException e) {
					fatal = e;
					throw e;
				}
			}

			public void write(byte[] b, int off, int len) throws IOException {
				try {
					out.write(b, off, len);
					size += len;
				} catch (IOException e) {
					fatal = e;
					throw e;
				}
			}

			public void close() throws IOException {
				super.close();
				written(fatal == null, size);
			}
		};
	}

	public void changed(String uri) {
		changed = true;
	}

	protected synchronized boolean hasConflict() {
		return changed;
	}

	protected synchronized boolean isChangePending() {
		return deleted || written;
	}

	protected synchronized boolean sync() throws IOException {
		if (!open)
			return false;
		try {
			String iri = disk.getVersion();
			if (deleted) {
				appendIndexFile(null, 0, iri);
				version = iri;
				return true;
			} else if (written) {
				if (writeCompressed && writeFile.length() >= length / 2) {
					uncompress(writeFile);
					writeCompressed = false;
				}
				appendIndexFile(writeFile, length, iri);
				readFile = writeFile;
				readCompressed = length > readFile.length();
				version = iri;
				return true;
			}
			return false;
		} finally {
			if (open) {
				disk.unwatch(uri, this);
				open = false;
				changed = false;
				written = false;
				deleted = false;
			}
		}
	}

	protected synchronized void abort() {
		if (open) {
			disk.unwatch(uri, this);
			open = false;
			changed = false;
			deleted = false;
			if (written) {
				written = false;
				writeFile.delete();
			}
		}
	}

	protected synchronized boolean erase() throws IOException {
		final String erasing = disk.getVersion();
		final AtomicBoolean erased = new AtomicBoolean(false);
		final File rest = new File(dir, getIndexFileName(disk.getVersion()
				.hashCode()));
		final PrintWriter writer = new PrintWriter(new FileWriter(rest));
		try {
			eachVersion(new Closure<Void>() {
				public Void call(String name, long length, String iri) {
					if (iri.equals(erasing) && name.length() > 0) {
						File file = new File(dir, name);
						erased.set(file.delete());
						File d = file.getParentFile();
						if (d.list().length == 0) {
							d.delete();
						}
						if (d.getParentFile().list().length == 0) {
							d.getParentFile().delete();
						}
					} else if (iri.equals(erasing)) {
						erased.set(true);
					} else {
						writer.print(name);
						writer.print(' ');
						writer.print(Long.toString(length));
						writer.print(' ');
						writer.println(iri);
					}
					return null;
				}
			});
		} finally {
			writer.close();
		}
		if (erased.get()) {
			File index = new File(dir, getIndexFileName(null));
			index.delete();
			if (rest.length() > 0) {
				rest.renameTo(index);
			} else {
				rest.delete();
				File parent = dir;
				while (parent.list().length == 0 && parent.delete()) {
					parent = parent.getParentFile();
				}
			}
			return true;
		}
		return false;
	}

	private void uncompress(File file) throws IOException {
		File dir = file.getParentFile();
		File gz = new File(dir, file.getName() + ".gz");
		if (!file.renameTo(gz))
			throw new IOException("Cannot rename " + file);
		try {
			InputStream in = new GZIPInputStream(new FileInputStream(gz));
			try {
				OutputStream out = new FileOutputStream(writeFile);
				try {
					int read;
					byte[] buf = new byte[512];
					while ((read = in.read(buf)) >= 0) {
						out.write(buf, 0, read);
					}
				} finally {
					out.close();
				}
			} finally {
				in.close();
			}
		} finally {
			gz.delete();
		}
	}

	private synchronized void written(boolean success, long size) {
		if (success) {
			written = true;
			deleted = false;
			length = size;
		} else {
			written = false;
			writeFile.delete();
		}
	}

	private void init(boolean write) throws IOException {
		if (disk.isClosed()) {
			if (write)
				throw new IllegalStateException(
						"Transaction has already completed");
		} else {
			if (!open) {
				open = true;
				disk.watch(uri, this);
			}
		}
		if (!initialised) {
			initialised = true;
			Lock readLock = disk.readLock();
			try {
				readLock.lock();
				initReadWriteFile();
			} finally {
				readLock.unlock();
			}
		}
	}

	private void initReadWriteFile() throws IOException {
		final String current = disk.getVersion();
		version = null;
		readFileName = writeFileName = null;
		eachVersion(new Closure<Boolean>() {
			public Boolean call(String name, long l, String iri) {
				if (name.length() == 0) {
					readFileName = null;
				} else {
					readFileName = name;
				}
				version = iri;
				length = l;
				if (iri.equals(current)) {
					writeFileName = readFileName;
					return Boolean.TRUE; // break;
				}
				return null;
			}
		});
		if (readFileName == null) {
			readFile = null;
			length = 0;
			readCompressed = true;
		} else {
			readFile = new File(dir, readFileName);
			readCompressed = length > readFile.length();
		}
		if (writeFileName == null) {
			writeFileName = newWriteFileName();
			writeFile = new File(dir, writeFileName);
			writeCompressed = readCompressed || readFile.length() <= 512;
		} else {
			writeFile = new File(dir, writeFileName);
			writeCompressed = length > writeFile.length();
		}
	}

	private String newWriteFileName() throws IOException {
		final String current = disk.getVersion();
		int code = current.hashCode();
		String name;
		Boolean conflict;
		do {
			final String cname = name = getLocalName(code++);
			conflict = eachVersion(new Closure<Boolean>() {
				public Boolean call(String name, long length, String iri) {
					if (name.equals(cname) && !iri.equals(current))
						return Boolean.TRUE; // continue;
					return null;
				}
			});
		} while (conflict != null && conflict);
		return name;
	}

	private <V> V eachVersion(Closure<V> closure) throws IOException {
		Lock read = disk.readLock();
		try {
			read.lock();
			File index = new File(dir, getIndexFileName(null));
			BufferedReader reader = new BufferedReader(new FileReader(index));
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					String[] split = line.split("\\s+", 3);
					V ret = closure.call(split[0], Long.valueOf(split[1]),
							split[2]);
					if (ret != null)
						return ret;
				}
			} finally {
				reader.close();
			}
		} catch (FileNotFoundException e) {
			// same as empty file
		} finally {
			read.unlock();
		}
		return null;
	}

	private void appendIndexFile(File file, long length, String iri)
			throws IOException {
		File index = new File(dir, getIndexFileName(null));
		PrintWriter writer = new PrintWriter(new FileWriter(index, true));
		try {
			if (file != null) {
				String jpath = dir.getAbsolutePath();
				String path = file.getAbsolutePath();
				if (path.startsWith(jpath)
						&& path.charAt(jpath.length()) == File.separatorChar) {
					path = path.substring(jpath.length() + 1);
				} else {
					throw new AssertionError("Invalid blob entry path: " + path);
				}
				writer.print(path);
			}
			writer.print(' ');
			writer.print(Long.toString(length));
			writer.print(' ');
			writer.println(iri);
		} finally {
			writer.close();
		}
	}

	private String safe(String path) {
		if (path == null)
			return "";
		path = path.replace('/', File.separatorChar);
		path = path.replace('\\', File.separatorChar);
		path = path.replace(':', File.separatorChar);
		path = path.replaceAll("[^a-zA-Z0-9\\-./\\\\]", "_");
		return path.toLowerCase();
	}

	private String getIndexFileName(Integer code) {
		String name = Integer.toHexString(uri.hashCode());
		if (code == null)
			return "index$" + name;
		return "index$" + name + '-' + Integer.toHexString(code);
	}

	private String getLocalName(int code) {
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toHexString(code));
		while (sb.length() < 8) {
			sb.insert(0, '0');
		}
		sb.insert(4, File.separatorChar);
		sb.append(File.separatorChar);
		sb.append('$');
		sb.append(Integer.toHexString(uri.hashCode()));
		return sb.toString();
	}

}
