/**
 * ﻿Copyright 2018 Smartrplace UG
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
 */
package org.smartrplace.logging.fendodb.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;

class Response {
	
	final ProxyOutputStream streamOut = new ProxyOutputStream();
	final ProxyWriter writerOut = new ProxyWriter();
	
	String getResponseAsString() {
		final String writerResult = writerOut.getResult();
		if (writerResult != null && !writerResult.isEmpty())
			return writerResult;
		return streamOut.getResultAsString();
	}
	
	static class ProxyOutputStream extends ServletOutputStream {

		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		@Override
		public void write(int b) throws IOException {
			out.write(b);
		}
		
		public byte[] getResult() {
			return out.toByteArray();
		}
		
		public String getResultAsString() {
			return out.size() == 0 ? null : new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
		
	}
	
	static class ProxyWriter extends PrintWriter {

		ProxyWriter() {
			super(new StringWriter());
		}

		public String getResult() {
			return ((StringWriter) out).toString();
		}
		
	}

}
