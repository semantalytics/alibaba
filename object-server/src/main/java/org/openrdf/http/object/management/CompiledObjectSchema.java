package org.openrdf.http.object.management;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Model;
import org.openrdf.model.Value;
import org.openrdf.model.impl.TreeModel;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.object.ObjectFactory;
import org.openrdf.repository.object.ObjectService;
import org.openrdf.repository.object.ObjectServiceImpl;
import org.openrdf.repository.object.compiler.OWLCompiler;
import org.openrdf.repository.object.managers.helpers.DirUtil;
import org.openrdf.repository.object.vocabulary.MSG;
import org.openrdf.rio.helpers.ContextStatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompiledObjectSchema implements ObjectService {
	final Logger logger = LoggerFactory.getLogger(CompiledObjectSchema.class);
	private final AtomicInteger recompile = new AtomicInteger();
	private final Repository repository;
	private final ClassLoader cl;
	private final File libDir;
	private JarResolver resolver;
	private ObjectService service;
	private ObjectService fallback;
	private volatile boolean compiling;

	public CompiledObjectSchema(Repository repository)
			throws OpenRDFException, IOException {
		this(repository, CompiledObjectSchema.class.getClassLoader());
	}

	public CompiledObjectSchema(Repository repository,
			ClassLoader cl) throws OpenRDFException, IOException {
		this(repository, DirUtil.createTempDir("object-schema"), cl);
		DirUtil.deleteOnExit(libDir);
	}

	public CompiledObjectSchema(Repository repository,
			File libDir, ClassLoader cl) throws OpenRDFException, IOException {
		assert repository != null && libDir != null && cl != null;
		this.repository = repository;
		this.libDir = libDir;
		this.cl = cl;
		this.fallback = new ObjectServiceImpl(cl);
	}

	@Override
	public ObjectFactory createObjectFactory() {
		return getObjectService().createObjectFactory();
	}

	public JarResolver getJarResolver() {
		return resolver;
	}

	public void setJarResolver(JarResolver resolver) {
		this.resolver = resolver;
	}

	public void recompileSchema() throws IOException, OpenRDFException {
		int num = recompile.incrementAndGet();
		synchronized (libDir) {
			if (num == recompile.get()) {
				try {
					compiling = true;
					setObjectService(compileSchema(repository, libDir, cl));
				} finally {
					compiling = false;
				}
			}
		}
	}

	public synchronized boolean isCompiled() {
		return service != null;
	}

	public boolean isCompiling() {
		return compiling;
	}

	public void setCompiling(boolean compiling) {
		this.compiling = compiling;
	}

	private synchronized ObjectService getObjectService() {
		if (service == null) {
			try {
				recompileSchema();
			} catch (IOException e) {
				logger.error(e.toString(), e);
				setObjectService(fallback);
			} catch (OpenRDFException e) {
				logger.error(e.toString(), e);
				setObjectService(fallback);
			}
		}
		return service;
	}

	private synchronized void setObjectService(ObjectService service) {
		assert service != null;
		this.service = service;
	}

	private ObjectService compileSchema(Repository repo, File libDir,
			ClassLoader cl) throws IOException, OpenRDFException {
		libDir.mkdirs();
		File jar = File.createTempFile("model", ".jar", libDir);
		jar.deleteOnExit();
		Model schema = new TreeModel();
		RepositoryConnection con = repo.getConnection();
		ContextStatementCollector collector = new ContextStatementCollector(
				schema, con.getValueFactory());
		try {
			con.export(collector);
		} finally {
			con.close();
		}
		OWLCompiler converter = new OWLCompiler(resolveJarsInClassPath(schema, cl));
		converter.setNamespaces(collector.getNamespaces());
		converter.setModel(schema);
		ClassLoader child = converter.createJar(jar);
		logger.info("Compiled {} into {}", repo.getDataDir(), jar);
		return new ObjectServiceImpl(child);
	}

	private synchronized ClassLoader resolveJarsInClassPath(Model schema, ClassLoader cl) {
		if (resolver == null)
			return cl;
		List<URL> classpath = new ArrayList<URL>();
		for (Value path : schema.filter(null, MSG.CLASSPATH, null).objects()) {
			File jar = resolver.resolve(path.stringValue());
			if (jar == null) {
				logger.warn("Could not resolve JAR {}", jar);
			} else {
				try {
					// TODO read JAR's Class-Path for additional JARs to load
					classpath.add(jar.toURI().toURL());
				} catch (MalformedURLException e) {
					logger.warn("Could not resolve JAR {} {}", jar, e.getMessage());
				}
			}
		}
		return new URLClassLoader(classpath.toArray(new URL[classpath.size()]), cl);
	}

}
