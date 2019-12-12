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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.ogema.core.application.ApplicationManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.importutils.FendoDBImportUtils;
import org.smartrplace.logging.fendodb.visualisation.CsvExportPage.CsvExportPageInit;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.buttonconfirm.ButtonConfirm;
import de.iwes.widgets.html.fileupload.FileUpload;
import de.iwes.widgets.html.fileupload.FileUploadListener;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.html5.Flexbox;
import de.iwes.widgets.html.html5.SimpleGrid;
import de.iwes.widgets.template.DefaultDisplayTemplate;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=csvimport.html",
				LazyWidgetPage.MENU_ENTRY + "=CSV import"
		}
)
public class CsvImportPage implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new CsvImportPageInit(page, factory, appMan);
	}
	
	public enum ImportFormat {
		FENDO_EXPORT,
		EMONCMS
	}
	private static class UploadData {
		long startTime;
		long endTime;
		ImportFormat format;
		FendoTimeSeries ts;
		public UploadData(long startTime, long endTime, ImportFormat format, FendoTimeSeries ts) {
			this.startTime = startTime;
			this.endTime = endTime;
			this.format = format;
			this.ts = ts;
		}
	}
	private static class CsvImportPageInit extends CsvExportPageInit {
		private final TemplateDropdown<ImportFormat> formatDrop;
		private final Button startImportButton;
		private final FileUpload importUpload;
		private final ButtonConfirm deleteIntervalButton;
		
		CsvImportPageInit(WidgetPage<?> page, FendoDbFactory factory, ApplicationManager am) {
			super(page, factory, am, true, "_import");
			page.setTitle("FendoDB CSV import");
			this.header = new Header(page, "header"+subId, "CSV import");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_LEFT);
			header.setDefaultColor("red");
			formatDrop = new TemplateDropdown<ImportFormat>(page, "formatDrop");
			formatDrop.setTemplate(new DefaultDisplayTemplate<ImportFormat>() {
				@Override
				public String getLabel(ImportFormat object, OgemaLocale locale) {
					if(object == ImportFormat.FENDO_EXPORT) return "FendoDB Export format";
					if(object == ImportFormat.EMONCMS) return "Emoncms Export format";
					throw new IllegalStateException("Unknown format enum:"+object);
				}
			});
			formatDrop.setDefaultItems(Arrays.asList(new ImportFormat[] {ImportFormat.FENDO_EXPORT, ImportFormat.EMONCMS}));
			
			importUpload = new FileUpload(page, "importUpload", am);
			FileUploadListener<UploadData> listener = new FileUploadListener<UploadData>() {

				@Override
				public void fileUploaded(FileItem fileItem, UploadData context, OgemaHttpRequest req) {
					FendoDBImportUtils.importCSVData(
							context.ts, context.format, fileItem);
				}
				
			};
			startImportButton = new Button(page, "startImportButton", "Import") {
				private static final long serialVersionUID = 1L;
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					List<FendoTimeSeries> tsList = seriesSelector.getSelectedItems(req);
					if(tsList.size() != 1) {
						startImportButton.disable(req);
						alert.showAlert("Currently CSV import is only supported for single time series", false, req);
						return;						
					}
					final long start = startPicker.getDateLong(req);
					final long end = endPicker.getDateLong(req);
					if (end < start) {
						startImportButton.disable(req);
						alert.showAlert("End time must be >= startTime", false, req);
						return;
					} else startImportButton.enable(req);
					final ImportFormat format = formatDrop.getSelectedItem(req);
					importUpload.registerListener(listener, new UploadData(start, end, format, tsList.get(0)), req);
					if(alert != null) alert.showAlert("Started Upload!", true, req);
				}
			};
	    	startImportButton.triggerAction(importUpload, TriggeringAction.POST_REQUEST, TriggeredAction.POST_REQUEST);
			if(alert != null) startImportButton.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			deleteIntervalButton = new ButtonConfirm(page, "deleteInterval", "Delete Interval of row") {
				private static final long serialVersionUID = 1L;
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					List<FendoTimeSeries> tsList = seriesSelector.getSelectedItems(req);
					final long start = startPicker.getDateLong(req);
					final long end = endPicker.getDateLong(req);
					if (end < start) {
						deleteIntervalButton.disable(req);
						alert.showAlert("End time must be >= startTime", false, req);
						return;
					} else deleteIntervalButton.enable(req);
					for(FendoTimeSeries fts: tsList) {
						FendoDBImportUtils.deleteInterval(start, end, fts);
					}
				}
				@Override
				public String getConfirmPopupTitle(OgemaHttpRequest req) {
					List<FendoTimeSeries> tsList = seriesSelector.getSelectedItems(req);
					String text;
					if(tsList.isEmpty())
						text = "(no timeseries selected)";
					else
						text = tsList.get(0).getPath()+" and "+(tsList.size()-1)+" more";
					return "Really delete "+text+" for selected interval?";
				}
			};
			
			buildPage();
			setDependencies();
		}
		
		@Override
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
			
			final SimpleGrid grid = new SimpleGrid(page, "mainGrid", true)
					.addItem("Select timeseries", false, null).addItem(seriesSelector, false, null)
					.addItem("Start time", true, null).addItem(startPicker, false, null)
					.addItem("End time", true, null).addItem(endPicker, false, null)
					.addItem("CSV Format", true, null).addItem(formatDrop, false, null)
					.addItem("Options", true, null).addItem(options, false, null)
					.addItem("Sampling interval", true, null).addItem(samplingFlex, false, null)
					.addItem(nrDatapointsTrigger, true, null).addItem(nrDatapoints, false, null)
					.addItem(startImportButton, true, null).addItem(deleteIntervalButton, false, null);
			grid.setDefaultAppendFillColumn(true);
			page.append(grid);
		}
	};

}
