package org.openrdf.http.object.management;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openrdf.OpenRDFException;
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

	final Logger logger = LoggerFactory
			.getLogger(ObjectRepositoryManager.class);
	private final Map<String, ObjectRepository> repositories = new HashMap<String, ObjectRepository>();
	private final LocalRepositoryManager manager;
	private final CompiledObjectSchema service;

	public ObjectRepositoryManager(File dataDir) throws OpenRDFException,
			IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir);
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(watch(sys));
	}

	public ObjectRepositoryManager(File dataDir, ClassLoader cl)
			throws OpenRDFException, IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir);
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(watch(sys), cl);
	}

	public ObjectRepositoryManager(File dataDir, File libDir, ClassLoader cl)
			throws OpenRDFException, IOException {
		LocalRepositoryManager manager = RepositoryProvider
				.getRepositoryManager(dataDir);
		this.manager = manager;
		SystemRepository sys = manager.getSystemRepository();
		service = new CompiledObjectSchema(watch(sys), libDir, cl);
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
		Matcher m = HTTP_DOTALL.matcher(config.getTitle());
		config.setTitle(m.replaceAll("") + desc);
		manager.addRepositoryConfig(config);
	}

	private Repository watch(SystemRepository repo) {
		repo.addRepositoryConnectionListener(new RepositoryConnectionListenerAdapter() {
			private Map<RepositoryConnection, Boolean> changed = new WeakHashMap<RepositoryConnection, Boolean>();
			private Map<RepositoryConnection, Boolean> modified = new WeakHashMap<RepositoryConnection, Boolean>();

			public void clear(RepositoryConnection conn, Resource... contexts) {
				modified.put(conn, true);
			}

			public void remove(RepositoryConnection conn, Resource subject,
					URI predicate, Value object, Resource... contexts) {
				modified.put(conn, true);
			}

			public void add(RepositoryConnection conn, Resource subject,
					URI predicate, Value object, Resource... contexts) {
				modified.put(conn, true);
			}

			public void execute(RepositoryConnection conn, QueryLanguage ql,
					String update, String baseURI, Update operation) {
				modified.put(conn, true);
			}

			public void begin(RepositoryConnection conn) {
				if (!changed.containsKey(conn) && modified.containsKey(conn)) {
					changed.put(conn, true);
				}
			}

			public void rollback(RepositoryConnection conn) {
				modified.remove(conn);
			}

			public void commit(RepositoryConnection conn) {
				if (!changed.containsKey(conn) && modified.containsKey(conn)) {
					changed.put(conn, true);
				}
			}

			public void close(RepositoryConnection conn) {
				if (isCompiled()
						&& (changed.containsKey(conn) || modified
								.containsKey(conn))) {
					try {
						recompileSchema();
					} catch (IOException e) {
						logger.error(e.toString(), e);
					} catch (OpenRDFException e) {
						logger.error(e.toString(), e);
					} catch (RuntimeException e) {
						logger.error(e.toString(), e);
					} catch (Error e) {
						logger.error(e.toString(), e);
						throw e;
					} finally {
						changed.remove(conn);
						modified.remove(conn);
					}
				}
			}
		});
		return repo;
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

}
