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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ogema.core.application.ApplicationManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.alert.Alert;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.button.ButtonData;
import de.iwes.widgets.html.form.dropdown.DropdownOption;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.multiselect.Multiselect;
import de.iwes.widgets.reswidget.scheduleviewer.ScheduleViewerBasic;
import de.iwes.widgets.reswidget.scheduleviewer.api.ScheduleViewerConfiguration;
import de.iwes.widgets.reswidget.scheduleviewer.api.ScheduleViewerConfigurationBuilder;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=index.html",
				LazyWidgetPage.START_PAGE + ":Boolean=true",
				LazyWidgetPage.MENU_ENTRY + "=FendoDB visualisation"
		}
)
public class FendoPage2 implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new FendoPage2Init(page, factory, appMan);
	}
	
	private static class FendoPage2Init {

		private final WidgetPage<?> page;
		private final Header header;
		private final Alert alert;
		private final FendoSelector slotsSelector;
		private final Multiselect tagsSelect;
		private final PropertiesFlexbox propertiesBox;
		private final Button applySelection;
		private final ScheduleViewerBasic<FendoTimeSeries> scheduleViewer;
	
		@SuppressWarnings("serial")
		FendoPage2Init(final WidgetPage<?> page, final FendoDbFactory factory, final ApplicationManager am) {
			this.page = page;
			page.setTitle("FendoDB visualisation");
			this.header = new Header(page, "header", "FendoDB visualization");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_LEFT);
			header.setDefaultColor("blue");
			this.alert = new Alert(page, "alert", "");
			alert.setDefaultVisibility(false);
			this.slotsSelector = new FendoSelector(page, "slotsSelector", factory) {
	
				// initialize time series selector
				@Override
				public void onGET(OgemaHttpRequest req) {
					super.onGET(req);
					initScheduleViewer(req);
				}
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					initScheduleViewer(req);
				}
	
				private final void initScheduleViewer(final OgemaHttpRequest req) {
					scheduleViewer.setSchedules(Collections.emptyList(), req);
					try (final CloseableDataRecorder rec = Utils.getDataRecorder(slotsSelector, req, true)) {
						if (rec == null)
							return;
						scheduleViewer.setSchedules(rec.getAllTimeSeries(), req);
					} catch (IOException ignore) {}
				}
	
			};
			this.tagsSelect = new Multiselect(page, "tagsSelect") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					clear(req);
					Map<String, Collection<String>> props = Collections.emptyMap();
					try (final CloseableDataRecorder rec = Utils.getDataRecorder(slotsSelector, req, true)) {
						if (rec == null)
							return;
						props = rec.getAllProperties();
					}  catch (IOException ignore) {} // just from closing the reference
					setOptions(getDropOptions(props.keySet()), req);
					propertiesBox.updateProps(props, req);
				}
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					propertiesBox.updateTags(getSelectedValues(req), req);
				}
	
			};
			this.propertiesBox = new PropertiesFlexbox(page, "propertiesBox");
			this.applySelection = new Button(page, "applySelection", "Apply filters") {
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference ref = slotsSelector.getSelectedItem(req);
					if (ref == null) {
						scheduleViewer.setSchedules(Collections.emptyList(), req);
						return;
					}
					final Map<String, Collection<String>> props = propertiesBox.getSelectedProperties(req);
					final SearchFilterBuilder builder = SearchFilterBuilder.getInstance();
					props.entrySet().stream()
						.filter(entry -> !entry.getValue().isEmpty())
						.forEach(entry -> builder.filterByPropertyMultiValue(entry.getKey(), entry.getValue(), false));
	//					.forEach(entry -> entry.getValue().stream().forEach(val -> builder.filterByProperty(entry.getKey(), val, false)));
					try (final CloseableDataRecorder rec = ref.getDataRecorder()) {
						final List<FendoTimeSeries> result = rec.findTimeSeries(builder.build());
						scheduleViewer.setSchedules(result, req);
						scheduleViewer.selectSchedules(result, req);
					} catch (IOException e) {
						alert.showAlert("An error occured: " + e, false, req);
						return;
					}
	
				}
	
			};
			this.applySelection.addDefaultStyle(ButtonData.BOOTSTRAP_BLUE);
			final ScheduleViewerConfiguration cfg = ScheduleViewerConfigurationBuilder.newBuilder()
					.setShowPlotTypeSelector(true)
					.setShowIndividualConfigBtn(true)
					.setShowDownsamplingInterval(true)
					.build();
			this.scheduleViewer = new ScheduleViewerBasic<>(page, "scheduleViewer", am, cfg, null);
			scheduleViewer.getDefaultPlotConfiguration().doScale(false);
			buildPage();
			setDependencies();
		}
	
		private final void buildPage() {
			int row = 0;
			page.append(header).append(alert).linebreak().append(new StaticTable(2, 3, new int[] {2,2,8})
				.setContent(row, 0, "Select FendoDB").setContent(row++, 1, slotsSelector)
				.setContent(row, 0, "Select tags").setContent(row++, 1, tagsSelect)
			).append(new StaticTable(2, 2, new int[] {2,10})
				.setContent(row=0, 0, "Select properties").setContent(row++, 1, propertiesBox)
				.setContent(row, 0, applySelection)
			)
			.append(scheduleViewer);
		}
	
		private final void setDependencies() {
			slotsSelector.triggerAction(tagsSelect, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			slotsSelector.triggerAction(scheduleViewer.getScheduleSelector(), TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST); // initial schedules selection
			slotsSelector.triggerAction(scheduleViewer.getScheduleSelector(), TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST); // initial schedules selection
			tagsSelect.triggerAction(propertiesBox, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			tagsSelect.triggerAction(propertiesBox, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			applySelection.triggerAction(scheduleViewer, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			applySelection.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
		}
	
	
		private final static List<DropdownOption> getDropOptions(final Collection<String> values) {
			return values.stream()
				.map(val -> new DropdownOption(val, val, true))
				.collect(Collectors.toList());
		}
	
	}

}
