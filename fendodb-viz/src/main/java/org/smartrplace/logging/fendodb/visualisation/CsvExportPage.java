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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ogema.core.application.ApplicationManager;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.tools.FendoDbTools;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfiguration;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfigurationBuilder;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.OgemaWidget;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.alert.Alert;
import de.iwes.widgets.html.calendar.datepicker.Datepicker;
import de.iwes.widgets.html.filedownload.Download;
import de.iwes.widgets.html.filedownload.FileDownloadData;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.button.ButtonData;
import de.iwes.widgets.html.form.checkbox.Checkbox2;
import de.iwes.widgets.html.form.checkbox.DefaultCheckboxEntry;
import de.iwes.widgets.html.form.dropdown.DropdownOption;
import de.iwes.widgets.html.form.dropdown.EnumDropdown;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.form.label.Label;
import de.iwes.widgets.html.form.textfield.ValueInputField;
import de.iwes.widgets.html.html5.Flexbox;
import de.iwes.widgets.html.html5.SimpleGrid;
import de.iwes.widgets.html.html5.flexbox.JustifyContent;
import de.iwes.widgets.html.multiselect.Multiselect;
import de.iwes.widgets.html.multiselect.TemplateMultiselect;
import de.iwes.widgets.reswidget.scheduleviewer.StartEndDatepicker;
import de.iwes.widgets.template.DisplayTemplate;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=csv.html",
				LazyWidgetPage.MENU_ENTRY + "=CSV export"
		}
)
public class CsvExportPage implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new CsvExportPageInit(page, factory, appMan, false, "_export");
	}
	
	static class CsvExportPageInit {

		protected final WidgetPage<?> page;
		protected Header header = null;
		protected final Alert alert;
		
		protected static enum SelectionMode {
			TAGS_PROPERTIES,
			PATH,
			FILTER
		}
		//protected final TemplateDropdown<SelectionMode> selectionMode;
		
		protected final FendoSelector slotsSelector;
		protected final Multiselect tagsSelect;
		protected final PropertiesFlexbox propertiesBox;
		protected final Button applySelection;
		protected final TemplateMultiselect<FendoTimeSeries> seriesSelector;
		protected final Datepicker startPicker;
		protected final Datepicker endPicker;
		protected final Checkbox2 options;
		private ValueInputField<Long> samplingInterval = null;
		private EnumDropdown<TimeUnit> samplingUnitSelector = null;
		protected final Label nrDatapoints;
		protected final Button nrDatapointsTrigger;
		private Download download = null;
		private Button downloadTrigger = null;
		private static final String SINGLE_FILE_PROP = "singlefile";
		private static final String DATES_FIXED_PROP = "datesfixed";
		private static final String ALIGN_TIMESTAMPS_PROP = "aligntimestamps";
	
		protected final String subId;
		
		@SuppressWarnings("serial")
		CsvExportPageInit(final WidgetPage<?> page, final FendoDbFactory factory,
				final ApplicationManager am, boolean isInherited, String subId) {
			this.page = page;
			this.subId = subId;
			if(!isInherited) {
				page.setTitle("FendoDB CSV export");
				this.header = new Header(page, "header"+subId, "CSV export");
				header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_LEFT);
				header.setDefaultColor("blue");
			}
			this.alert = new Alert(page, "alert"+subId, "");
			alert.setDefaultVisibility(false);
			this.slotsSelector = new FendoSelector(page, "slotsSelector"+subId, factory) {
	
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
					seriesSelector.update(Collections.emptyList(), req);
					try (final CloseableDataRecorder rec = Utils.getDataRecorder(slotsSelector, req, false)) {
						if (rec == null)
							return;
						//FIXME: Quick hack to avoid too many time series offered here
						List<FendoTimeSeries> allTs = rec.getAllTimeSeries();
						List<FendoTimeSeries> offered = new ArrayList<>();
						for(FendoTimeSeries ts: allTs) {
							if(ts.getPath().contains("EMON_BASE/electricityConnectionBox_D/connection/energySensor"))
								offered.add(ts);
						}
						seriesSelector.update(offered, req);
						//seriesSelector.update(rec.getAllTimeSeries(), req);
					} catch (IOException ignore) {}
				}
	
			};
			//this.selectionMode = new TemplateDropdown<>(page, "selectionMode");
			//selectionMode.setDefaultItems(Arrays.asList(a));
			
			this.tagsSelect = new Multiselect(page, "tagsSelect"+subId) {
	
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
			this.propertiesBox = new PropertiesFlexbox(page, "propertiesBox"+subId);
			this.applySelection = new Button(page, "applySelection"+subId, "Apply filters") {
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final DataRecorderReference ref = slotsSelector.getSelectedItem(req);
					if (ref == null) {
						seriesSelector.update(Collections.emptyList(), req);
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
						seriesSelector.update(result, req);
						seriesSelector.selectItems(result, req);
					} catch (IOException e) {
						alert.showAlert("An error occured: " + e, false, req);
						return;
					}
	
				}
	
			};
			this.applySelection.addDefaultStyle(ButtonData.BOOTSTRAP_BLUE);
			this.seriesSelector = new TemplateMultiselect<>(page, "seriesSelector"+subId);
			seriesSelector.setTemplate(new DisplayTemplate<FendoTimeSeries>() {
				
				@Override
				public String getLabel(FendoTimeSeries ts, OgemaLocale locale) {
					return ts.getPath();
				}
				
				@Override
				public String getId(FendoTimeSeries ts) {
					return ResourceUtils.getValidResourceName(ts.getPath());
				}
				
			});
			this.nrDatapoints = new Label(page, "nrDatapoinst"+subId) {
				
				public void onGET(OgemaHttpRequest req) {
					final OgemaWidget trigger = page.getTriggeringWidget(req);
					if (trigger != nrDatapointsTrigger)
						setText("", req);
				}
				
			};
			this.nrDatapointsTrigger = new Button(page, "nrDatapointsTrigger"+subId, "Estimate nr of points") {
				
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final Collection<FendoTimeSeries> series = seriesSelector.getSelectedItems(req);
					if (series.isEmpty()) {
						nrDatapoints.setText("0", req);
						return;
					}
					final long start = startPicker.getDateLong(req);
					long end = endPicker.getDateLong(req);
					if (end < start) { // this might lead to an exception in the size call, otherwise
						nrDatapoints.setText("0", req);
						return;
					}
					final long size;
					final boolean useSampling = options.isChecked(ALIGN_TIMESTAMPS_PROP, req);
					Long itv = useSampling ? samplingInterval.getNumericalValue(req) : null;
					if (itv != null) {
						final TimeUnit unit = samplingUnitSelector.getSelectedItem(req);
						itv = TimeUnit.MILLISECONDS.convert(itv, unit);
						size = (end - start) / itv * series.size();
					} else {
						size = series.stream()
							.mapToLong(ts -> ts.size(start, end)) 
							.reduce((a,b) -> a + b)
							.orElse(0);
					}
					nrDatapoints.setText(String.valueOf(size), req);
				}
				
			};
			this.startPicker = new CsvStartEndPicker(page, "startPicker"+subId, true);
			this.endPicker = new CsvStartEndPicker(page, "endPicker"+subId, false);
			
			this.options = new Checkbox2(page, "optionsCheck"+subId) {
				
				public void onGET(OgemaHttpRequest req) {
					if (!isChecked(ALIGN_TIMESTAMPS_PROP, req))
						setState(SINGLE_FILE_PROP, false, req);
				}
				
			};
			if(isInherited) {
				options.setDefaultCheckboxList(Arrays.asList(
						new DefaultCheckboxEntry(DATES_FIXED_PROP, "Fix interval on schedule change", false)
				));
				return;
			}
			options.setDefaultCheckboxList(Arrays.asList(
					new DefaultCheckboxEntry(SINGLE_FILE_PROP, "Write to single file", true),
					new DefaultCheckboxEntry(DATES_FIXED_PROP, "Fix interval on schedule change", false),
					new DefaultCheckboxEntry(ALIGN_TIMESTAMPS_PROP, "Align timestamps (equidistant)?", true)
			));
			this.samplingInterval = new ValueInputField<Long>(page, "samplingInterval", Long.class) {
				
				public void onGET(OgemaHttpRequest req) {
					if (!options.isChecked(ALIGN_TIMESTAMPS_PROP, req))
						disable(req);
					else
						enable(req);
				}
				
			};
			samplingInterval.setDefaultNumericalValue(15L);
			samplingInterval.setDefaultLowerBound(1);
			this.samplingUnitSelector = new EnumDropdown<TimeUnit>(page, "samplingUnitSelector", TimeUnit.class) {
				
				public void onGET(OgemaHttpRequest req) {
					if (!options.isChecked(ALIGN_TIMESTAMPS_PROP, req))
						disable(req);
					else
						enable(req);
				}
				
			};
			samplingUnitSelector.selectDefaultItem(TimeUnit.MINUTES);
			this.download = new Download(page, "download", am);
			this.downloadTrigger = new Button(page, "downloadTrigger", "Download") {
				
				public void onGET(OgemaHttpRequest req) {
					if (seriesSelector.isEmpty(req))
						disable(req);
					else
						enable(req);
				}
				
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final Collection<FendoTimeSeries> timeSeries = seriesSelector.getSelectedItems(req);
					if (timeSeries.isEmpty()) {
						download.disable(req);
						alert.showAlert("Please select a time series", false, req);
						return;
					} 
					final long start = startPicker.getDateLong(req);
					final long end = endPicker.getDateLong(req);
					if (end < start) {
						download.disable(req);
						alert.showAlert("End time must be >= startTime", false, req);
						return;
					}
					final List<String> ids = timeSeries.stream()
						.map(FendoTimeSeries::getPath)
						.collect(Collectors.toList());
					final DumpConfigurationBuilder configBuilder = DumpConfigurationBuilder.getInstance()
						.setInterval(start, end)
						.setIncludedIds(ids);
					if (options.isChecked(ALIGN_TIMESTAMPS_PROP, req)) {
						Long itv = samplingInterval.getNumericalValue(req);
						if (itv != null) {
							final TimeUnit unit = samplingUnitSelector.getSelectedItem(req);
							itv = TimeUnit.MILLISECONDS.convert(itv, unit);
							configBuilder.setSamplingInterval(itv, options.isChecked(SINGLE_FILE_PROP, req));
						}
					}
					final DumpConfiguration configuration = configBuilder.build();
					final DataRecorderReference ref = slotsSelector.getSelectedItem(req);
					try {
						final CloseableDataRecorder instance = ref.getDataRecorder();
						String path = instance.getPath().normalize().toString();
						if (path.startsWith("./"))
							path = path.substring(2);
						download.setCustomFilename("fendodb_" + path.replace('/', '_'), req);
						download.setSource(output -> {
							try {
								FendoDbTools.zippedDump(instance, output, configuration);
//								FendoDbTools.dump(instance, output, configuration);
							} catch (IOException e) {
								LoggerFactory.getLogger(getClass()).warn("Failed to zip db: ",e);
							}
						}, true, "application/zip", req);
					} catch (IOException e) {
						download.disable(req);
						alert.showAlert("Failed to create db dump: " + e, false, req);
						return;
					}
					download.enable(req);
					alert.showAlert("Download starting", true, req);
				}
				
			};
			downloadTrigger.addDefaultStyle(ButtonData.BOOTSTRAP_BLUE);
			buildPage();
			setDependencies();
		}
	
		protected void buildPage() {
			int row = 0;
			page.append(header).append(alert).linebreak().append(new StaticTable(2, 3, new int[] {2,2,8})
				.setContent(row, 0, "Select FendoDB").setContent(row++, 1, slotsSelector)
				.setContent(row, 0, "Select tags").setContent(row++, 1, tagsSelect)
			).append(new StaticTable(2, 2, new int[] {2,10})
				.setContent(row=0, 0, "Select properties").setContent(row++, 1, propertiesBox)
				.setContent(row, 0, applySelection)
			);
			final Flexbox samplingFlex = new Flexbox(page, "samplingFlex", true);
			samplingFlex.addItem(samplingInterval, null).addItem(samplingUnitSelector, null);
			samplingFlex.setDefaultJustifyContent(JustifyContent.FLEX_LEFT);
			samplingInterval.setDefaultMargin("1em", false, false, false, true);
			
			final SimpleGrid grid = new SimpleGrid(page, "mainGrid", true)
					.addItem("Select timeseries", false, null).addItem(seriesSelector, false, null)
					.addItem("Start time", true, null).addItem(startPicker, false, null)
					.addItem("End time", true, null).addItem(endPicker, false, null)
					.addItem("Options", true, null).addItem(options, false, null)
					.addItem("Sampling interval", true, null).addItem(samplingFlex, false, null)
					.addItem(nrDatapointsTrigger, true, null).addItem(nrDatapoints, false, null)
					.addItem((String) null, true, null).addItem(downloadTrigger, false, null);
			grid.setDefaultAppendFillColumn(true);
			page.append(grid);
			page.linebreak().append(download);
		}
	
		protected final void setDependencies() {
			slotsSelector.triggerAction(tagsSelect, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			slotsSelector.triggerAction(seriesSelector, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST); // initial schedules selection
			slotsSelector.triggerAction(seriesSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST); // initial schedules selection
			tagsSelect.triggerAction(propertiesBox, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			tagsSelect.triggerAction(propertiesBox, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			applySelection.triggerAction(seriesSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			applySelection.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			nrDatapointsTrigger.triggerAction(nrDatapoints, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			if(download != null) {
				downloadTrigger.triggerAction(download, TriggeringAction.POST_REQUEST, FileDownloadData.GET_AND_STARTDOWNLOAD);
				options.triggerAction(samplingInterval, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
				options.triggerAction(samplingUnitSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			}
			options.triggerAction(nrDatapoints, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			options.triggerAction(options, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			seriesSelector.triggerAction(startPicker, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(startPicker, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(endPicker, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(endPicker, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(downloadTrigger, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(downloadTrigger, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(nrDatapoints, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
			seriesSelector.triggerAction(nrDatapoints, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
		}
	
	
		private final static List<DropdownOption> getDropOptions(final Collection<String> values) {
			return values.stream()
				.map(val -> new DropdownOption(val, val, true))
				.collect(Collectors.toList());
		}
		
		@SuppressWarnings("serial")
		private final class CsvStartEndPicker extends StartEndDatepicker {

			public CsvStartEndPicker(WidgetPage<?> page, String id, boolean startOrEnd) {
				super(page, id, startOrEnd);
			}
			
			@Override
			protected Stream<ReadOnlyTimeSeries> getSelectedSchedules(OgemaHttpRequest req) {
				return seriesSelector.getSelectedItems(req).stream().map(Function.identity());
			}
			
			@Override
			protected boolean autoModeEnabled(OgemaHttpRequest req) {
				return !options.isChecked(DATES_FIXED_PROP, req);
			}
			
		}
	
	}
	
	
}
