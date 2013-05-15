package org.openrdf.sail.optimistic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.openrdf.model.URI;
import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.RepositoryException;

public class NamedQueryManager {
	private OptimisticSail sail;
	private Map<URI, OptimisticNamedQuery> namedQueries = new HashMap<URI, OptimisticNamedQuery>() ;

	public NamedQueryManager(OptimisticSail sail) {
		this.sail = sail;
	}

	public synchronized NamedQuery createNamedQuery(URI uri, QueryLanguage ql,
			String queryString, String baseURI) throws RepositoryException {
		// allow existing mapping to be overwritten
		// but detach the old named query from the repository
		if (namedQueries.containsKey(uri)) {
			sail.removeSailChangedListener(namedQueries.get(uri));
		}
		OptimisticNamedQuery nq;
		nq = new OptimisticNamedQuery(uri, ql, queryString, baseURI);
		namedQueries.put(uri, nq);
		sail.addSailChangedListener(nq);
		return nq;
	}

	public synchronized NamedQuery getNamedQuery(URI uri) {
		return namedQueries.get(uri);
	}

	public synchronized URI[] getNamedQueryIDs() {
		Set<URI> uris = namedQueries.keySet();
		return uris.toArray(new URI[uris.size()]);
	}

	public synchronized void removeNamedQuery(URI uri) {
		OptimisticNamedQuery nq = namedQueries.get(uri);
		sail.removeSailChangedListener(nq);
		namedQueries.remove(uri);
	}
}
