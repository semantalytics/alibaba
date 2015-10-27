package org.openrdf.http.object.management;

import java.io.File;

import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.store.blob.BlobObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JarResolver {
	private final Logger logger = LoggerFactory.getLogger(JarResolver.class);
	private final ObjectRepositoryManager manager;

	public JarResolver(ObjectRepositoryManager manager) {
		this.manager = manager;
	}

	public File resolve(String uri) {
		try {
			for (String id : manager.getRepositoryIDs()) {
				for (String prefix : manager.getRepositoryPrefixes(id)) {
					if (uri.startsWith(prefix)) {
						ObjectRepository repo = manager.getObjectRepository(id);
						BlobObject blob = repo.getBlobStore().open(uri);
						File file = blob.toFile();
						if (file != null)
							return file;
					}
				}
			}
		} catch (Exception e) {
			logger.error(e.toString(), e);
		}
		return null;
	}

}
