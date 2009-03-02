package org.openrdf.repository.object.managers;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openrdf.model.URI;
import org.openrdf.model.URIFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.object.annotations.complementOf;
import org.openrdf.repository.object.annotations.equivalent;
import org.openrdf.repository.object.annotations.intercepts;
import org.openrdf.repository.object.annotations.intersectionOf;
import org.openrdf.repository.object.annotations.oneOf;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.annotations.triggeredBy;
import org.openrdf.repository.object.exceptions.ObjectStoreConfigException;
import org.openrdf.repository.object.managers.helpers.ComplexMapper;
import org.openrdf.repository.object.managers.helpers.HierarchicalRoleMapper;

public class RoleMapper {
	private URIFactory vf;

	private HierarchicalRoleMapper roleMapper = new HierarchicalRoleMapper();

	private Map<URI, List<Class<?>>> instances = new ConcurrentHashMap<URI, List<Class<?>>>(
			256);

	private ComplexMapper additional = new ComplexMapper();

	private Set<Class<?>> conceptClasses = new HashSet<Class<?>>();

	private Set<Method> triggers = new HashSet<Method>();

	public RoleMapper() {
		this(ValueFactoryImpl.getInstance());
	}

	public RoleMapper(URIFactory vf) {
		this.vf = vf;
		roleMapper.setURIFactory(vf);
	}

	public Collection<Class<?>> getConceptClasses() {
		return conceptClasses;
	}

	public Collection<Method> getTriggerMethods() {
		return triggers;
	}

	public Collection<Class<?>> findIndividualRoles(URI instance,
			Collection<Class<?>> classes) {
		List<Class<?>> list = instances.get(instance);
		if (list != null) {
			classes.addAll(list);
		}
		return classes;
	}

	public Collection<Class<?>> findRoles(URI type) {
		return additional.findAdditonalRoles(roleMapper.findRoles(type));
	}

	public Collection<Class<?>> findRoles(Collection<URI> types,
			Collection<Class<?>> roles) {
		return additional
				.findAdditonalRoles(roleMapper.findRoles(types, roles));
	}

	public Collection<Class<?>> findAdditionalRoles(Collection<Class<?>> classes) {
		return additional.findAdditonalRoles(classes);
	}

	public Collection<URI> findSubTypes(Class<?> role, Collection<URI> rdfTypes) {
		return roleMapper.findSubTypes(role, rdfTypes);
	}

	public URI findType(Class<?> concept) {
		return roleMapper.findType(concept);
	}

	public boolean isIndividualRolesPresent(URI instance) {
		return !instances.isEmpty() && instances.containsKey(instance);
	}

	public boolean isTypeRecorded(URI type) {
		return roleMapper.isTypeRecorded(type);
	}

	public void addConcept(Class<?> role) throws ObjectStoreConfigException {
		if (!role.isInterface()) {
			conceptClasses.add(role);
		}
		recordRole(role, role, null, true, true);
	}

	public void addConcept(Class<?> role, URI type)
			throws ObjectStoreConfigException {
		if (!role.isInterface()) {
			conceptClasses.add(role);
		}
		recordRole(role, role, type, true, false);
	}

	public void addBehaviour(Class<?> role) throws ObjectStoreConfigException {
		assertNotConcept(role);
		boolean hasType = false;
		for (Class<?> face : role.getInterfaces()) {
			boolean recorded = recordRole(role, face, null, false, false);
			if (recorded && hasType) {
				throw new ObjectStoreConfigException(role.getSimpleName()
						+ " can only implement one concept");
			} else {
				hasType = true;
			}
		}
		if (!hasType)
			throw new ObjectStoreConfigException(role.getSimpleName()
					+ " must implement a concept or mapped explicitly");
	}

	public void addBehaviour(Class<?> role, URI type)
			throws ObjectStoreConfigException {
		assertNotConcept(role);
		recordRole(role, null, type, false, false);
		for (Class<?> face : role.getInterfaces()) {
			if (recordRole(role, face, null, false, false))
				throw new ObjectStoreConfigException(
						role.getSimpleName()
								+ " cannot implement concept interfaces when mapped explicitly");
		}
	}

	private void assertNotConcept(Class<?> role)
			throws ObjectStoreConfigException {
		if (isAnnotationPresent(role))
			throw new ObjectStoreConfigException(role.getSimpleName()
					+ " cannot have a concept annotation");
		for (Method method : role.getDeclaredMethods()) {
			if (isAnnotationPresent(method)
					&& method.getName().startsWith("get"))
				throw new ObjectStoreConfigException(role.getSimpleName()
						+ " cannot have a property annotation");
		}
	}

	private boolean isAnnotationPresent(AnnotatedElement role)
			throws ObjectStoreConfigException {
		String pkg = rdf.class.getPackage().getName();
		for (Annotation ann : role.getAnnotations()) {
			Class<? extends Annotation> type = ann.annotationType();
			if (intercepts.class.equals(type))
				continue;
			if (pkg.equals(type.getPackage().getName()))
				return true;
		}
		return false;
	}

	private boolean recordRole(Class<?> role, Class<?> elm, URI rdfType,
			boolean concept, boolean base) throws ObjectStoreConfigException {
		boolean isRecorded = recordExplicitRoles(role, elm, rdfType, concept,
				base);
		recordAliases(role, elm, concept);
		for (Method m : role.getMethods()) {
			if (m.isAnnotationPresent(triggeredBy.class)) {
				if (m.getParameterTypes().length > 0)
					throw new ObjectStoreConfigException(
							"Trigger methods cannot have parameters in "
									+ m.getDeclaringClass().getSimpleName());
				triggers.add(m);
			}
		}
		return isRecorded;
	}

	private boolean recordExplicitRoles(Class<?> role, Class<?> elm,
			URI rdfType, boolean concept, boolean base)
			throws ObjectStoreConfigException {
		boolean defaultType = elm != null && elm.isAnnotationPresent(rdf.class);
		boolean complement = elm != null
				&& elm.isAnnotationPresent(complementOf.class);
		boolean intersec = elm != null
				&& elm.isAnnotationPresent(intersectionOf.class);
		boolean one = elm != null && elm.isAnnotationPresent(oneOf.class);
		boolean annotated = defaultType;
		annotated = annotated || complement || intersec || one;
		URI defType = elm == null ? null : findDefaultType(role, elm);
		boolean hasType = annotated;
		if (defType != null) {
			if (concept) {
				roleMapper.recordConcept(role, defType);
			} else {
				roleMapper.recordBehaviour(role, defType);
			}
			hasType = true;
		}
		if (rdfType != null) {
			if (concept) {
				roleMapper.recordConcept(role, rdfType);
			} else {
				roleMapper.recordBehaviour(role, rdfType);
			}
			hasType = true;
		}
		if (!hasType && elm != null) {
			for (Class<?> face : elm.getInterfaces()) {
				hasType |= recordRole(role, face, null, concept, false);
			}
		}
		if (!hasType && base) {
			throw new ObjectStoreConfigException(role.getSimpleName()
					+ " does not have an RDF type mapping");
		}
		if (elm != null) {
			additional.recordRole(role, elm);
			recordOneOf(role, elm);
		}
		return hasType;
	}

	private void recordOneOf(Class<?> role, Class<?> elm) {
		if (elm.isAnnotationPresent(oneOf.class)) {
			oneOf ann = elm.getAnnotation(oneOf.class);
			for (String instance : ann.value()) {
				URI uri = vf.createURI(instance);
				List<Class<?>> list = instances.get(uri);
				if (list == null) {
					list = new CopyOnWriteArrayList<Class<?>>();
					instances.put(uri, list);
				}
				list.add(role);
			}
		}
	}

	private URI findDefaultType(Class<?> role, AnnotatedElement elm) {
		if (elm.isAnnotationPresent(rdf.class)) {
			String value = elm.getAnnotation(rdf.class).value();
			if (value != null) {
				return vf.createURI(value);
			}
		}
		return null;
	}

	private void recordAliases(Class<?> role, Class<?> elm, boolean concept)
			throws ObjectStoreConfigException {
		if (elm != null && elm.isAnnotationPresent(equivalent.class)) {
			String[] uris = elm.getAnnotation(equivalent.class).value();
			for (int i = 0; i < uris.length; i++) {
				URI eqType = vf.createURI(uris[i]);
				recordExplicitRoles(role, elm, eqType, concept, false);
			}
		}
	}
}
