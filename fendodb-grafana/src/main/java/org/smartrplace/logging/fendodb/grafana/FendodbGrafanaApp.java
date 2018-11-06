package org.smartrplace.logging.fendodb.grafana;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service=Application.class)
public class FendodbGrafanaApp extends HttpServlet implements Application {

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
	
	@Activate
	protected void activate(BundleContext ctx) throws IOException {
		this.baseFolder = ctx.getDataFile(FendodbGrafanaApp.FOLDER).toPath().resolve(FendodbGrafanaApp.GLOBAL_CONFIGS);
		Files.createDirectories(baseFolder);
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
			if (!Files.isRegularFile(file)) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Config " + config + " does not exist");
				return;
			}
			Files.copy(file, resp.getOutputStream());
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/json");
			break;
		case "configs":
			final List<String> configs = getGlobalConfigs();
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
