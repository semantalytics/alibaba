package org.openrdf.http.object.management;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.concurrent.ManagedExecutors;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.Update;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.event.base.RepositoryConnectionListenerAdapter;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.repository.manager.RepositoryInfo;
import org.openrdf.repository.manager.RepositoryProvider;
import org.openrdf.repository.manager.SystemRepository;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.sail.config.RepositoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectRepositoryManager implements RepositoryResolver {
	private static final Pattern HTTP_DOTALL = Pattern.compile("\\s*\\bhttp.*",
			Pattern.DOTALL);
	private static final Pattern URL_PATTERN = Pattern
			.compile("https?://[a-zA-Z0-9\\-\\._~%!\\$\\&'\\(\\)\\*\\+,;=:/\\[\\]@]+/(?![a-zA-Z0-9\\-\\._~%!\\$\\&'\\(\\)\\*\\+,;=:/\\?\\#\\[\\]@])");

	private final Map<String, ObjectRepository> repositories = new HashMap<String, ObjectRepository>();
	private final LocalRepositoryManager manager;
	private final CompiledObjectSchema service;
	private final SchemaListener listener;

	public ObjectRepositoryManager(File dataDir) throws OpenRDFException,
			IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir.getCanonicalFile());
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(sys);
		listener = new SchemaListener(sys, service);
	}

	public ObjectRepositoryManager(File dataDir, ClassLoader cl)
			throws OpenRDFException, IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir.getCanonicalFile());
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(sys, cl);
		listener = new SchemaListener(sys, service);
	}

	public ObjectRepositoryManager(File dataDir, File libDir, ClassLoader cl)
			throws OpenRDFException, IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir.getCanonicalFile());
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(sys, libDir, cl);
		listener = new SchemaListener(sys, service);
	}

	public URL getLocation() throws MalformedURLException {
		return manager.getLocation();
	}

	public CompiledObjectSchema getObjectSchema() {
		return service;
	}

	public synchronized boolean isInitialized() {
		return manager.isInitialized();
	}

	public synchronized void refresh() throws RepositoryException {
		for (ObjectRepository repo : repositories.values()) {
			repo.shutDown();
		}
		repositories.clear();
		manager.refresh();
	}

	public synchronized void shutDown() throws RepositoryException {
		listener.release();
		for (ObjectRepository repo : repositories.values()) {
			repo.shutDown();
		}
		repositories.clear();
		manager.shutDown();
	}

	public boolean isCompiled() {
		return service.isCompiled();
	}

	public boolean isCompiling() {
		return service.isCompiling();
	}

	public void recompileSchema() throws IOException, OpenRDFException {
		service.recompileSchema();
	}

	public RepositoryConnection openSchemaConnection()
			throws RepositoryException {
		return manager.getSystemRepository().getConnection();
	}

	public synchronized Set<String> getRepositoryIDs()
			throws RepositoryException {
		return manager.getRepositoryIDs();
	}

	public synchronized boolean isRepositoryPresent(String repositoryID)
			throws RepositoryConfigException, RepositoryException {
		return manager.hasRepositoryConfig(repositoryID);
	}

	public synchronized Repository getRepository(String id)
			throws RepositoryConfigException, RepositoryException {
		return manager.getRepository(id);
	}

	public synchronized ObjectRepository getObjectRepository(String id)
			throws RepositoryConfigException, RepositoryException {
		if (!isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		if (repositories.containsKey(id))
			return repositories.get(id);
		File dataDir = manager.getRepositoryDir(id);
		Repository repo = manager.getRepository(id);
		ObjectRepository obj = new ObjectRepository(service);
		obj.setDelegate(repo);
		obj.setBlobStoreUrl(new File(dataDir, "blob").toURI().toASCIIString());
		repositories.put(id, obj);
		return obj;
	}

	public synchronized void addRepository(RepositoryConfig config)
			throws OpenRDFException {
		manager.addRepositoryConfig(config);
	}

	public synchronized boolean removeRepository(String id)
			throws OpenRDFException {
		return manager.removeRepository(id);
	}

	public synchronized URL getRepositoryLocation(String id)
			throws OpenRDFException {
		if (!isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		return manager.getRepositoryInfo(id).getLocation();
	}

	public synchronized String[] getRepositoryPrefixes(String id)
			throws OpenRDFException {
		if (!isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		RepositoryInfo info = manager.getRepositoryInfo(id);
		return splitURLs(info.getDescription());
	}

	public synchronized void addRepositoryPrefix(String id, String prefix)
			throws OpenRDFException {
		if (!prefix.endsWith("/"))
			throw new IllegalArgumentException(
					"Prefix must end with a slash, this does not: " + prefix);
		RepositoryInfo info = manager.getRepositoryInfo(id);
		if (info == null)
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		if (Arrays.asList(splitURLs(info.getDescription())).contains(prefix))
			return;
		RepositoryConfig config = manager.getRepositoryConfig(id);
		config.setTitle(config.getTitle() + "\n" + prefix);
		manager.addRepositoryConfig(config);
	}

	public synchronized void setRepositoryPrefixes(String id, String[] prefixes)
			throws OpenRDFException {
		if (!isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		StringBuilder desc = new StringBuilder();
		if (prefixes != null) {
			for (String prefix : prefixes) {
				if (!prefix.endsWith("/"))
					throw new IllegalArgumentException(
							"Prefix must end with a slash, this does not: "
									+ prefix);
				desc.append("\n").append(prefix);
			}
		}
		RepositoryConfig config = manager.getRepositoryConfig(id);
		String title = config.getTitle() == null ? "" : config.getTitle();
		Matcher m = HTTP_DOTALL.matcher(title);
		config.setTitle(m.replaceAll("") + desc);
		manager.addRepositoryConfig(config);
	}

	private String[] splitURLs(String desc) {
		List<String> list = new ArrayList<String>();
		Matcher m = desc != null ? URL_PATTERN.matcher(desc) : null;
		if (m != null && m.find()) {
			do {
				list.add(m.group());
			} while (m.find());
		}
		return list.toArray(new String[list.size()]);
	}

	private static final class SchemaListener extends
			RepositoryConnectionListenerAdapter implements Runnable {
		private final Logger logger = LoggerFactory
				.getLogger(ObjectRepositoryManager.class);
		private final ExecutorService executor = ManagedExecutors.getInstance()
				.newFixedThreadPool(1, "ObjectRepositorySchemaCompiler");
		private final Map<RepositoryConnection, Boolean> changed = new WeakHashMap<RepositoryConnection, Boolean>();
		private final Map<RepositoryConnection, Boolean> modified = new WeakHashMap<RepositoryConnection, Boolean>();
		private final SystemRepository sys;
		private final WeakReference<CompiledObjectSchema> ref;
		private Future<?> recompile;
		private boolean released;

		public SchemaListener(SystemRepository sys, CompiledObjectSchema service) {
			this.sys = sys;
			this.ref = new WeakReference<CompiledObjectSchema>(service);
			sys.addRepositoryConnectionListener(this);
		}

		public synchronized void clear(RepositoryConnection conn, Resource... contexts) {
			modified.put(conn, true);
		}

		public synchronized void remove(RepositoryConnection conn, Resource subject,
				URI predicate, Value object, Resource... contexts) {
			modified.put(conn, true);
		}

		public synchronized void add(RepositoryConnection conn, Resource subject,
				URI predicate, Value object, Resource... contexts) {
			modified.put(conn, true);
		}

		public synchronized void execute(RepositoryConnection conn, QueryLanguage ql,
				String update, String baseURI, Update operation) {
			modified.put(conn, true);
		}

		public synchronized void begin(RepositoryConnection conn) {
			if (!changed.containsKey(conn) && modified.containsKey(conn)) {
				changed.put(conn, true);
			}
		}

		public synchronized void rollback(RepositoryConnection conn) {
			modified.remove(conn);
		}

		public synchronized void commit(RepositoryConnection conn) {
			if (!changed.containsKey(conn) && modified.containsKey(conn)) {
				changed.put(conn, true);
			}
		}

		public void close(RepositoryConnection conn) {
			awaitRecompile(submitRecompile(conn));
		}

		public synchronized void release() {
			released = true;
			sys.removeRepositoryConnectionListener(this);
			executor.shutdown();
		}

		public void run() {
			try {
				CompiledObjectSchema service = ref.get();
				if (service == null) {
					release();
				} else if (service.isCompiled()) {
					service.recompileSchema();
				}
			} catch (IOException e) {
				logger.error(e.toString(), e);
			} catch (OpenRDFException e) {
				logger.error(e.toString(), e);
			} catch (RuntimeException e) {
				logger.error(e.toString(), e);
			} catch (Error e) {
				logger.error(e.toString(), e);
				throw e;
			}
		}

		private synchronized Future<?> getRecompile() {
			return recompile;
		}

		private synchronized Future<?> setRecompile(Future<?> recompile) {
			Future<?> previous = this.recompile;
			this.recompile = recompile;
			return previous;
		}

		private synchronized Future<?> submitRecompile(RepositoryConnection conn) {
			if (changed.containsKey(conn) || modified.containsKey(conn)) {
				try {
					CompiledObjectSchema service = ref.get();
					if (service == null) {
						release();
					} else if (!released && service.isCompiled()) {
						Future<?> next, previous;
						previous = setRecompile(next = executor.submit(this));
						service.setCompiling(true);
						if (previous != null && !previous.isDone()) {
							previous.cancel(false);
						}
						return next;
					}
				} finally {
					changed.remove(conn);
					modified.remove(conn);
				}
			}
			return null;
		}

		private void awaitRecompile(Future<?> recompile) {
			try {
				if (recompile != null) {
					if (recompile.isCancelled()) {
						awaitRecompile(getRecompile());
					} else {
						recompile.get();
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				logger.error(e.toString(), e);
			} catch (CancellationException e) {
				awaitRecompile(getRecompile());
			}
		}
	}
}
