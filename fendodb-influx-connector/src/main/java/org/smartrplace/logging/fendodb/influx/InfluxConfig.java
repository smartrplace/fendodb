package org.smartrplace.logging.fendodb.influx;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Also requires constants
 * <ul>
 * 	 <li>org.smartrplace.tools.housekeeping.Period</li>
 *   <li>org.smartrplace.tools.housekeeping.Delay</li>
 *   <li>org.smartrplace.tools.housekeeping.Unit</li>
 * </ul>
 */
@ObjectClassDefinition
public @interface InfluxConfig {
	
	public static final String PID = "org.smartrplace.logging.fendodb.InfluxExport";

	String url();
	String user();
	String pw();
	String influxdb();
	String fendodb();
	String measurementid();
	
}
