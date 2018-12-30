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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.webadmin.AdminWebAccessManager;
import org.ogema.webadmin.AdminWebAccessManager.StaticRegistration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(
		service=Application.class,
		configurationPid = FendodbGrafanaApp.PID,
		configurationPolicy = ConfigurationPolicy.OPTIONAL
)
@Designate(ocd=FendodbGrafanaApp.Config.class)
public class FendodbGrafanaApp extends HttpServlet implements Application {

	public static final String PID = "org.smartrplace.logging.FendoDbGrafana";
	static final String FOLDER = "configs"; 
	static final String GLOBAL_CONFIGS = "global";
	static final String USER_CONFIGS = "users";
	private static final long serialVersionUID = 1L;
	private static final String WEB_PATH = "/org/smartrplace/logging/fendodb/grafana";
	private ApplicationManager appMan;
	private Path globalConfigs;
	private Path userConfigs;
	private volatile WeakReference<List<String>> globalConfigsList = new WeakReference<>(null);
	private Path baseFolder;
	private Bundle thisBundle;
	private Config config;
	
	@ObjectClassDefinition
	@interface Config {
		
		@AttributeDefinition(description="URL replacing '/org/ogema/tools/grafana-base'", defaultValue="")
		String remoteResources() default "";
		
	}
	
	@Activate
	protected void activate(BundleContext ctx, Config config) throws IOException {
		this.baseFolder = ctx.getDataFile(FendodbGrafanaApp.FOLDER).toPath().resolve(FendodbGrafanaApp.GLOBAL_CONFIGS);
		Files.createDirectories(baseFolder);
		thisBundle = ctx.getBundle();
		this.config = config;
	}

	@Override
	public void start(ApplicationManager appManager) {
		this.appMan = appManager;
		final Path basePath = appManager.getDataFile(FOLDER).toPath();
		this.globalConfigs = basePath.resolve(GLOBAL_CONFIGS);
		this.userConfigs = basePath.resolve(USER_CONFIGS);
		appManager.getWebAccessManager().registerWebResource(WEB_PATH + "/servlet", this);
		appManager.getWebAccessManager().registerWebResource(WEB_PATH + "/viz", "webresources");
		appManager.getWebAccessManager().registerWebResource(WEB_PATH + "/upload", new ConfigFileUpload(basePath, () -> globalConfigsList = new WeakReference<>(null)));
		final String remote = config.remoteResources();
		if (remote != null && !remote.trim().isEmpty()) {
			final AtomicReference<StaticRegistration> ref = new AtomicReference<AdminWebAccessManager.StaticRegistration>(null);
			try {
				final String browserPath2 = WEB_PATH + "/viz/index2.html";
				final RemoteResourcesPage page = new RemoteResourcesPage(remote.trim(), thisBundle, ref);
				final StaticRegistration reg = ((AdminWebAccessManager) appManager.getWebAccessManager()).registerStaticWebResource(browserPath2, page);
				ref.set(reg);
				if (Boolean.getBoolean("org.ogema.gui.usecdn")) {
	        	  	 appManager.getWebAccessManager().registerStartUrl(browserPath2);
	        	 }
			} catch (IOException | SecurityException e) {
				appManager.getLogger().warn("Failed to register remote resources page",e);
			}
		}
	}
	
	@Override
	public void stop(AppStopReason reason) {
		try {
			appMan.getWebAccessManager().unregisterWebResource(WEB_PATH + "/upload");
			appMan.getWebAccessManager().unregisterWebResource(WEB_PATH + "/servlet");
			appMan.getWebAccessManager().unregisterWebResource(WEB_PATH + "/viz");
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			appMan.getWebAccessManager().unregisterWebResource(WEB_PATH + "/viz/index2.html");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final String target = req.getParameter("target");
		if (target == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Target missing");
			return;
		}
		switch (target.toLowerCase()) {
		case "config":
			final String config = req.getParameter("config");
			if (config == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Config missing");
				return;
			}
			final Path file = globalConfigs.resolve(config + ".json");
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/json");
			if (!Files.isRegularFile(file)) {
				final URL url = getFragmentConfig(config);
				if (url == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Config " + config + " does not exist");
					return;
				}
				final byte[] buffer = new byte[8192];
			    int bytesRead;
			    try (final InputStream in = url.openStream(); 
			    		final OutputStream out = resp.getOutputStream()) {
				    while ((bytesRead = in.read(buffer)) != -1) {
				        out.write(buffer, 0, bytesRead);
				    }
			    }
			    resp.setHeader("X-Editable", "false");
			} else { 
				Files.copy(file, resp.getOutputStream());
				resp.setHeader("X-Editable", "true");
			}
			break;
		case "configs":
			final List<String> configs = new ArrayList<>(getGlobalConfigs());
			configs.addAll(getFragmentConfigs());
			print(resp.getWriter(), configs);
			resp.setContentType("application/json");
			break;
		default:
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown target " + target);
			return;
		}
		resp.setStatus(HttpServletResponse.SC_OK);
	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final String config = req.getParameter("config");
		if (config == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Config missing");
			return;
		}
		final Path file = globalConfigs.resolve(config + ".json");
		if (!Files.isRegularFile(file)) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Config " + config + " does not exist");
			return;
		}
		Files.delete(file);
		resp.getWriter().write("Config " + config + " deleted");
		resp.setStatus(HttpServletResponse.SC_OK);
		globalConfigsList = new WeakReference<List<String>>(null);
	}

	private List<String> getGlobalConfigs() throws IOException {
		List<String> list = globalConfigsList.get();
		if (list == null) {
			synchronized (this) {
				list = globalConfigsList.get();
				if (list == null) {
					try (final Stream<Path> stream = Files.list(globalConfigs)) {
						list = stream.filter(Files::isRegularFile)
							.map(Path::getFileName)
							.map(Path::toString)
							.filter(name -> name.toLowerCase().endsWith(".json"))
							.map(string -> string.substring(0, string.length()-5))
							.collect(Collectors.toList());
					}
					globalConfigsList = new WeakReference<List<String>>(list);
				}
			}
		}
		return list;
	}
	
	private URL getFragmentConfig(final String config) {
		final Enumeration<URL> en = thisBundle.findEntries("configs", config + ".json" , false);
		return en.hasMoreElements() ? en.nextElement() : null;
	}
	
	private List<String> getFragmentConfigs() {
		final Enumeration<URL> urls = thisBundle.findEntries("/configs", "*.json", true);
		if (urls == null || !urls.hasMoreElements())
			return Collections.emptyList();
		final List<String> list = new ArrayList<>();
		while (urls.hasMoreElements()) {
			final URL url = urls.nextElement();
			final String path = url.getPath();
			list.add(path.substring("/configs/".length(), path.length() - ".json".length()));
		}
		return list;
	}
	
	private static void print(final PrintWriter writer, final Collection<String> list) {
		writer.write('[');
		final Iterator<String> it = list.iterator();
		boolean first = true;
		while (it.hasNext()) {
			if (!first) {
				writer.write(',');
				writer.write(' ');
			}
			writer.write('\"');
			writer.write(it.next());
			writer.write('\"');
			first = false;
		}
		writer.write(']');
	}
	
}
