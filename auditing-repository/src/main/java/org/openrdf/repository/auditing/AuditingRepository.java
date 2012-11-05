/*
 * Copyright (c) 2012 3 Round Stones Inc., Some rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution. 
 * - Neither the name of the openrdf.org nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.openrdf.repository.auditing;

import static org.openrdf.query.QueryLanguage.SPARQL;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.openrdf.OpenRDFException;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.Update;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.DelegatingRepository;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.auditing.helpers.ActivityTagFactory;
import org.openrdf.repository.contextaware.ContextAwareRepository;
import org.openrdf.repository.http.HTTPRepository;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditingRepository extends ContextAwareRepository {
	private static final String FILTER_NOT_EXISTS_ACTIVE_TRIPLES = "FILTER NOT EXISTS { GRAPH ?obsolete {\n\t\t\t"
				+ "?s ?p ?o\n\t\t\t"
				+ "FILTER (isIri(?s) && !strstarts(str(?s),str(?obsolete)) )\n\t\t\t"
				+ "FILTER ( !strstarts(str(?p),str(rdf:)) )\n\t\t\t"
				+ "FILTER ( !strstarts(str(?p),str(audit:)) )\n\t\t"
				+ "FILTER ( !strstarts(str(?p),str(prov:)) || sameTerm(?p,prov:wasGeneratedBy) )\n\t\t\t"
				+ "}}\n\t\t";
	private static final String SELECT_RECENT = "PREFIX prov:<http://www.w3.org/ns/prov#>\n"
			+ "PREFIX audit:<http://www.openrdf.org/rdf/2012/auditing#>\n"
			+ "SELECT REDUCED ?recent { ?recent a audit:RecentActivity\n\t"
			+ "OPTIONAL { ?recent prov:endedAtTime ?endedAtTime } }\n"
			+ "ORDER BY ?endedAtTime";
	private static final String TRIM_RECENT_ACTIVITY = "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
			+ "PREFIX prov:<http://www.w3.org/ns/prov#>\n"
			+ "PREFIX audit:<http://www.openrdf.org/rdf/2012/auditing#>\n"
			+ "DELETE {\n\t"
			+ "GRAPH $activity { $activity a audit:RecentActivity }\n"
			+ "} INSERT {\n\t"
			+ "GRAPH ?obsolete { ?obsolete a audit:ObsoleteActivity }\n"
			+ "} WHERE {\n\t"
			+ "$activity a audit:RecentActivity\n\t"
			+ "OPTIONAL {\n\t\t"
			+ "{\n\t\t\t"
			+ "BIND ($activity AS ?obsolete)\n\t\t"
			+ "} UNION {\n\t\t\t"
			+ "$activity prov:wasInfluencedBy ?obsolete . ?obsolete a audit:Activity\n\t\t"
			+ "}\n\t\t"
			+ FILTER_NOT_EXISTS_ACTIVE_TRIPLES
			+ "FILTER EXISTS { GRAPH ?obsolete { ?s ?p ?o } }\n\t"
			+ "}\n"
			+ "}";
	private static final String PURGE_EARLIER = "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
			+ "PREFIX prov:<http://www.w3.org/ns/prov#>\n"
			+ "PREFIX audit:<http://www.openrdf.org/rdf/2012/auditing#>\n"
			+ "DELETE {\n\t"
			+ "GRAPH ?obsolete { ?subject ?predicate ?object }\n"
			+ "} WHERE {\n\t"
			+ "?obsolete a audit:ObsoleteActivity; prov:wasGeneratedBy [prov:endedAtTime ?endedAtTime]\n\t"
			+ "FILTER (?endedAtTime < $earlier)\n\t"
			+ "FILTER NOT EXISTS { ?obsolete a audit:RecentActivity }\n\t"
			+ FILTER_NOT_EXISTS_ACTIVE_TRIPLES
			+ "GRAPH ?obsolete { ?subject ?predicate ?object }\n" + "}";
	private static final String TRIM_EARLIER = "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
			+ "PREFIX prov:<http://www.w3.org/ns/prov#>\n"
			+ "PREFIX audit:<http://www.openrdf.org/rdf/2012/auditing#>\n"
			+ "DELETE {\n\t"
			+ "?e1 audit:with ?triple . ?e2 audit:without ?triple .\n\t"
			+ "?triple rdf:subject ?s ; rdf:predicate ?p ; rdf:object ?o\n"
			+ "} WHERE {\n\t"
			+ "?e2 audit:without ?triple . ?prov prov:generated ?e1 ; prov:endedAtTime ?endedAtTime\n\t"
			+ "FILTER (?endedAtTime < $earlier)\n\t"
			+ "OPTIONAL { ?e1 audit:with ?triple }\n\t"
			+ "OPTIONAL { ?triple rdf:subject ?s ; rdf:predicate ?p ; rdf:object ?o }\n"
			+ "}";
	private static final ScheduledExecutorService executor = Executors
			.newSingleThreadScheduledExecutor(new ThreadFactory() {
				public Thread newThread(Runnable r) {
					String name = AuditingRepository.class.getSimpleName()
							+ "-" + Thread.currentThread().getName();
					Thread t = new Thread(r, name);
					t.setDaemon(true);
					return t;
				}
			});

	private final Logger logger = LoggerFactory
			.getLogger(AuditingRepository.class);
	private final ArrayDeque<URI> recent = new ArrayDeque<URI>(1024);
	private DatatypeFactory datatypeFactory;
	private Duration purgeAfter;
	private ScheduledFuture<?> puringTask;
	private int minRecent;
	private int maxRecent;
	private Boolean transactional;
	private ActivityFactory activityFactory;

	public AuditingRepository() {
		super();
	}

	public AuditingRepository(Repository delegate) {
		super(delegate);
	}

	/**
	 * The minimum about of time to wait after the prov:endedAtTime to purge
	 * obsolete activity graphs.
	 */
	public Duration getPurgeAfter() {
		return purgeAfter;
	}

	/**
	 * The minimum about of time to wait after the prov:endedAtTime to purge
	 * obsolete activity graphs.
	 */
	public void setPurgeAfter(Duration purgeAfter) {
		this.purgeAfter = purgeAfter;
	}

	/**
	 * The minimum number of recent activity graphs to flag before checking for
	 * obsolete activity graphs.
	 */
	public int getMinRecent() {
		return minRecent;
	}

	/**
	 * The minimum number of recent activity graphs to flag before checking for
	 * obsolete activity graphs.
	 */
	public void setMinRecent(int minRecent) {
		this.minRecent = minRecent;
	}

	/**
	 * The maximum number of recent activity graphs to flag before checking for
	 * obsolete activity graphs.
	 */
	public int getMaxRecent() {
		return maxRecent;
	}

	/**
	 * The maximum number of recent activity graphs to flag before checking for
	 * obsolete activity graphs.
	 */
	public void setMaxRecent(int maxRecent) {
		this.maxRecent = maxRecent;
	}

	public boolean isTransactional() {
		if (transactional != null)
			return transactional;
		Repository delegate = this;
		while (delegate instanceof DelegatingRepository) {
			if (!isTransactionSupported(delegate))
				return false;
			delegate = ((DelegatingRepository) delegate).getDelegate();
		}
		return true;
	}

	public Boolean getTransactional() {
		return transactional;
	}

	public void setTransactional(Boolean transactional) {
		this.transactional = transactional;
	}

	public ActivityFactory getActivityFactory() {
		return activityFactory;
	}

	public void setActivityFactory(ActivityFactory activityFactory) {
		this.activityFactory = activityFactory;
	}

	@Override
	public synchronized void initialize() throws RepositoryException {
		super.initialize();
		try {
			datatypeFactory = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException e) {
			throw new RepositoryException(e);
		}
		if (getActivityFactory() == null) {
			setActivityFactory(new ActivityTagFactory());
		}
		RepositoryConnection con = super.getConnection();
		try {
			recent.addAll(loadRecentActivities(con));
			if (purgeAfter != null) {
				long now = System.currentTimeMillis();
				Date next = new Date(now);
				purgeAfter.multiply(BigDecimal.valueOf(0.25)).addTo(next);
				long delay = next.getTime() - now;
				if (delay > 60000) {
					puringTask = executor.scheduleWithFixedDelay(new Runnable() {
						public void run() {
							purge(true);
						}
					}, delay, delay, TimeUnit.MILLISECONDS);
				} else {
					puringTask = null;
				}
			}
		} catch (MalformedQueryException e) {
			throw new RepositoryException(e);
		} catch (QueryEvaluationException e) {
			throw new RepositoryException(e);
		} finally {
			con.close();
		}
		trim();
		if (purgeAfter != null) {
			purge(false);
		}
	}

	@Override
	public void shutDown() throws RepositoryException {
		if (puringTask != null) {
			puringTask.cancel(false);
			puringTask.notifyAll();
			while (!puringTask.isDone()) {
				try {
					logger.info("Waiting for purging task to complete");
					puringTask.wait(10000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		super.shutDown();
	}

	@Override
	public AuditingRepositoryConnection getConnection()
			throws RepositoryException {
		AuditingRepositoryConnection con = new AuditingRepositoryConnection(
				this, getDelegate().getConnection());
		con.setIncludeInferred(isIncludeInferred());
		con.setMaxQueryTime(getMaxQueryTime());
		con.setQueryLanguage(getQueryLanguage());
		con.setBaseURI(getBaseURI());
		con.setReadContexts(getReadContexts());
		con.setAddContexts(getAddContexts());
		con.setRemoveContexts(getRemoveContexts());
		con.setArchiveContexts(getArchiveContexts());
		con.setInsertContext(getInsertContext());
		con.setActivityFactory(getActivityFactory());
		return con;
	}

	synchronized void addRecentActivities(Collection<URI> recentActivities)
			throws RepositoryException {
		recent.addAll(recentActivities);
	}

	void cleanup() {
		trim();
		if (puringTask == null && purgeAfter != null) {
			purge(false);
		}
	}

	synchronized void trim() {
		if (recent.size() >= maxRecent) {
			try {
				RepositoryConnection con = super.getConnection();
				try {
					trimRecentActivities(con);
				} finally {
					con.close();
				}
			} catch (OpenRDFException e) {
				logger.warn(e.toString(), e);
			}
		}
	}

	void purge(boolean delay) {
		long now = System.currentTimeMillis();
		Date earlier = new Date(now);
		purgeAfter.negate().addTo(earlier);
		try {
			RepositoryConnection con = super.getConnection();
			try {
				purgeObsolete(earlier, con);
			} finally {
				con.close();
			}
			long later = System.currentTimeMillis();
			if (delay && puringTask != null) {
				logger.info("Purged the obsolete activities in {} seconds", (later - now) / 1000.0);
				puringTask.wait(later - now);
			}
			if (puringTask != null && !puringTask.isCancelled()) {
				long ready = System.currentTimeMillis();
				con = super.getConnection();
				try {
					trimEarlier(earlier, con);
				} finally {
					con.close();
				}
				long done = System.currentTimeMillis();
				logger.info("Removed the old reified triples in {} seconds", (done - ready) / 1000.0);
			}
		} catch (OpenRDFException e) {
			logger.error(e.toString(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			if (puringTask != null) {
				puringTask.notifyAll();
			}
		}
	}

	private synchronized void trimRecentActivities(RepositoryConnection con)
			throws RepositoryException, MalformedQueryException,
			UpdateExecutionException {
		Set<URI> trim = new LinkedHashSet<URI>(recent.size());
		while (recent.size() > minRecent || recent.size() > maxRecent) {
			URI poll = recent.poll();
			if (poll == null)
				break;
			trim.add(poll);
		}
		try {
			Iterator<URI> iter = trim.iterator();
			while (iter.hasNext()) {
				con.setAutoCommit(false);
				Update update = con.prepareUpdate(SPARQL, TRIM_RECENT_ACTIVITY);
				update.setBinding("activity", iter.next());
				update.execute();
				con.setAutoCommit(true);
				iter.remove();
			}
		} finally {
			recent.addAll(trim);
		}
	}

	private void purgeObsolete(Date earlier, RepositoryConnection con)
			throws RepositoryException, MalformedQueryException,
			UpdateExecutionException {
		GregorianCalendar cal = new GregorianCalendar(1970, 0, 1);
		cal.setTime(earlier);
		XMLGregorianCalendar xgc = datatypeFactory.newXMLGregorianCalendar(cal);
		ValueFactory vf = con.getValueFactory();
		Update update = con.prepareUpdate(QueryLanguage.SPARQL, PURGE_EARLIER);
		update.setBinding("earlier", vf.createLiteral(xgc));
		update.execute();
	}

	private void trimEarlier(Date earlier, RepositoryConnection con)
			throws RepositoryException, MalformedQueryException,
			UpdateExecutionException {
		GregorianCalendar cal = new GregorianCalendar(1970, 0, 1);
		cal.setTime(earlier);
		XMLGregorianCalendar xgc = datatypeFactory.newXMLGregorianCalendar(cal);
		ValueFactory vf = con.getValueFactory();
		Update update = con.prepareUpdate(QueryLanguage.SPARQL, TRIM_EARLIER);
		update.setBinding("earlier", vf.createLiteral(xgc));
		update.execute();
	}

	private Collection<URI> loadRecentActivities(RepositoryConnection con)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {
		int size = maxRecent > 16 ? maxRecent : 16;
		Set<URI> recentActivities = new LinkedHashSet<URI>(size);
		TupleQuery qry = con.prepareTupleQuery(SPARQL, SELECT_RECENT);
		TupleQueryResult result = qry.evaluate();
		try {
			while (result.hasNext()) {
				Value recent = result.next().getValue("recent");
				if (recent instanceof URI) {
					recentActivities.add((URI) recent);
				}
			}
		} finally {
			result.close();
		}
		return recentActivities;
	}

	private boolean isTransactionSupported(Repository delegate) {
		return !(delegate instanceof HTTPRepository) && !(delegate instanceof SPARQLRepository);
	}

}
