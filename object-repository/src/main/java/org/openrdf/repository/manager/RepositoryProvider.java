/*
 * Copyright James Leigh (c) 2009.
 *
 * Licensed under the Aduna BSD-style license.
 */
package org.openrdf.repository.manager;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfigException;

/**
 * A static access point to manage RepositoryManagers that are automatically
 * shutdown when the JVM is closed.
 * 
 * @author James Leigh
 */
public class RepositoryProvider {

	private static final String REPOSITORIES = "repositories/";

	private static class SynchronizedManager {

		private final String url;

		private RepositoryManager manager;

		public SynchronizedManager(String url) {
			this.url = url;
		}

		public synchronized RepositoryManager get()
			throws RepositoryConfigException, RepositoryException
		{
			if (manager == null) {
				RepositoryManager m = createRepositoryManager(url);
				m.initialize();
				manager = m;
			}
			return manager;
		}

		public synchronized void shutDown() {
			if (manager != null) {
				manager.shutDown();
			}
		}
	}

	static final Map<String, SynchronizedManager> managers = new HashMap<String, SynchronizedManager>();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread("RepositoryProvider-shutdownHook") {

			public void run() {
				synchronized (managers) {
					for (SynchronizedManager manager : managers.values()) {
						manager.shutDown();
					}
				}
			}
		});
	}

	/**
	 * Creates a RepositoryManager, if not already created, that will be shutdown
	 * when the JVM exits cleanly. The parameter must be a URL of the form
	 * http://host:port/path or file:///path.
	 */
	public static RepositoryManager getRepositoryManager(String url)
		throws RepositoryConfigException, RepositoryException
	{
		String uri = normalize(url);
		SynchronizedManager sync = null;
		synchronized (managers) {
			if (managers.containsKey(uri)) {
				sync = managers.get(uri);
			}
			else {
				managers.put(uri, sync = new SynchronizedManager(url));
			}
		}
		return sync.get();
	}

	/**
	 * Creates a LocalRepositoryManager, if not already created, that will be
	 * shutdown when the JVM exits cleanly.
	 */
	public static LocalRepositoryManager getRepositoryManager(File dir)
			throws RepositoryConfigException, RepositoryException
	{
		String url = dir.toURI().toASCIIString();
		return (LocalRepositoryManager) getRepositoryManager(url);
	}

	/**
	 * Returns the RepositoryManager that will be used for the given repository
	 * URL. Creates a RepositoryManager, if not already created, that will be
	 * shutdown when the JVM exits cleanly. The parameter must be a URL of the
	 * form http://host:port/path/repositories/id or file:///path/repositories/id.
	 */
	public static RepositoryManager getRepositoryManagerOfRepository(String url)
			throws RepositoryConfigException, RepositoryException
	{
		if (!url.contains(REPOSITORIES)) {
			throw new IllegalArgumentException("URL is not repository URL: "
					+ url);
		}
		int idx = url.lastIndexOf(REPOSITORIES);
		String server = url.substring(0, idx);
		if (server.endsWith("/")) {
			server = server.substring(0, server.length() - 1);
		}
		else if (server.length() == 0) {
			server = ".";
		}
		return getRepositoryManager(server);
	}

	/**
	 * Returns the Repository ID that will be passed to a RepositoryManager for the given repository
	 * URL. The parameter must be a URL of the
	 * form http://host:port/path/repositories/id or file:///path/repositories/id.
	 */
	public static String getRepositoryIdOfRepository(String url)
	{
		if (!url.contains(REPOSITORIES)) {
			throw new IllegalArgumentException("URL is not repository URL: "
					+ url);
		}
		int idx = url.lastIndexOf(REPOSITORIES);
		String id = url.substring(idx + REPOSITORIES.length());
		if (id.endsWith("/")) {
			id = id.substring(0, id.length() - 1);
		}
		return id;
	}

	/**
	 * Created a Repository, if not already created, that will be shutdown when
	 * the JVM exits cleanly. The parameter must be a URL of the form
	 * http://host:port/path/repositories/id or file:///path/repositories/id.
	 * @return Repository from a RepositoryManager or null if repository is not defined
	 */
	public static Repository getRepository(String url)
		throws RepositoryException, RepositoryConfigException
	{
		RepositoryManager manager = getRepositoryManagerOfRepository(url);
		String id = getRepositoryIdOfRepository(url);
		return manager.getRepository(id);
	}

	/**
	 * Created a new RepositoryConnection, that must be closed by the caller. The
	 * parameter must be a URL of the form http://host:port/path/repositories/id
	 * or file:///path/repositories/id.
	 */
	public static RepositoryConnection getConnection(String url)
		throws RepositoryException, RepositoryConfigException
	{
		Repository repository = getRepository(url);
		return repository.getConnection();
	}

	static RepositoryManager createRepositoryManager(String url)
		throws RepositoryConfigException
	{
		if (url.startsWith("http")) {
			return new RemoteRepositoryManager(url);
		}
		else {
			return new LocalRepositoryManager(asLocalFile(url));
		}
	}

	private static File asLocalFile(String url)
		throws RepositoryConfigException
	{
		URI uri = new File(".").toURI().resolve(url);
		return new File(uri);
	}

	private static String normalize(String url) throws IllegalArgumentException {
		try {
			URI norm = URI.create(url);
			if (!norm.isAbsolute()) {
				norm = new File(".").toURI().resolve(url);
			}
			norm = norm.normalize();
			if (norm.isOpaque())
				throw new IllegalArgumentException(
						"Repository Manager URL must not be opaque: " + url);
			String sch = norm.getScheme();
			String host = norm.getAuthority();
			String path = norm.getPath();
			String qs = norm.getRawQuery();
			String frag = norm.getRawFragment();
			if (sch != null) {
				sch = sch.toLowerCase();
			}
			if (host != null) {
				host = host.toLowerCase();
			}
			String uri = new URI(sch, host, path, null, null).toASCIIString();
			if (qs == null && frag == null)
				return uri;
			StringBuilder sb = new StringBuilder(uri);
			if (qs != null) {
				sb.append('?').append(qs);
			}
			if (frag != null) {
				sb.append('#').append(frag);
			}
			sb.append(uri);
			return sb.toString();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
