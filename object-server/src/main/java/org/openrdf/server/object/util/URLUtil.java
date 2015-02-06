package org.openrdf.server.object.util;

import info.aduna.net.ParsedURI;

import java.net.URISyntaxException;

public class URLUtil {

	/**
	 * This method will decode percent-encoded reserved characters in the path.
	 * However, it will not decode percent-encoded unreserved characters in the
	 * query string and fragment.
	 * 
	 * @param iri
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static String canonicalize(String iri)
			throws IllegalArgumentException {
		try {
			java.net.URI net = new java.net.URI(iri).normalize();
			String scheme = net.getScheme();
			if (scheme != null) {
				scheme = scheme.toLowerCase();
			}
			String frag = net.getRawFragment();
			if (net.isOpaque()) {
				String part = net.getSchemeSpecificPart();
				net = new java.net.URI(scheme, part, net.getFragment());
				return net.toASCIIString(); // URI
			}
			String auth = net.getAuthority();
			if (auth != null) {
				auth = auth.toLowerCase();
				if (auth.endsWith(":")) {
					auth = auth.substring(0, auth.length() - 1);
				} else if ("http".equals(scheme) && auth.endsWith(":80")) {
					auth = auth.substring(0, auth.length() - 3);
				} else if ("https".equals(scheme) && auth.endsWith(":443")) {
					auth = auth.substring(0, auth.length() - 4);
				}
			}
			String qs = net.getRawQuery();
			String path = net.getPath();
			if (path == null || "".equals(path)) {
				path = "/";
			} else {
				while (path.startsWith("/../")) {
					path = path.substring(3);
				}
			}
			net = new java.net.URI(scheme, auth, path, null, null);
			StringBuilder sb = new StringBuilder(net.toASCIIString());
			if (qs != null) {
				sb.append('?').append(qs);
			}
			if (frag != null) {
				sb.append('#').append(frag);
			}
			return sb.toString();
		} catch (URISyntaxException x) {
			throw new IllegalArgumentException(x);
		}
	}

	public static String resolve(String relative, String base)
			throws IllegalArgumentException {
		return canonicalize(resolve(new ParsedURI(base),
				new ParsedURI(relative)).toString());
	}

	/**
	 * Resolves a relative URI using this URI as the base URI.
	 */
	private static ParsedURI resolve(ParsedURI baseURI, ParsedURI relURI) {
		// This algorithm is based on the algorithm specified in chapter 5 of
		// RFC 2396: URI Generic Syntax. See http://www.ietf.org/rfc/rfc2396.txt

		// RFC, step 3:
		if (relURI.isAbsolute() || baseURI.isOpaque()) {
			return relURI;
		}

		// relURI._scheme == null

		// RFC, step 2:
		if (relURI.getAuthority() == null && relURI.getQuery() == null
				&& relURI.getPath().length() == 0) {

			// Inherit any fragment identifier from relURI
			String fragment = relURI.getFragment();

			return new ParsedURI(baseURI.getScheme(), baseURI.getAuthority(),
					baseURI.getPath(), baseURI.getQuery(), fragment);
		} else if (relURI.getAuthority() == null
				&& relURI.getPath().length() == 0) {

			// Inherit any query or fragment from relURI
			String query = relURI.getQuery();
			String fragment = relURI.getFragment();

			return new ParsedURI(baseURI.getScheme(), baseURI.getAuthority(),
					baseURI.getPath(), query, fragment);
		}

		// We can start combining the URIs
		String scheme, authority, path, query, fragment;
		boolean normalizeURI = false;

		scheme = baseURI.getScheme();
		query = relURI.getQuery();
		fragment = relURI.getFragment();

		// RFC, step 4:
		if (relURI.getAuthority() != null) {
			authority = relURI.getAuthority();
			path = relURI.getPath();
		} else {
			authority = baseURI.getAuthority();

			// RFC, step 5:
			if (relURI.getPath().startsWith("/")) {
				path = relURI.getPath();
			} else {
				// RFC, step 6:
				path = baseURI.getPath();

				if (path == null) {
					path = "/";
				} else {
					if (!path.endsWith("/")) {
						// Remove the last segment of the path. Note: if
						// lastSlashIdx is -1, the path will become empty,
						// which is fixed later.
						int lastSlashIdx = path.lastIndexOf('/');
						path = path.substring(0, lastSlashIdx + 1);
					}

					if (path.length() == 0) {
						// No path means: start at root.
						path = "/";
					}
				}

				// Append the path of the relative URI
				path += relURI.getPath();

				// Path needs to be normalized.
				normalizeURI = true;
			}
		}

		ParsedURI result = new ParsedURI(scheme, authority, path, query,
				fragment);

		if (normalizeURI) {
			result.normalize();
		}

		return result;
	}
}
