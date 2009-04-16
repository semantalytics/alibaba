package org.openrdf.repository.object;

import junit.framework.Test;

import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.base.ObjectRepositoryTestCase;
import org.openrdf.repository.RepositoryException;

public class AbstractBehaviourTest extends ObjectRepositoryTestCase {

	public static Test suite() throws Exception {
		return ObjectRepositoryTestCase.suite(AbstractBehaviourTest.class);
	}

	@rdf("urn:example:Concept")
	public interface Concept extends RDFObject {
		@rdf("urn:example:int")
		int getInt();
		void setInt(int value);
		int test();
		void remove();
		void setOneWay(Concept value);
		@rdf("urn:example:ortheother")
		Concept getOrTheOther();
		void setOrTheOther(Concept value);
		@rdf("urn:example:string")
		String getString();
		void setString(String value);
	}

	public static abstract class AbstractConcept implements Concept {
		public int test() {
			setString("blah");
			if ("blah".equals(getString()))
				return getInt();
			return 0;
		}
		public void setOneWay(Concept value) {
			value.setOrTheOther(this);
		}
	}

	public void testAbstractConcept() throws RepositoryException {
		Concept concept = con.addType(con.getObjectFactory().createObject(), Concept.class);
		concept.setInt(5);
		assertEquals(5, concept.test());
	}

	public void testAssignment() throws RepositoryException {
		Concept c1 = con.addType(con.getObjectFactory().createObject(), Concept.class);
		Concept c2 = con.addType(con.getObjectFactory().createObject(), Concept.class);
		c1.setOneWay(c2);
		assertEquals(c1, c2.getOrTheOther());
	}

	protected void setUp() throws Exception {
		config.addConcept(Concept.class);
		config.addBehaviour(AbstractConcept.class);
		super.setUp();
	}
}
