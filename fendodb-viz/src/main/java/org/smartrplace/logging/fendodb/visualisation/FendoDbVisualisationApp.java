/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.visualisation;

import java.nio.file.Paths;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;

import de.iwes.widgets.api.OgemaGuiService;
import de.iwes.widgets.api.widgets.WidgetApp;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.navigation.NavigationMenu;

@Component
@Service(Application.class)
public class FendoDbVisualisationApp implements Application {

	private WidgetApp wApp;

	@Reference
	private FendoDbFactory slotsDbFactory;

	@Reference
	private OgemaGuiService widgetService;

	@Override
	public void start(ApplicationManager appManager) {
		String dbs = System.getProperty("org.smartrplace.logging.fendo.instances_read_only");
		if (dbs != null) {
			final FendoDbConfiguration cfg = FendoDbConfigurationBuilder.getInstance()
					.setReadOnlyMode(true)
					.build();
			String[] dbsArr = dbs.split(",");
			for (String db: dbsArr) {
				try {
					slotsDbFactory.getInstance(Paths.get(db), cfg);
				} catch (Exception e) {
					appManager.getLogger().error("Could not create slotsdb instance " +db,e);
				}
			}
		}
		String dbs2 = System.getProperty("org.smartrplace.logging.fendo.instances");
		if (dbs2 != null) {
			String[] dbsArr = dbs2.split(",");
			for (String db: dbsArr) {
				try {
					slotsDbFactory.getInstance(Paths.get(db));
				} catch (Exception e) {
					appManager.getLogger().error("Could not create slotsdb instance " +db,e);
				}
			}
		}

		wApp = widgetService.createWidgetApp("/org/smartrplace/slotsdb/visualisation", appManager);
		final WidgetPage<?> vizPageOld = wApp.createWidgetPage("slots-viz.html");
		new FendoPlotPage(vizPageOld, slotsDbFactory, appManager, widgetService.getNameService());

		final WidgetPage<?> vizPage = wApp.createStartPage();
		new FendoPage2(vizPage, slotsDbFactory, appManager);

		final WidgetPage<?> overview = wApp.createWidgetPage("overview.html");
		new FendoDbOverviewPage(overview, slotsDbFactory);

		final WidgetPage<?> tagsPage = wApp.createWidgetPage("tags.html");
		new TagsPage(tagsPage, slotsDbFactory);

		final NavigationMenu menu = new NavigationMenu(" Select page");
		menu.addEntry("FendoDB visualisation", vizPage);
		menu.addEntry("Legacy fendo visualisation", vizPageOld);
		menu.addEntry("FendoDB overview", overview);
		menu.addEntry("Tags view", tagsPage);
		vizPageOld.getMenuConfiguration().setCustomNavigation(menu);
		overview.getMenuConfiguration().setCustomNavigation(menu);
		vizPage.getMenuConfiguration().setCustomNavigation(menu);
		tagsPage.getMenuConfiguration().setCustomNavigation(menu);
	}

	@Override
	public void stop(AppStopReason reason) {
		if (wApp != null)
			wApp.close();
		wApp = null;
	}


}
