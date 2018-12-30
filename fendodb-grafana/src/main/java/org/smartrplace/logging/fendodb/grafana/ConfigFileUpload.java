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
package org.smartrplace.logging.fendodb.grafana;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
@Component(
		service = Servlet.class,
		property = {
				HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/config-upload",
				HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT + "=" + RecordedDataServlet.CONTEXT_FILTER,
				HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_MULTIPART_ENABLED + ":Boolean=true",
				HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_MULTIPART_MAXFILESIZE + ":Long=20000000"
		}
)
*/
@MultipartConfig(maxFileSize=20000000, maxRequestSize=20000000,fileSizeThreshold=3000000)
public class ConfigFileUpload extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Path baseFolder;
	private final Runnable uploadCallback;
	
	
	public ConfigFileUpload(Path baseFolder, Runnable uploadCallback) {
		this.baseFolder = baseFolder;
		this.uploadCallback = uploadCallback;
	}
	
	/*
	@Activate
	protected void activate(BundleContext ctx) throws IOException {
		this.baseFolder = ctx.getDataFile(FendodbGrafanaApp.FOLDER).toPath().resolve(FendodbGrafanaApp.GLOBAL_CONFIGS);
		Files.createDirectories(baseFolder);
	}
	*/

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final Collection<Part> parts = req.getParts();
		if (parts.isEmpty()) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No files provided");
			return;
		}
		try {
			parts.stream()
				.filter(part -> part.getContentType() != null && part.getContentType().startsWith("application/json"))
				.forEach(part -> {
					String name = part.getSubmittedFileName();
					if (name == null || name.length() > 50) // TODO report to client
						return;
					if (!name.toLowerCase().endsWith(".json"))
						name = name + ".json";
					final Path target = baseFolder.resolve(FendodbGrafanaApp.GLOBAL_CONFIGS).resolve(name);
					try {
						Files.copy(part.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
						logger.debug("File uploaded to {}", target);
					} catch (IOException e) {
						logger.error("Failed to save configuration " + name, e);
						throw new UncheckedIOException(e);
					}
				});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
		resp.setStatus(HttpServletResponse.SC_OK);
		if (uploadCallback != null)
			uploadCallback.run();
	}
	
}
