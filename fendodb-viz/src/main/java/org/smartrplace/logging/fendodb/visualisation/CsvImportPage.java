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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.smartrplace.logging.fendodb.visualisation.CsvExportPage.SelectionMode;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.fileupload.FileUpload;
import de.iwes.widgets.html.fileupload.FileUploadListener;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.form.label.Label;
import de.iwes.widgets.html.form.label.LabelData;
import de.iwes.widgets.html.form.textfield.TextField;
import de.iwes.widgets.template.DefaultDisplayTemplate;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=csvimport.html",
				LazyWidgetPage.MENU_ENTRY + "=CSV Import"
		}
)
public class CsvImportPage implements LazyWidgetPage {

	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		page.getMenuConfiguration().setShowMessages(false);
		page.getMenuConfiguration().setLanguageSelectionVisible(false);
		new CsvImportPageInit(page, factory, appMan);
	}
	
	public enum ImportFormat {
		FENDO_EXPORT,
		EMONCMS
	}
	public enum SourceType {
		FILE_UPLOAD,
		FILE_ON_SERVER
	}
	private static class UploadData {
		//long startTime;
		//long endTime;
		ImportFormat format;
		FendoTimeSeries ts;
		public UploadData(ImportFormat format, FendoTimeSeries ts) {
			//this.startTime = startTime;
			//this.endTime = endTime;
			this.format = format;
			this.ts = ts;
		}
	}
	private static class CsvImportPageInit extends CsvExportPageInit {
		private final TemplateDropdown<ImportFormat> formatDrop;
		private final Button startImportButton;
		private final FileUpload importUpload;
		private final TemplateDropdown<SourceType> sourceDrop;
		private final TextField sourceFile;
		//private final ButtonConfirm deleteIntervalButton;
		
		CsvImportPageInit(WidgetPage<?> page, FendoDbFactory factory, ApplicationManager am) {
			super(page, factory, am, true, "_import", SelectionMode.FILTER, false, true);
			page.setTitle("FendoDB CSV import");
			this.header = new Header(page, "header"+subId, "CSV Import");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_LEFT);
			//header.setDefaultColor("red");
			
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
			
			this.sourceDrop = new TemplateDropdown<SourceType>(page, "sourceDrop");
			sourceDrop.setTemplate(new DefaultDisplayTemplate<SourceType>() {
				@Override
				public String getLabel(SourceType object, OgemaLocale locale) {
					if(object == SourceType.FILE_UPLOAD) return "Upload file to import";
					if(object == SourceType.FILE_ON_SERVER) return "Use file on server in <OGEMA-Rundir>/csvImport";
					throw new IllegalStateException("Unknown format enum:"+object);
				}
			});
			sourceDrop.setDefaultItems(Arrays.asList(new SourceType[] {SourceType.FILE_UPLOAD, SourceType.FILE_ON_SERVER}));
			this.sourceFile = new TextField(page, "sourceFile") {
				private static final long serialVersionUID = 1L;

				@Override
				public void onGET(OgemaHttpRequest req) {
					if(sourceDrop.getSelectedItem(req) == SourceType.FILE_UPLOAD) {
						setWidgetVisibility(false, req);
						return;
					}
					setWidgetVisibility(true, req);
					super.onGET(req);
				}				
			};

			importUpload = new FileUpload(page, "importUpload", am);
			FileUploadListener<UploadData> listener = new FileUploadListener<UploadData>() {

				@Override
				public void fileUploaded(FileItem fileItem, UploadData context, OgemaHttpRequest req) {
					String msg = FendoDBImportUtils.importCSVData(
							context.ts, context.format, fileItem);
                    alert.showAlert(msg, !msg.contains("ERROR"), req);
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

					final ImportFormat format = formatDrop.getSelectedItem(req);
					if(sourceDrop.getSelectedItem(req) == SourceType.FILE_UPLOAD) {
						importUpload.registerListener(listener, new UploadData(format, tsList.get(0)), req);
					} else {
						Path filePath = Paths.get("csvImport", sourceFile.getValue(req));
						InputStream is;
						try {
							is = Files.newInputStream(filePath);
							String msg = FendoDBImportUtils.importCSVData(
									tsList.get(0), format, is);
		                    alert.showAlert(msg, !msg.contains("ERROR"), req);						
						} catch (IOException e) {
		                    alert.showAlert("Could not open:"+filePath, false, req);						
						}
					}
					if(alert != null) alert.showAlert("Finished Upload!", true, req);
				}
			};
	    	startImportButton.triggerAction(importUpload, TriggeringAction.POST_REQUEST, TriggeredAction.POST_REQUEST);
			if(alert != null) startImportButton.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			
			buildPage();
			setDependencies();
		}
		
		@Override
		protected void buildPage() {
			int row = 0;
			Label infoLabel = new Label(page, "infoLabel", simpleImport?"Note: Importing data into time series that have ongoing data"
					+ "collection from a sensor or actor configured may require stopping the data acquisition during import. "
					+ "Please contact Smartrplace support for assistence in such a case.":
				"Note: Importing CSV files requires that the time series is empty before the"
					+ " start time of the CSV files. See https://github.com/smartrplace/fendodb/wiki/Data-Manipulation-and-Import "
					+ "for details."); 
			infoLabel.addDefaultStyle(LabelData.BOOTSTRAP_RED);
			
			page.append(header).append(alert).linebreak().append(new StaticTable(2, 4, new int[] {2,2,2,6})
					.setContent(row, 0, simpleImport?"Select database":"Select FendoDB").setContent(row, 1, slotsSelector).setContent(row++, 2, selectionMode)
					.setContent(row, 0, simpleImport?"Search String":"Select tags").setContent(row, 1, tagsSelect).setContent(row++, 1, filterString)
			);
			if(!simpleImport) {
				page.append(new StaticTable(2, 2, new int[] {2,10})
					.setContent(row=0, 0, "Select properties").setContent(row++, 1, propertiesBox)
					.setContent(row, 0, applySelection).setContent(row, 1, infoLabel)
				);
			} else {
				page.append(new StaticTable(1, 2, new int[] {2,10})
						.setContent(0, 0, applySelection).setContent(0, 1, infoLabel)
					);				
			}
			
			//final Flexbox samplingFlex = new Flexbox(page, "samplingFlex", true);
			
			/*final SimpleGrid grid = new SimpleGrid(page, "mainGrid", true)
					.addItem("Select timeseries", false, null).addItem(seriesSelector, false, null)
					//.addItem("Start time", true, null).addItem(startPicker, false, null)
					//.addItem("End time", true, null).addItem(endPicker, false, null)
					.addItem("CSV Format", true, null).addItem(formatDrop, false, null)
					.addItem("Options", true, null).addItem(options, false, null)
					.addItem(sourceDrop, true, null).addItem(sourceFile, false, null)
					.addItem(nrDatapointsTrigger, true, null).addItem(nrDatapoints, false, null)
					.addItem(startImportButton, true, null).addItem(importUpload, false, null);
			grid.setDefaultAppendFillColumn(true);*/
			row = 0;
			final StaticTable grid = new StaticTable(5, 2)
					.setContent(row, 0, "Select timeseries").setContent(row++, 1, seriesSelector)
					.setContent(row, 0, "CSV Format").setContent(row++, 1, formatDrop)
					//.setContent(row, 0, "Options").setContent(row++, 1, options)
					.setContent(row, 0, sourceDrop).setContent(row++, 1, sourceFile)
					.setContent(row, 0, nrDatapointsTrigger).setContent(row++, 1, nrDatapoints)
					.setContent(row, 0, startImportButton).setContent(row++, 1, importUpload);
			page.append(grid);
			
			sourceDrop.triggerOnPOST(sourceFile);
		}
	};

}
