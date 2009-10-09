package org.openrdf.server.metadata;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.PropertyPermission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataPolicy extends Policy {
	private static Logger logger = LoggerFactory.getLogger(MetadataPolicy.class);
	private static CodeSource source = MetadataPolicy.class
			.getProtectionDomain().getCodeSource();
	/** loaded from a writable location */
	private final PermissionCollection plugins;
	private final PermissionCollection jars;
	private final List<String> writableLocations;

	public static boolean apply(String[] readable, File... writable) {
		try {
			Policy.setPolicy(new MetadataPolicy(readable, writable));
			System.setSecurityManager(new SecurityManager());
			logger.info("Restricted file system access in effect");
			return true;
		} catch (SecurityException e) {
			// a policy must already be applied
			return false;
		}
	}

	@Override
	public PermissionCollection getPermissions(CodeSource codesource) {
		if (source == codesource || source != null && source.equals(codesource))
			return jars;
		if (codesource == null || codesource.getLocation() == null)
			return plugins;
		String location = codesource.getLocation().toExternalForm();
		if (!location.startsWith("file:"))
			return plugins;
		for (String url : writableLocations) {
			if (location.startsWith(url))
				return plugins;
		}
		return jars;
	}

	private MetadataPolicy(String[] readable, File... directories) {
		plugins = new Permissions();
		plugins.add(new PropertyPermission("*", "read"));
		plugins.add(new RuntimePermission("getenv.*"));
		plugins.add(new SocketPermission("*", "connect,resolve"));
		plugins.add(new SocketPermission("*", "accept,listen"));
		plugins.add(new ReflectPermission("suppressAccessChecks"));
		plugins.add(new RuntimePermission("accessDeclaredMembers"));
		plugins.add(new RuntimePermission("getClassLoader"));
		plugins.add(new RuntimePermission("createClassLoader"));
		plugins.add(new RuntimePermission("accessDeclaredMembers"));
		plugins.add(new RuntimePermission("getProtectionDomain"));
		plugins.add(new RuntimePermission("setContextClassLoader"));
		plugins.add(new RuntimePermission("accessClassInPackage.sun.*"));
		File home = new File(System.getProperty("user.home"));
		addReadableFile(new File(home, ".mime-types.properties"));
		addReadableFile(new File(home, ".magic.mime"));
		plugins.add(new FilePermission("/usr/share/mime/-", "read"));
		plugins.add(new FilePermission("/usr/local/share/mime/-", "read"));
		plugins.add(new FilePermission(home.getAbsolutePath() + "/.local/share/mime/-", "read"));
		plugins.add(new FilePermission("/usr/share/mimelnk/-", "read"));
		plugins.add(new FilePermission("/usr/share/file/-", "read"));
		plugins.add(new FilePermission("/etc/magic.mime", "read"));
		addClassPath(System.getProperty("jdk.home"));
		addClassPath(System.getProperty("java.home"));
		addClassPath(System.getProperty("java.endorsed.dirs"));
		addClassPath(System.getProperty("java.ext.dirs"));
		addClassPath(System.getProperty("java.class.path"));
		addClassPath(System.getProperty("sun.boot.class.path"));
		addClassPath(".");
		addClassPath(readable);
		writableLocations = new ArrayList<String>(directories.length + 1);
		addWritableDirectories(directories);
		jars = new Permissions();
		Enumeration<Permission> elements = plugins.elements();
		while (elements.hasMoreElements()) {
			jars.add(elements.nextElement());
		}
		jars.add(new RuntimePermission("shutdownHooks"));
		jars.add(new RuntimePermission("accessClassInPackage.sun.misc"));
		jars.add(new RuntimePermission("createSecurityManager"));
		addJavaPath(System.getProperty("jdk.home"));
		addJavaPath(System.getProperty("java.home"));
		addJavaPath(System.getenv("JAVA_HOME"));
		addPath(System.getProperty("java.library.path"));
		addPath(System.getenv("PATH"));
	}

	private void addReadableFile(File file) {
		plugins.add(new FilePermission(file.getAbsolutePath(), "read"));
	}

	private void addClassPath(String... paths) {
		for (String path : paths) {
			if (path == null)
				continue;
			for (String dir : path.split(File.pathSeparator)) {
				String file = new File(dir).getAbsolutePath();
				plugins.add(new FilePermission(file, "read"));
				if (new File(dir).isDirectory()) {
					file = file + File.separatorChar + "-";
					plugins.add(new FilePermission(file, "read"));
				}
			}
		}
	}

	private void addWritableDirectories(File... directories) {
		for (File dir : directories) {
			addWriteableDirectory(dir);
			try {
				writableLocations.add(url(dir));
			} catch (MalformedURLException e) {
				// skip directory
			}
		}
		try {
			File tmp = File.createTempFile("server", "tmp");
			tmp.delete();
			addWriteableDirectory(tmp.getParentFile());
			writableLocations.add(url(tmp.getParentFile()));
		} catch (IOException e) {
			// can't write to tmp
		}
	}

	private String url(File dir) throws MalformedURLException {
		return dir.toURI().toURL().toExternalForm();
	}

	private void addWriteableDirectory(File dir) {
		String path = dir.getAbsolutePath();
		plugins.add(new FilePermission(path, "read"));
		plugins.add(new FilePermission(path, "write"));
		path = path + File.separatorChar + "-";
		plugins.add(new FilePermission(path, "read"));
		plugins.add(new FilePermission(path, "write"));
		plugins.add(new FilePermission(path, "delete"));
	}

	private void addJavaPath(String path) {
		if (path != null) {
			File parent = new File(path).getParentFile();
			addPath(parent.getAbsolutePath());
		}
	}

	private void addPath(String... paths) {
		for (String path : paths) {
			if (path == null)
				continue;
			for (String dir : path.split(File.pathSeparator)) {
				String file = new File(dir).getAbsolutePath();
				jars.add(new FilePermission(file, "read"));
				file = file + File.separatorChar + "-";
				jars.add(new FilePermission(file, "read"));
				jars.add(new FilePermission(file, "execute"));
			}
		}
	}

}
