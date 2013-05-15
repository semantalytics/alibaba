package org.openrdf.sail.optimistic;

import info.aduna.io.IOUtil;
import junit.framework.TestCase;

import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;

public class DeleteInsertTest extends TestCase {
	private Repository repo;
	private String NS = "http://example.org/";
	private RepositoryConnection con;
	private ClassLoader cl = getClass().getClassLoader();

	@Override
	public void setUp() throws Exception {
		OptimisticSail sail = new OptimisticSail(new MemoryStore());
		sail.setSnapshot(false);
		sail.setSerializable(false);
		repo = new SailRepository(sail);
		repo.initialize();
		con = repo.getConnection();
	}

	@Override
	public void tearDown() throws Exception {
		con.close();
		repo.shutDown();
	}

	public void test() throws Exception {
		String load = IOUtil.readString(cl.getResource("test/insert-data.ru"));
		con.prepareUpdate(QueryLanguage.SPARQL, load, NS).execute();
		con.setAutoCommit(false);
		String modify = IOUtil.readString(cl.getResource("test/delete-insert.ru"));
		con.prepareUpdate(QueryLanguage.SPARQL, modify, NS).execute();
		con.setAutoCommit(true);
		String ask = IOUtil.readString(cl.getResource("test/ask.rq"));
		assertTrue(con.prepareBooleanQuery(QueryLanguage.SPARQL, ask, NS).evaluate());
	}
}
