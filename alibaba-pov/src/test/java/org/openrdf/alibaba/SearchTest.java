package org.openrdf.alibaba;


import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.openrdf.alibaba.concepts.Expression;
import org.openrdf.alibaba.concepts.Format;
import org.openrdf.alibaba.concepts.Intent;
import org.openrdf.alibaba.concepts.Layout;
import org.openrdf.alibaba.concepts.LiteralDisplay;
import org.openrdf.alibaba.concepts.SearchPattern;
import org.openrdf.alibaba.concepts.TextPresentation;
import org.openrdf.alibaba.exceptions.AlibabaException;
import org.openrdf.alibaba.vocabulary.ALI;
import org.openrdf.concepts.dc.DcResource;
import org.openrdf.concepts.foaf.Person;
import org.openrdf.concepts.rdf.Seq;
import org.openrdf.concepts.rdfs.Class;
import org.openrdf.elmo.ElmoManager;
import org.openrdf.elmo.ElmoManagerFactory;
import org.openrdf.elmo.sesame.SesameManagerFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.memory.MemoryStore;

public class SearchTest extends TestCase {
	private static final String POV_DATA = "META-INF/data/alibaba-data.nt";

	private static final String SELECT_NAME_SURNAME = "PREFIX rdf:<"
			+ RDF.NAMESPACE
			+ ">\n"
			+ "PREFIX foaf:<http://xmlns.com/foaf/0.1/>\n"
			+ "SELECT ?name ?surname "
			+ "WHERE {?person rdf:type foaf:Person ; foaf:name ?name ; foaf:surname ?surname}";

	private static final String NS = "http://www.example.com/rdf/2007/";

	private Repository repository;

	private ElmoManager manager;

	@SuppressWarnings("unchecked")
	public void testTable() throws Exception {
		LiteralDisplay name = createBindingDisplay("name");
		LiteralDisplay surname = createBindingDisplay("surname");

		Seq list = manager.create(Seq.class);
		list.add(name);
		list.add(surname);
		Expression expression = manager.create(Expression.class);
		expression.setPovInSparql(SELECT_NAME_SURNAME);
		SearchPattern query = manager.create(SearchPattern.class);
		query.setPovSelectExpression(expression);
		query.setPovLayout((Layout) manager.find(new QName(ALI.NS, "table")));
		query.setPovDisplays(list);
		Person megan = manager.create(Person.class, new QName(NS, "megan"));
		megan.getFoafNames().add("Megan");
		megan.getFoafSurnames().add("Smith");
		Person kelly = manager.create(Person.class, new QName(NS, "kelly"));
		kelly.getFoafNames().add("Kelly");
		kelly.getFoafSurnames().add("Smith");
		assertEquals("name\tsurname\nMegan\tSmith\nKelly\tSmith", load(query,
				Collections.EMPTY_MAP, null));
	}

	@SuppressWarnings("unchecked")
	public void testParameters() throws Exception {
		LiteralDisplay name = createBindingDisplay("name");
		LiteralDisplay surname = createBindingDisplay("surname");

		Expression expression = manager.create(Expression.class);
		expression.setPovInSparql(SELECT_NAME_SURNAME);
		expression.getPovBindings().add(name);
		SearchPattern query = manager.create(SearchPattern.class);
		query.setPovSelectExpression(expression);
		query.setPovLayout((Layout) manager.find(new QName(ALI.NS, "table")));
		Seq list = manager.create(Seq.class);
		list.add(name);
		list.add(surname);
		query.setPovDisplays(list);
		Person megan = manager.create(Person.class, new QName(NS, "megan"));
		megan.getFoafNames().add("Megan");
		megan.getFoafSurnames().add("Smith");
		Person kelly = manager.create(Person.class, new QName(NS, "kelly"));
		kelly.setFoafTitle("\t");
		kelly.getFoafNames().add("Kelly");
		kelly.getFoafSurnames().add("Smith");
		Map parameters = Collections.singletonMap("name", "Megan");
		assertEquals("name\tsurname\nMegan\tSmith", load(query, parameters,
				null));
	}

	private LiteralDisplay createBindingDisplay(String label) {
		LiteralDisplay name = manager.create(LiteralDisplay.class);
		name.setPovFormat((Format) manager.find(new QName(ALI.NS, "none")));
		name.setPovName(label);
		((DcResource) name).setRdfsLabel(label);
		return name;
	}

	@Override
	protected void setUp() throws Exception {
		repository = new SailRepository(new MemoryStore());
		repository.initialize();
		RepositoryConnection conn = repository.getConnection();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		conn.add(cl.getResource(POV_DATA), "", RDFFormat.NTRIPLES);
		conn.close();
		ElmoManagerFactory factory = new SesameManagerFactory(repository);
		manager = factory.createElmoManager(Locale.US);
	}

	@Override
	protected void tearDown() throws Exception {
		manager.close();
		repository.shutDown();
	}

	private String load(SearchPattern spec, Map<String, String> parameters,
			String orderBy) throws AlibabaException, IOException {
		CharArrayWriter writer = new CharArrayWriter();
		Intent intention = (Intent) manager.find(ALI.GENERAL);
		TextPresentation present = (TextPresentation) manager
				.find(ALI.TEXT_PRESENTATION);
		spec.setPovPurpose(intention);
		Class type = manager.create(Class.class);
		spec.getPovRepresents().add(type);
		present.getPovSearchPatterns().add(spec);
		present.exportPresentation(intention, type, parameters, orderBy, new PrintWriter(
				writer));
		return writer.toString().trim();
	}

}
