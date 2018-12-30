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
