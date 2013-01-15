package org.openrdf.sail.optimistic;

import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.optimistic.exceptions.ConcurrencySailException;

public class OptimisticRepositoryConnection extends SailRepositoryConnection {
	private final SailConnection sailConnection;

	public OptimisticRepositoryConnection(SailRepository repository,
			SailConnection sailConnection) {
		super(repository, sailConnection);
		this.sailConnection = sailConnection;
	}

	@Override
	public void commit() throws RepositoryException {
		try {
			sailConnection.commit();
		} catch (ConcurrencySailException e) {
			throw e.getCause();
		} catch (SailException e) {
			throw new RepositoryException(e);
		}
	}

}
