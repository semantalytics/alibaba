/*
 * Copyright (c) 2008, James Leigh All rights reserved.
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
package org.openrdf.repository.object.compiler.source;

import java.util.Map;

/**
 * Java source code builder for a Java method.
 * 
 * @author James Leigh
 * 
 */
public class JavaMethodBuilder extends JavaSourceBuilder {
	private String methodName;
	private boolean isInterface;
	private boolean isStatic;
	private boolean hasParameters;
	private boolean isAbstract = true;
	private boolean hasReturnType;
	private boolean endParameter;
	private boolean headerPrinted;
	private boolean bodyPrinted;
	private StringBuilder body = new StringBuilder();

	public JavaMethodBuilder(String name, boolean isInterface,
			boolean isStatic, boolean isAbstract, Map<String, String> imports, StringBuilder sb) {
		this.methodName = name;
		this.isInterface = isInterface;
		this.isStatic = isStatic;
		this.isAbstract = isAbstract;
		setImports(imports);
		setStringBuilder(sb);
		setIndent("\t");
	}

	public JavaMethodBuilder returnType(String type) {
		hasReturnType = true;
		body.append(imports(type)).append(" ");
		return this;
	}

	public JavaMethodBuilder returnSetOf(String type) {
		hasReturnType = true;
		body.append(imports("java.util.Set"));
		body.append("<").append(imports(type)).append("> ");
		return this;
	}

	@Override
	protected void begin() {
		if (!headerPrinted) {
			sb.append("\t");
			if (!isInterface) {
				sb.append("public ");
				if (isStatic) {
					sb.append("static ");
				}
				if (isAbstract) {
					sb.append("abstract ");
				}
			}
			headerPrinted = true;
		}
		if (endParameter) {
			body.append(", ");
			endParameter = false;
		} else if (hasReturnType && !hasParameters) {
			hasParameters = true;
			body.append(methodName);
			body.append("(");
		}
		sb.append(body);
		body.setLength(0);
	}

	public JavaMethodBuilder paramSetOf(String type, String name) {
		if (hasParameters && endParameter) {
			body.append(", ");
		} else if (!hasParameters) {
			hasParameters = true;
			body.append(methodName);
			body.append("(");
		}
		body.append(imports("java.util.Set"));
		body.append("<").append(imports(type)).append("> ");
		body.append(name);
		endParameter = true;
		return this;
	}

	public JavaMethodBuilder param(String type, String name) {
		if (hasParameters && endParameter) {
			body.append(", ");
		} else if (!hasParameters) {
			hasParameters = true;
			body.append(methodName);
			body.append("(");
		}
		body.append(imports(type)).append(" ").append(var(name));
		endParameter = true;
		return this;
	}

	public JavaMethodBuilder code(String code) {
		if (code == null)
			return this;
		if (!bodyPrinted) {
			if (!hasParameters) {
				body.append(methodName);
				body.append("(");
			}
			isAbstract = false;
			bodyPrinted = true;
			body.append(") {\n\t\t");
		}
		body.append(code);
		return this;
	}

	public JavaMethodBuilder string(String string) {
		code("\""
				+ string.replace("\\", "\\\\").replace("\"", "\\\"").replace(
						"\n", "\\n") + "\"");
		return this;
	}

	public void end() {
		if (!headerPrinted) {
			sb.append("\t");
			if (!isInterface) {
				sb.append("public ");
				if (isStatic) {
					sb.append("static ");
				}
				if (isAbstract) {
					sb.append("abstract ");
				}
			}
			headerPrinted = true;
		}
		sb.append(body);
		if (!bodyPrinted) {
			if (!hasParameters) {
				sb.append(methodName);
				sb.append("(");
			}
			sb.append(");\n\n");
		} else {
			sb.append("\n\t}\n\n");
		}
	}

}
