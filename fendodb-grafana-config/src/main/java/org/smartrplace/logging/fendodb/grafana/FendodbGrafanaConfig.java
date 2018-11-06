package org.smartrplace.logging.fendodb.grafana;

import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.security.WebAccessManager;
import org.osgi.service.component.annotations.Component;

@Component(service=Application.class)
public class FendodbGrafanaConfig implements Application {

	private static final String WEB_PATH = "/org/smartrplace/logging/fendodb/grafana";
	private ApplicationManager appMan;
	
	@Override
	public void start(ApplicationManager appManager) {
		this.appMan = appManager;
		final WebAccessManager wam = appManager.getWebAccessManager();
		wam.registerWebResource(WEB_PATH + "/config", "webresources");
		wam.registerStartUrl(WEB_PATH + "/config/config.html");
	}
	
	@Override
	public void stop(AppStopReason reason) {
		try {
			appMan.getWebAccessManager().unregisterWebResource(WEB_PATH + "/config");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
