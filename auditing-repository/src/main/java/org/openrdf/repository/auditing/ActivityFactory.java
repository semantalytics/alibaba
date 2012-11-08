package org.openrdf.repository.auditing;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

/**
 * Assigns the activity URI and is notified of when an activity starts and ends.
 * 
 * @author James Leigh
 * 
 */
public interface ActivityFactory {

	/**
	 * Assigns an activity URI to the given insert context. If the given insert
	 * context is null, the resulting URI upto, but not including, the first '#'
	 * character will be used as the default insert context.
	 * 
	 * @param bundle
	 *            the default insert context (a prov:Bundle)
	 * @param vf
	 * @return the prov:Activity URI for this insert context or null if no
	 *         activity
	 */
	URI createActivityURI(URI bundle, ValueFactory vf);

	/**
	 * Indicates that the given activity has started. Implementations may choose
	 * to assign the prov:startedAtTime to the activity within the given bundle.
	 * 
	 * @param activity
	 *            the prov:Activity URI created from
	 *            {@link #createActivityURI(URI, ValueFactory)}.
	 * @param bundle
	 *            the defalut insert context or the activity URI upto, but not
	 *            including, the first '#'.
	 * @param con
	 * @throws RepositoryException
	 */
	void activityStarted(URI activity, URI bundle, RepositoryConnection con)
			throws RepositoryException;

	/**
	 * Indicates that the given activity has ended. Implementations should
	 * assign the prov:endedAtTime to the activity within the given bundle.
	 * 
	 * @param activity
	 *            the prov:Activity URI created from
	 *            {@link #createActivityURI(URI, ValueFactory)}.
	 * @param bundle
	 *            the defalut insert context or the activity URI upto, but not
	 *            including, the first '#'.
	 * @param con
	 * @throws RepositoryException
	 */
	void activityEnded(URI activity, URI bundle, RepositoryConnection con)
			throws RepositoryException;
}
