package org.openrdf.http.object.management;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.io.DirUtil;
import org.openrdf.model.Model;
import org.openrdf.model.impl.TreeModel;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.object.ObjectFactory;
import org.openrdf.repository.object.ObjectService;
import org.openrdf.repository.object.ObjectServiceImpl;
import org.openrdf.repository.object.compiler.OWLCompiler;
import org.openrdf.rio.helpers.ContextStatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompiledObjectSchema implements ObjectService {
	final Logger logger = LoggerFactory.getLogger(CompiledObjectSchema.class);
	private final AtomicInteger recompile = new AtomicInteger();
	private final Repository repository;
	private final ClassLoader cl;
	private final File libDir;
	private ObjectService service;
	private ObjectService fallback;
	private volatile boolean compiling;

	public CompiledObjectSchema(Repository repository)
			throws OpenRDFException, IOException {
		this(repository, CompiledObjectSchema.class.getClassLoader());
	}

	public CompiledObjectSchema(Repository repository,
			ClassLoader cl) throws OpenRDFException, IOException {
		assert repository != null && cl != null;
		this.repository = repository;
		this.libDir = DirUtil.createTempDir("object-schema");
		DirUtil.deleteOnExit(libDir);
		this.cl = cl;
		this.fallback = new ObjectServiceImpl(cl);
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
		OWLCompiler converter = new OWLCompiler(cl);
		Model schema = new TreeModel();
		RepositoryConnection con = repo.getConnection();
		try {
			ContextStatementCollector collector = new ContextStatementCollector(
					schema, con.getValueFactory());
			con.export(collector);
			converter.setNamespaces(collector.getNamespaces());
			converter.setModel(schema);
		} finally {
			con.close();
		}
		ClassLoader child = converter.createJar(jar);
		logger.info("Compiled {} into {}", repo.getDataDir(), jar);
		return new ObjectServiceImpl(child);
	}

}
