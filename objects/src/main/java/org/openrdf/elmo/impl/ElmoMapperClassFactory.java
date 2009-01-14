/*
 * Copyright (c) 2007, James Leigh All rights reserved.
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
package org.openrdf.elmo.impl;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.NotFoundException;

import org.openrdf.elmo.ElmoProperty;
import org.openrdf.elmo.ElmoPropertyFactory;
import org.openrdf.elmo.Entity;
import org.openrdf.elmo.ImplementationResolver;
import org.openrdf.elmo.Mergeable;
import org.openrdf.elmo.Refreshable;
import org.openrdf.elmo.annotations.inverseOf;
import org.openrdf.elmo.annotations.rdf;
import org.openrdf.elmo.dynacode.ClassFactory;
import org.openrdf.elmo.dynacode.ClassTemplate;
import org.openrdf.elmo.dynacode.CodeBuilder;
import org.openrdf.elmo.exceptions.ElmoCompositionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Properties that have the rdf or localname annotation are replaced with
 * getters and setters that access the Sesame Repository directly.
 * 
 * @author James Leigh
 * 
 */
public class ElmoMapperClassFactory implements ImplementationResolver {
	private static final String SET_READ_ONLY = "setReadOnly";

	private static final String ELMO_PROPERTIES = "META-INF/org.openrdf.elmo.properties";

	private static final String PROPERTY_SUFFIX = "Property";

	private static final String FACTORY_SUFFIX = "Factory";

	private static final String SET_PROPERTY_DESCRIPTOR = "setPropertyDescriptor";

	private static final String SET_FIELD = "setField";

	private static final String SET_URI = "setUri";

	private static final String CREATE_ELMO_PROPERTY = "createElmoProperty";

	private static final String CLASS_PREFIX = "elmobeans.mappers.";

	private static final String BEAN_FIELD_NAME = "_$elmoBean";

	private static final String GET_ALL = "getAll";

	private static final String GET_SINGLE = "getSingle";

	private static final String SET_ALL = "setAll";

	private static final String SET_SINGLE = "setSingle";

	private static final String ADD_ALL = "addAll";

	private static final String ADD_SINGLE = "add";

	private Logger logger = LoggerFactory.getLogger(ElmoMapperClassFactory.class);

	private ClassFactory cp;

	private Class<?> propertyFactoryClass;

	private Properties properties = new Properties();

	public ElmoMapperClassFactory() {
	}

	public void setClassDefiner(ClassFactory definer) {
		this.cp = definer;
		loadProperties(definer);
	}

	@SuppressWarnings("unchecked")
	public void setElmoPropertyFactoryClass(
			Class<? extends ElmoPropertyFactory> propertyFactoryClass) {
		this.propertyFactoryClass = propertyFactoryClass;
	}

	public Method getReadMethod(Field field) throws Exception {
		if (!isMappedField(field))
			return null;
		String property = getPropertyName(field);
		String getter = "_$get_" + property;
		Class<?> declaringClass = field.getDeclaringClass();
		return findBehaviour(declaringClass).getMethod(getter);
	}

	public Method getWriteMethod(Field field) throws Exception {
		if (!isMappedField(field))
			return null;
		String property = getPropertyName(field);
		String setter = "_$set_" + property;
		Class<?> declaringClass = field.getDeclaringClass();
		return findBehaviour(declaringClass).getMethod(setter, field.getType());
	}

	public Collection<Class<?>> findImplementations(Collection<Class<?>> interfaces) {
		try {
			Set<Class<?>> faces = new HashSet<Class<?>>();
			for (Class<?> i : interfaces) {
				faces.add(i);
				faces = getInterfaces(i, faces);
			}
			List<Class<?>> mappers = new ArrayList<Class<?>>();
			for (Class<?> concept : faces) {
				if (isRdfPropertyPresent(concept)) {
					mappers.add(findBehaviour(concept));
				}
			}
			return mappers;
		} catch (ElmoCompositionException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoCompositionException(e);
		}
	}

	private void loadProperties(ClassLoader cl) {
		try {
			Enumeration<URL> resources = cl.getResources(ELMO_PROPERTIES);
			while (resources.hasMoreElements()) {
				try {
					InputStream stream = resources.nextElement().openStream();
					try {
						properties.load(stream);
					} finally {
						stream.close();
					}
				} catch (IOException e) {
					logger.warn(e.toString(), e);
				}
			}
		} catch (IOException e) {
			logger.warn(e.toString(), e);
		}
	}

	private Class<?> findBehaviour(Class<?> concept) throws Exception {
		String className = getJavaClassName(concept);
		try {
			return Class.forName(className, true, cp);
		} catch (ClassNotFoundException e1) {
			synchronized (cp) {
				try {
					return Class.forName(className, true, cp);
				} catch (ClassNotFoundException e2) {
					return implement(className, concept);
				}
			}
		}
	}

	private boolean isRdfPropertyPresent(Class<?> concept) {
		for (Method m : concept.getDeclaredMethods()) {
			if (isMappedGetter(m))
				return true;
		}
		for (Field f : concept.getDeclaredFields()) {
			if (isMappedField(f))
				return true;
		}
		return false;
	}

	private String getJavaClassName(Class<?> concept) {
		String cn = concept.getName();
		return CLASS_PREFIX + cn + "Mapper";
	}

	private Class<?> implement(String className, Class<?> concept)
			throws Exception {
		ClassTemplate cc = cp.createClassTemplate(className);
		cc.addInterface(Mergeable.class);
		cc.addInterface(Refreshable.class);
		addNewConstructor(cc);
		enhance(cc, concept);
		return cp.createClass(cc);
	}

	private Set<Class<?>> getInterfaces(Class<?> concept,
			Set<Class<?>> interfaces) throws NotFoundException {
		for (Class<?> face : concept.getInterfaces()) {
			if (!interfaces.contains(face)) {
				interfaces.add(face);
				getInterfaces(face, interfaces);
			}
		}
		Class<?> superclass = concept.getSuperclass();
		if (superclass != null) {
			interfaces.add(superclass);
			getInterfaces(superclass, interfaces);
		}
		return interfaces;
	}

	private void addNewConstructor(ClassTemplate cc) throws NotFoundException,
			CannotCompileException {
		cc.createField(Entity.class, BEAN_FIELD_NAME);
		cc.addConstructor(new Class<?>[] { Entity.class }, BEAN_FIELD_NAME + " = $1;");
	}

	private void enhance(ClassTemplate cc, Class<?> concept) throws Exception {
		for (Method method : concept.getDeclaredMethods()) {
			if (isMappedGetter(method)) {
				overrideMethod(method, cc);
			}
		}
		for (Field field : concept.getDeclaredFields()) {
			if (isMappedField(field)) {
				implementProperty(field, cc);
			}
		}
		overrideMergeMethod(cc, concept);
		overrideRefreshMethod(cc, concept);
	}

	private boolean isMappedGetter(Method method) {
		if (method.getParameterTypes().length != 0)
			return false;
		if (!method.getName().startsWith("get") && !(method.getName().startsWith("is")
				&& method.getReturnType().equals(Boolean.TYPE)))
			return false;
		if (method.isAnnotationPresent(rdf.class))
			return true;
		if (method.isAnnotationPresent(inverseOf.class))
			return true;
		if (properties.isEmpty())
			return false;
		String name = method.getDeclaringClass().getName();
		String key = name + "." + getPropertyName(method);
		return properties.containsKey(key);
	}

	private boolean isMappedField(Field field) {
		if (field.isAnnotationPresent(rdf.class))
			return true;
		if (field.isAnnotationPresent(inverseOf.class))
			return true;
		if (properties.isEmpty())
			return false;
		String name = field.getDeclaringClass().getName();
		String key = name + "#" + field.getName();
		return properties.containsKey(key);
	}

	private void overrideMergeMethod(ClassTemplate cc, Class<?> concept)
			throws Exception {
		Method merge = Mergeable.class.getMethod("merge", Object.class);
		CodeBuilder sb = cc.overrideMethod(merge);
		sb.code("if($1 instanceof ").code(concept.getName());
		sb.code("){\n");
		for (Method method : concept.getDeclaredMethods()) {
			if (isMappedGetter(method)) {
				String property = getPropertyName(method);
				Class<?> type = method.getReturnType();
				String ref = "((" + concept.getName() + ") $1)." + method.getName() + "()";
				mergeProperty(concept, sb, property, type, ref);
			}
		}
		int count = 0;
		for (Field f : concept.getDeclaredFields()) {
			if (isMappedField(f)) {
				String property = getPropertyName(f);
				Class<?> type = f.getType();
				String ref;
				if (Modifier.isPublic(f.getModifiers())) {
					ref = "((" + concept.getName() + ") $1)." + f.getName();
				} else {
					String fieldVar = f.getName() + "Field" + ++count;
					sb.declareObject(Field.class, fieldVar);
					sb.insert(f.getDeclaringClass());
					sb.code(".getDeclaredField(\"");
					sb.code(f.getName()).code("\")").semi();
					sb.code(fieldVar).code(".setAccessible(true)").semi();
					StringBuilder s = new StringBuilder();
					s.append(fieldVar).append(".get");
					if (f.getType().isPrimitive()) {
						String tname = f.getType().getName();
						s.append(tname.substring(0, 1).toUpperCase());
						s.append(tname.substring(1));
					}
					s.append("($1)");
					String fieldValue = f.getName() + "FieldValue" + ++count;
					sb.declareObject(f.getType(), fieldValue);
					if (f.getType().isPrimitive()) {
						sb.code(s.toString()).semi();
					} else {
						sb.castObject(s.toString(), f.getType()).semi();
					}
					ref = fieldValue;
				}
				mergeProperty(concept, sb, property, type, ref);
			}
		}
		sb.code("}").end();
	}

	private void mergeProperty(Class<?> concept, CodeBuilder sb,
			String property, Class<?> type, String ref) throws Exception {
		String field = getPropertyField(property);
		String factory = getFactoryField(property);
		if (type.isPrimitive()) {
			appendNullCheck(sb, field, factory);
			appendPersistCode(sb, field, type, ref);
		} else {
			String var = property + "Var";
			sb.declareObject(type, var);
			sb.code(ref);
			sb.code(";\n");
			// array != null does not seem to work in javassist
			sb.code("if(");
			sb.codeInstanceof(var, type);
			sb.code("){");
			appendNullCheck(sb, field, factory);
			appendPersistCode(sb, field, type, var);
			sb.code("}\n");
		}
	}

	private void overrideRefreshMethod(ClassTemplate cc, Class<?> concept)
			throws Exception {
		Method refresh = Refreshable.class.getMethod("refresh");
		CodeBuilder sb = cc.overrideMethod(refresh);
		for (String field : cc.getDeclaredFieldNames()) {
			if (field.endsWith(PROPERTY_SUFFIX)) {
				sb.code("if (").code(field).code(" != null) {");
				sb.code(field).code(".refresh();}\n");
			}
		}
		sb.end();
	}

	private void overrideMethod(Method method, ClassTemplate cc) throws Exception {
		String property = getPropertyName(method);
		Method setter = getSetterMethod(property, method);
		Class<?> type = method.getReturnType();
		String field = createPropertyField(property, cc);
		String factory = createFactoryField(property, method, setter, cc);
		CodeBuilder body = cc.overrideMethod(method);
		appendNullCheck(body, field, factory);
		appendGetterMethod(body, field, type, cc);
		body.end();
		if (setter != null) {
			body = cc.overrideMethod(setter);
			appendNullCheck(body, field, factory);
			appendSetterMethod(body, field, type, "$1");
			body.end();
		}
	}

	private String getPropertyName(Method method) {
		String name = method.getName();
		for (int i = 0, n = name.length(); i < n; i++) {
			char chr = name.charAt(i);
			if (!Character.isLowerCase(chr)) {
				StringBuilder property = new StringBuilder(name.length() - i);
				property.append(Character.toLowerCase(name.charAt(i)));
				property.append(name, i + 1, name.length());
				return property.toString();
			}
		}
		throw new IllegalArgumentException();
	}

	private void implementProperty(Field f, ClassTemplate cc) throws Exception {
		String property = getPropertyName(f);
		String field = createPropertyField(property, cc);
		String getter = "_$get_" + property;
		String setter = "_$set_" + property;
		Class<?> type = f.getType();
		String factory = createFactoryField(f, cc);
		CodeBuilder body = cc.createMethod(type, getter);
		appendNullCheck(body, field, factory);
		appendGetterMethod(body, field, type, cc);
		body.end();
		body = cc.createMethod(Void.TYPE, setter, type);
		appendNullCheck(body, field, factory);
		appendSetterMethod(body, field, type, "$1");
		body.end();
	}

	private String getPropertyName(Field f) {
		int code = f.getDeclaringClass().getName().hashCode();
		return f.getName() + Integer.toHexString(code);
	}

	private Method getSetterMethod(String property, Method getter) {
		try {
			StringBuilder smn = new StringBuilder();
			smn.append("set").append(Character.toUpperCase(property.charAt(0)));
			smn.append(property, 1, property.length());
			Class<?> dc = getter.getDeclaringClass();
			Class<?> rt = getter.getReturnType();
			return dc.getDeclaredMethod(smn.toString(), rt);
		} catch (NoSuchMethodException exc) {
			return null;
		}
	}

	private String createPropertyField(String property, ClassTemplate cc)
			throws Exception {
		String fieldName = getPropertyField(property);
		cc.createField(ElmoProperty.class, fieldName);
		return fieldName;
	}

	private String getPropertyField(String property) {
		return "_$" + property + PROPERTY_SUFFIX;
	}

	private String createFactoryField(String property, Method method,
			Method setter, ClassTemplate cc) throws Exception {
		String setterName = setter == null? null:setter.getName();
		Class<?> declaringClass = method.getDeclaringClass();
		String getterName = method.getName();
		Class<?> class1 = PropertyDescriptor.class;
		Class<?> type = ElmoPropertyFactory.class;
		String fieldName = getFactoryField(property);
		CodeBuilder code = cc.assignStaticField(type, fieldName);
		code.construct(propertyFactoryClass).code(".");
		String key = declaringClass.getName() + "." + property;
		if (properties.containsKey(key)) {
			code.code(SET_URI).code("(");
			code.insert((String) properties.get(key)).code(")");
			if (setter == null) {
				code.code(".").code(SET_READ_ONLY).code("(true)");
			}
		} else {
			code.code(SET_PROPERTY_DESCRIPTOR).code("(");
			code.construct(class1, property, declaringClass, getterName, setterName);
			code.code(")");
		}
		code.end();
		return fieldName;
	}

	private String createFactoryField(Field field, ClassTemplate cc) throws Exception {
		Class<?> declaringClass = field.getDeclaringClass();
		Class<?> type = ElmoPropertyFactory.class;
		String fieldName = getFactoryField(getPropertyName(field));
		CodeBuilder code = cc.assignStaticField(type, fieldName);
		code.construct(propertyFactoryClass).code(".");
		String key = declaringClass.getName() + "#" + field.getName();
		if (properties.containsKey(key)) {
			code.code(SET_URI).code("(");
			code.insert((String) properties.get(key)).code(")");
		} else {
			code.code(SET_FIELD).code("(").insert(declaringClass);
			code.code(".getDeclaredField(").insert(field.getName()).code("))");
		}
		code.end();
		return fieldName;
	}

	private String getFactoryField(String property) {
		return "_$" + property + FACTORY_SUFFIX;
	}

	private boolean isCollection(Class<?> type)
			throws Exception {
		return Set.class.equals(type);
	}

	public static Class<?> getClassType(Method method) {
		return method.getReturnType();
	}

	public static Class<?> getContentClassType(Method method) {
		Type[] types = getTypeArguments(method);
		if (types == null || types.length != 1)
			return null;
		if (types[0] instanceof Class)
			return (Class) types[0];
		if (types[0] instanceof ParameterizedType)
			return (Class) ((ParameterizedType) types[0]).getRawType();
		return null;
	}

	public static Type[] getTypeArguments(Method method) {
		Type type = method.getGenericReturnType();
		if (type instanceof ParameterizedType)
			return ((ParameterizedType) type).getActualTypeArguments();
		return null;
	}

	public static Type[] getContentTypeArguments(Method method) {
		Type[] types = getTypeArguments(method);
		if (types == null || types.length != 1)
			return null;
		if (types[0] instanceof ParameterizedType)
			return ((ParameterizedType) types[0]).getActualTypeArguments();
		return null;
	}

	private CodeBuilder appendNullCheck(CodeBuilder body, String field,
			String propertyFactory)
			throws Exception {
		body.code("if (").code(field).code(" == null) {");
		body.assign(field).code(propertyFactory);
		body.code(".").code(CREATE_ELMO_PROPERTY);
		body.code("(").code(BEAN_FIELD_NAME).code(");}");
		return body;
	}

	private CodeBuilder appendGetterMethod(CodeBuilder body, String field,
			Class<?> type, ClassTemplate cc) throws Exception {
		if (isCollection(type)) {
			body.code("return ").code(field);
			body.code(".").code(GET_ALL);
			body.code("();");
		} else if (type.isPrimitive()) {
			body.declareWrapper(type, "result");
			body.castObject(field, type);
			body.code(".").code(GET_SINGLE);
			body.code("();");
			body.code("if (result != null) ");
			body.code("return result.").code(type.getName()).code("Value();");
			if (Boolean.TYPE.equals(type)) {
				body.code("return false;");
			} else {
				body.code("return ($r) 0;");
			}
		} else {
			body.code("try {");
			body.code("return ");
			body.castObject(field, type);
			body.code(".").code(GET_SINGLE);
			body.code("();");
			body.code("} catch (java.lang.ClassCastException exc) {");
			body.code("throw new java.lang.ClassCastException(");
			body.code(field).code(".").code(GET_SINGLE);
			body.code("().toString() + \" cannot be cast to ");
			body.code(type.getName()).code("\");");
			body.code("}");
		}
		return body;
	}

	private CodeBuilder appendPersistCode(CodeBuilder body, String field,
			Class<?> type, String ref) throws Exception {
		body.code(field).code(".");
		if (isCollection(type)) {
			body.code(ADD_ALL).code("(").code(ref).code(");");
		} else {
			body.code(ADD_SINGLE).code("(").codeObject(ref, type).code(");");
		}
		return body;
	}

	private CodeBuilder appendSetterMethod(CodeBuilder body, String field,
			Class<?> type, String ref) throws Exception {
		body.code(field).code(".");
		if (isCollection(type)) {
			body.code(SET_ALL).code("(").code(ref).code(");");
		} else {
			body.code(SET_SINGLE).code("(").codeObject(ref, type).code(");");
		}
		return body;
	}
}
