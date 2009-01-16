package org.openrdf.repository.object;

import java.util.Collection;

import junit.framework.Test;

import org.openrdf.model.ValueFactory;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.base.RepositoryTestCase;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.object.managers.RoleMapper;

public class RoleMapperTest extends RepositoryTestCase {

	public static Test suite() throws Exception {
		return RepositoryTestCase.suite(RoleMapperTest.class);
	}

	private RoleMapper mapper;
	private ValueFactory vf;

	@rdf("urn:test:Display")
	public interface Display {}
	@rdf("urn:test:SubDisplay")
	public interface SubDisplay extends Display {}
	public static class DisplaySupport {}

	public void testSubclasses1() throws Exception {
		mapper.addConcept(Display.class);
		mapper.addConcept(SubDisplay.class);
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	public void testSubclasses2() throws Exception {
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		mapper.addConcept(Display.class);
		mapper.addConcept(SubDisplay.class);
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	public void testSubclasses3() throws Exception {
		mapper.addConcept(Display.class);
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		mapper.addConcept(SubDisplay.class);
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	public void testSubclasses4() throws Exception {
		mapper.addConcept(SubDisplay.class);
		mapper.addConcept(Display.class);
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	public void testSubclasses5() throws Exception {
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		mapper.addConcept(SubDisplay.class);
		mapper.addConcept(Display.class);
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	public void testSubclasses6() throws Exception {
		mapper.addConcept(SubDisplay.class);
		mapper.addBehaviour(DisplaySupport.class, "urn:test:Display");
		mapper.addConcept(Display.class);
		assertTrue(findRoles("urn:test:Display").contains(Display.class));
		assertTrue(findRoles("urn:test:Display").contains(DisplaySupport.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(Display.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(SubDisplay.class));
		assertTrue(findRoles("urn:test:SubDisplay").contains(DisplaySupport.class));
	}

	private Collection<Class<?>> findRoles(String uri) {
		return mapper.findRoles(vf.createURI(uri));
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		vf = repository.getValueFactory();
		ObjectRepository obj = new ObjectRepositoryFactory().createRepository(repository);
		mapper = obj.getRoleMapper();
	}
}
