package org.smartrplace.logging.fendodb.importutils;

import org.apache.commons.fileupload.FileItem;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.visualisation.CsvImportPage.ImportFormat;

public class FendoDBImportUtils {
	/** Delete all full days of the FendoTimeSeries selected within the interval from the file system
	 * 
	 * @param startTime
	 * @param endTime
	 * @param fts time series to delete from
	 */
	public static void deleteInterval(long startTime, long endTime, FendoTimeSeries fts) {
		
	}
	
	/** Import data from file within the interval selected. In the range of the first value within the
	 * interval defined by startTime and endTime within the CSV data to the last value within the CSV data
	 * all existing values shall be deleted. If deleteEntireTimeRange is true then all existing data
	 * within startTime to endTime shall be deleted whether new data from the CSV file is filling up
	 * the range or not.
	 *  
	 * @param startTime
	 * @param endTime
	 * @param fts time series to import to
	 * @param deleteEntireTimeRange
	 * @param inputFormat identifier for the format 
	 * @param fileItem CSV data to process
	 * 
	 * TODO: Check if TimeseriesImport and/or ScheduleCsvImporter from ogema.tools could be used for this,
	 * 	probably extensions for the formats are required 
	 */
	public static void importCSVData(long startTime, long endTime, FendoTimeSeries fts,
			boolean deleteEntireTimeRange, ImportFormat inputFormat, FileItem fileItem) {
		
	}
}
