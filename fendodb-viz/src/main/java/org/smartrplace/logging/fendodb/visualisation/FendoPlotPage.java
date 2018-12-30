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
package org.smartrplace.logging.fendodb.visualisation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.ogema.core.application.ApplicationManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.alert.Alert;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.reswidget.scheduleviewer.DefaultTimeSeriesDisplayTemplate;
import de.iwes.widgets.reswidget.scheduleviewer.ScheduleViewerBasic;
import de.iwes.widgets.reswidget.scheduleviewer.api.ScheduleViewerConfiguration;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=slots-viz.html",
				LazyWidgetPage.MENU_ENTRY + "=Legacy fendo visualisation"
		}
)
public class FendoPlotPage implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new FendoPlotPageInit(page, factory, appMan);
	}
	
	private static class FendoPlotPageInit {
	
	
		FendoPlotPageInit(final WidgetPage<?> page, final FendoDbFactory slotsDbFactory, final ApplicationManager appManager) {
	
			Header header = new Header(page, "header", true);
			header.setDefaultText("FendoDB visualization");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_CENTERED);
	
			final Alert alert = new Alert(page, "alert", "");
			alert.setDefaultVisibility(false);
	
			final TemplateDropdown<DataRecorderReference> slotsDbDropdown = new FendoSelector(page, "slotsSelector", slotsDbFactory);
	
			// per default, show time from before two days to now
	//		final ScheduleViewerConfiguration svconfig = new ScheduleViewerConfiguration(false, true, false, null, false, 2 * 24 * 3600 * 1000L, 0L);
	
			final ScheduleViewerConfiguration svconfig = new ScheduleViewerConfiguration(false, true, true, false, null, false, null, null, Arrays.asList(Programs.programs), null, 24*60*60*1000L);
			final ScheduleViewerBasic<FendoTimeSeries> dataPlot = new ScheduleViewerBasic<FendoTimeSeries>(page, "dataPlot", appManager,
						svconfig,new DefaultTimeSeriesDisplayTemplate<FendoTimeSeries>(null)) {
	
				private static final long serialVersionUID = 1L;
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					DataRecorderReference cdr = slotsDbDropdown.getSelectedItem(req);
					if (cdr == null)
						setSchedules(Collections.emptyList(), req);
					else {
						try (CloseableDataRecorder rec = cdr.getDataRecorder()) {
							setSchedules(rec.getAllTimeSeries(), req);
						} catch (IOException e) {
							setSchedules(Collections.emptyList(), req);
						}
					}
				}
	
			};
	
			slotsDbDropdown.triggerAction(dataPlot, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			slotsDbDropdown.triggerAction(dataPlot.getSchedulePlot(), TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
	
			StaticTable table =  new StaticTable(1, 2, new int[]{2,3})
				.setContent(0, 0, "Select FendoDb").setContent(0, 1, slotsDbDropdown);
	
			page.append(header).linebreak().append(alert).linebreak().append(table);
			page.linebreak().append(dataPlot);
		}
	}
}
