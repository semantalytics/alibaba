package org.openrdf.repository.object.codegen;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import junit.framework.TestCase;

import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.openrdf.store.StoreConfigException;
import org.openrdf.store.StoreException;

public abstract class CodeGenTestCase extends TestCase {

	/** Directory used to store files generated by this test case. */
	protected static File targetDir;

	protected ObjectRepositoryConfig converter;

	/**
	 * Setup the test case.
	 * 
	 * @throws Exception
	 */
	@Override
	protected void setUp() throws Exception {
		if (targetDir == null) {
			targetDir = File.createTempFile("elmo-codegen", "");
			targetDir.delete();
			targetDir = new File(targetDir.getParentFile(), "elmo-codegen");
			targetDir.mkdir();
		}
		converter = createConventer();
	}

	protected ObjectRepositoryConfig createConventer() {
		ObjectRepositoryConfig converter = new ObjectRepositoryConfig();
		converter.setImportJarOntologies(false);
		return converter;
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
	protected int countClasses(File jar, String prefix, String suffix) throws IOException {
		int count = 0;
		JarFile file = new JarFile(jar);
		Enumeration<JarEntry> entries = file.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			if (name.startsWith(prefix) && name.endsWith(suffix) && !name.contains("-")) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Creates a new File object in the <code>targetDir</code> folder.
	 * 
	 * @param name
	 * @return
	 * @throws StoreConfigException
	 * @throws StoreException 
	 */
	protected File createJar(String filename) throws StoreConfigException, StoreException {
		File file = new File(targetDir, filename);
		if (file.exists()) {
			file.delete();
		}
		ObjectRepositoryFactory ofm = new ObjectRepositoryFactory();
		ObjectRepository repo = ofm.createRepository(converter, new SailRepository(new MemoryStore()));
		repo.setDataDir(targetDir);
		repo.setCodeGenJar(file);
		repo.initialize();
		return file;
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
