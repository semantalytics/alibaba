package org.openrdf.repository.object.managers.helpers;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;

import org.openrdf.model.vocabulary.RDF;

public class CheckForConcept {

	protected ClassLoader cl;

	private String pkgName = RDF.class.getPackage().getName();

	public CheckForConcept(ClassLoader cl) {
		this.cl = cl;
	}

	public String getClassName(String name) throws IOException {
		// NOTE package-info.class should be excluded
		if (!name.endsWith(".class") || name.contains("-"))
			return null;
		InputStream stream = cl.getResourceAsStream(name);
		assert stream != null : name;
		DataInputStream dstream = new DataInputStream(stream);
		try {
			ClassFile cf = new ClassFile(dstream);
			// concept with an annotation
			AnnotationsAttribute attr = (AnnotationsAttribute) cf
					.getAttribute(AnnotationsAttribute.visibleTag);
			if (isAnnotationPresent(attr))
				return cf.getName();
		} finally {
			dstream.close();
			stream.close();
		}
		return null;
	}

	private boolean isAnnotationPresent(AnnotationsAttribute attr) {
		if (attr != null) {
			Annotation[] annotations = attr.getAnnotations();
			if (annotations != null) {
				for (Annotation ann : annotations) {
					if (ann.getTypeName().startsWith(pkgName))
						return true;
				}
			}
		}
		return false;
	}
}