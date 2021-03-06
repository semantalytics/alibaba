/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.http.object.management;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.apache.commons.codec.binary.Base64;

public class KeyStoreImpl implements KeyStoreMXBean {
	private static final int CERT_EXPIRE_DAYS = 31;

	public long getCertificateExperation() throws GeneralSecurityException,
			IOException {
		String alias = getKeyAlias();
		char[] password = getKeyStorePassword();
		return getCertificateExperation(CERT_EXPIRE_DAYS, alias, getKeyStoreFile(),
				password);
	}

	public synchronized String exportCertificate() throws IOException,
			GeneralSecurityException {
		KeyStore ks = loadKeyStore();
		String alias = getKeyAlias();
		if (!ks.isKeyEntry(alias))
			return null;
		Certificate cer = ks.getCertificate(alias);
		StringBuilder sb = new StringBuilder();
		sb.append("-----BEGIN CERTIFICATE-----\n");
		sb.append(new String(Base64.encodeBase64(cer.getEncoded())));
		sb.append("\n-----END CERTIFICATE-----\n");
		return sb.toString();
	}

	public boolean isCertificateSigned() throws IOException,
			GeneralSecurityException {
		KeyStore ks = loadKeyStore();
		Certificate[] chain = ks.getCertificateChain(getKeyAlias());
		return chain != null && chain.length > 1;
	}

	private String getKeyAlias() throws IOException, GeneralSecurityException {
		KeyStore ks = loadKeyStore();
		Enumeration<String> aliases = ks.aliases();
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			if (ks.isKeyEntry(alias))
				return alias;
		}
		return null;
	}

	private long getCertificateExperation(int days, String alias,
			File keystore, char[] password) throws GeneralSecurityException,
			IOException {
		KeyStore ks = loadKeyStore();
		if (ks.isKeyEntry(alias)) {
			Certificate cert = ks.getCertificate(alias);
			if (cert instanceof X509Certificate) {
				return ((X509Certificate) cert).getNotAfter().getTime();
			}
		}
		return -1;
	}

	private KeyStore loadKeyStore() throws IOException, KeyStoreException,
			FileNotFoundException, NoSuchAlgorithmException,
			CertificateException {
		char[] password = getKeyStorePassword();
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		File file = getKeyStoreFile();
		if (file.exists()) {
			FileInputStream in = new FileInputStream(file);
			try {
				ks.load(in, password);
			} finally {
				in.close();
			}
		} else {
			ks.load(null, password);
		}
		return ks;
	}

	private char[] getKeyStorePassword() throws IOException {
		String password = getProperty("javax.net.ssl.keyStorePassword");
		if (password == null)
			return "changeit".toCharArray();
		return password.toCharArray();
	}

	private File getKeyStoreFile() throws IOException {
		String keyStore = getProperty("javax.net.ssl.keyStore");
		if (keyStore == null)
			return new File(System.getProperty("user.home"), ".keystore");
		return new File(keyStore);
	}

	private String getProperty(String key) {
		try {
			return System.getProperty(key);
		} catch (SecurityException e) {
			e.printStackTrace(System.err);
		}
		return null;
	}
}
