package org.smartrplace.logging.fendodb.importutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.fileupload.FileItem;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.DataRecorderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.visualisation.CsvImportPage.ImportFormat;

public class FendoDBImportUtils {

    static final Logger LOGGER = LoggerFactory.getLogger(FendoDBImportUtils.class);

    /**
     * Delete all full days of the FendoTimeSeries selected within the interval
     * from the file system
     *
     * @param startTime
     * @param endTime
     * @param fts time series to delete from
     */
    public static void deleteInterval(long startTime, long endTime, FendoTimeSeries fts) {

    }

    /**
     * Import data from file within the interval selected. In the range of the
     * first value within the interval defined by startTime and endTime within
     * the CSV data to the last value within the CSV data all existing values
     * shall be deleted. If deleteEntireTimeRange is true then all existing data
     * within startTime to endTime shall be deleted whether new data from the
     * CSV file is filling up the range or not.
     *
     * @param fts time series to import to, must not contain data older than the
     * earliest imported data
     * @param inputFormat identifier for the format
     * @param fileItem CSV data to process
     *
     * TODO: Check if TimeseriesImport and/or ScheduleCsvImporter from
     * ogema.tools could be used for this, probably extensions for the formats
     * are required
     */
    public static void importCSVData(FendoTimeSeries fts,
            ImportFormat inputFormat, FileItem fileItem) {
        switch (inputFormat) {
            case EMONCMS : {
                try {
                List<SampledValue> values = readEmonCmsCSV(fileItem.getInputStream());
                LOGGER.info("inserting {} values from CSV import into {}", values.size(), fts.getPath());
                fts.insertValues(values);
                } catch (IOException | DataRecorderException ex) {
                    LOGGER.error("CVS import failed", ex);
                }
                break;
            }
            default : {}
        }
    }

    public static List<SampledValue> readEmonCmsCSV(InputStream is) {
        List<SampledValue> rval = new ArrayList<>();
        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(r)) {
            String line = null;
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
            int count = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String a[] = line.split(",", 2);
                if (a.length != 2) {
                    LOGGER.warn("unparseable line in CVS input: {}", line);
                }
                try {
                    long timestamp = Long.parseLong(a[0]);
                    float value = nf.parse(a[1]).floatValue();
                    rval.add(new SampledValue(new FloatValue(value), timestamp, Quality.GOOD));
                    count++;
                } catch (ParseException | NumberFormatException e) {
                    LOGGER.warn("unparseable line in CVS input: {}", line, e);
                }
            }
        } catch (IOException ioex) {
            LOGGER.error("import failed", ioex);
        }
        return rval;
    }

}
