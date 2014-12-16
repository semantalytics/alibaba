package org.openrdf.repository.object;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.openrdf.repository.object.composition.ClassFactory;
import org.openrdf.repository.object.composition.ClassResolver;
import org.openrdf.repository.object.exceptions.ObjectStoreConfigException;
import org.openrdf.repository.object.managers.LiteralManager;
import org.openrdf.repository.object.managers.PropertyMapper;
import org.openrdf.repository.object.managers.RoleMapper;
import org.openrdf.repository.object.managers.helpers.RoleClassLoader;

public class ObjectServiceImpl implements ObjectService {
	static final Collection<File> temporary = new ArrayList<File>();

	private static void deleteOnExit(File dir) {
		synchronized (temporary) {
			if (temporary.isEmpty()) {
				Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
					public void run() {
						synchronized (temporary) {
							for (File dir : temporary) {
								deleteDir(dir);
							}
						}
					}

					private void deleteDir(File dir) {
						File[] files = dir.listFiles();
						if (files != null) {
							for (File file : files) {
								deleteDir(file);
							}
						}
						dir.delete();
					}
				}, "ObjectService." + dir.getName()));
			}
			temporary.add(dir);
		}
	}

	private final RoleMapper mapper;
	private final LiteralManager literals;
	private final ClassLoader cl;
	private final PropertyMapper pm;
	private final ClassResolver resolver;

	public ObjectServiceImpl() throws ObjectStoreConfigException {
		this(Thread.currentThread().getContextClassLoader());
	}

	public ObjectServiceImpl(ClassLoader cl) throws ObjectStoreConfigException {
		if (cl == null) {
			cl = getClass().getClassLoader();
		}
		this.cl = cl;
		this.mapper = new RoleMapper();
		new RoleClassLoader(mapper).loadRoles(cl);
		this.literals = new LiteralManager(cl);
		pm = new PropertyMapper(cl, mapper.isNamedTypePresent());
		File dir = createTempDir("classes");
		resolver = new ClassResolver();
		resolver.setPropertyMapper(pm);
		resolver.setRoleMapper(mapper);
		resolver.setClassDefiner(new ClassFactory(dir, cl));
		resolver.setBaseClassRoles(mapper.getConceptClasses());
		resolver.init();
	}

	public ObjectServiceImpl(RoleMapper mapper, LiteralManager literalManager,
			ClassLoader cl) throws ObjectStoreConfigException {
		this.mapper = mapper;
		this.literals = literalManager;
		this.cl = cl;
		pm = new PropertyMapper(cl, mapper.isNamedTypePresent());
		File dir = createTempDir("classes");
		resolver = new ClassResolver();
		resolver.setPropertyMapper(pm);
		resolver.setRoleMapper(mapper);
		resolver.setClassDefiner(new ClassFactory(dir, cl));
		resolver.setBaseClassRoles(mapper.getConceptClasses());
		resolver.init();
	}

	public ObjectFactory createObjectFactory() {
		return new ObjectFactory(mapper, pm, literals, resolver, cl);
	}

	private File createTempDir(String name) throws ObjectStoreConfigException {
		String tmpDirStr = System.getProperty("java.io.tmpdir");
		if (tmpDirStr != null) {
			File tmpDir = new File(tmpDirStr);
			if (!tmpDir.exists()) {
				tmpDir.mkdirs();
			}
		}
		try {
			File tmp = File.createTempFile(name, "");
			tmp.delete();
			if (tmp.mkdir()) {
				deleteOnExit(tmp);
			}
			return tmp;
		} catch (IOException e) {
			throw new ObjectStoreConfigException(e);
		}
	}

}
