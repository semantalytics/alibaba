package org.openrdf.repository.object;

import junit.framework.Test;

import org.openrdf.repository.object.base.ObjectRepositoryTestCase;
import org.openrdf.repository.object.concepts.Agent;
import org.openrdf.repository.object.concepts.Person;

public class FunctionalTest extends ObjectRepositoryTestCase {
	public static Test suite() throws Exception {
		return ObjectRepositoryTestCase.suite(FunctionalTest.class);
	}

	public void testGender() throws Exception {
		Agent a = con.addDesignation(con.getObjectFactory().createObject(), Agent.class);
		a.setFoafGender("male");
		Object item = con.prepareObjectQuery("SELECT DISTINCT ?item WHERE {?item ?p ?o}").evaluate().singleResult();
		assertTrue(((Agent)item).getFoafGender().equals("male"));
	}

	public void testEagerGender() throws Exception {
		con.prepareUpdate("INSERT DATA { <urn:test:agent> a <urn:foaf:Agent>; <urn:foaf:gender> 'male'}").execute();
		Agent a = con.getObject(Agent.class, "urn:test:agent");
		con.prepareUpdate("DELETE { ?agent <urn:foaf:gender> 'male'} INSERT { ?agent <urn:foaf:gender> 'female'} WHERE {?agent <urn:foaf:gender> 'male'}").execute();
		assertEquals("male", a.getFoafGender());
	}

	public void testEagerPersonGender() throws Exception {
		con.prepareUpdate("INSERT DATA { <urn:test:mary> a <urn:foaf:Person>; <urn:foaf:knows> <urn:test:anne>; <urn:foaf:gender> 'female'}").execute();
		con.prepareUpdate("INSERT DATA { <urn:test:anne> a <urn:foaf:Person>; <urn:foaf:knows> <urn:test:mary>; <urn:foaf:gender> 'female'}").execute();
		Person mary = con.getObject(Person.class, "urn:test:mary");
		Person anne = mary.getFoafKnows().iterator().next();
		con.prepareUpdate("DELETE { ?person <urn:foaf:gender> 'female'} INSERT { ?person <urn:foaf:gender> 'male'} WHERE {?person <urn:foaf:gender> 'female'}").execute();
		assertEquals("female", anne.getFoafGender());
	}

	@Override
	protected void setUp() throws Exception {
		config.addConcept(Agent.class);
		super.setUp();
	}
}
