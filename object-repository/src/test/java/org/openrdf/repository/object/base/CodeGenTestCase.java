package org.openrdf.repository.object.base;

import info.aduna.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import junit.framework.TestCase;

import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;

public abstract class CodeGenTestCase extends TestCase {

	/** Directory used to store files generated by this test case. */
	protected File targetDir;

	protected ObjectRepositoryConfig converter;

	/**
	 * Setup the test case.
	 * 
	 * @throws Exception
	 */
	@Override
	protected void setUp() throws Exception {
		targetDir = File.createTempFile("owl-codegen", "");
		targetDir.delete();
		targetDir = new File(targetDir, getClass().getSimpleName());
		targetDir.mkdirs();
		converter = createConventer();
		converter.setFollowImports(false);
	}

	@Override
	public void tearDown() throws Exception {
		FileUtil.deltree(targetDir.getParentFile());
	}

	protected ObjectRepositoryConfig createConventer() {
		return new ObjectRepositoryConfig();
	}

	/**
	 * Count the number of files with the given <code>suffix</code> that exist
	 * inside the specified jar file.
	 * 
	 * @param jar
	 * @param suffix
	 * @return
	 * @throws IOException
	 */
	protected int countClasses(File jar, String prefix, String suffix)
			throws IOException {
		int count = 0;
		JarFile file = new JarFile(jar);
		Enumeration<JarEntry> entries = file.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			if (name.startsWith(prefix) && name.endsWith(suffix)
					&& !name.contains("-")) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Creates a new File object in the <code>targetDir</code> folder.
	 * 
	 * @param filename
	 * @return
	 * @throws StoreConfigException
	 * @throws RepositoryException
	 */
	protected File createJar(String filename) throws Exception {
		ObjectRepositoryFactory ofm = new ObjectRepositoryFactory();
		ObjectRepository repo = ofm.getRepository(converter);
		repo.setDelegate(new SailRepository(new MemoryStore()));
		repo.setDataDir(targetDir);
		repo.initialize();
		return getConceptJar(targetDir);
	}

	protected File getConceptJar(File targetDir) {
		return new File(new File(targetDir, "lib"), "concepts1.jar");
	}

	protected File createBehaviourJar(String filename)
			throws Exception {
		ObjectRepositoryFactory ofm = new ObjectRepositoryFactory();
		ObjectRepository repo = ofm.getRepository(converter);
		repo.setDelegate(new SailRepository(new MemoryStore()));
		repo.setDataDir(targetDir);
		repo.initialize();
		return new File(new File(targetDir, "lib"), "behaviours1.jar");
	}

	protected void addRdfSource(String owl) {
		converter.addImports(find(owl));
	}

	/**
	 * Returns a resource from the classpath.
	 * 
	 * @param owl
	 * @return
	 */
	protected URL find(String owl) {
		return getClass().getResource(owl);
	}
}
