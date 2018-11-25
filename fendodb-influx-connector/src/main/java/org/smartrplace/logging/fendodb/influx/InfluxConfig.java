package org.smartrplace.logging.fendodb.influx;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
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
	@AttributeDefinition(type=AttributeType.PASSWORD)
	String pw();
	String influxdb();
	/**
	 * Wildcard "*" allowed -> transfer all instances
	 * @return
	 */
	String fendodb();
	String measurementid();
	
	@AttributeDefinition(description = 
			"Either a long value (millis since 1st Jan 1970) or a String matching \"yyyy-MM-dd'T'HH:mm:ssZ\", "
			+ "where all parts beyond the year are optional")
	String startTime() default "";
	@AttributeDefinition(description = 
			"Either a long value (millis since 1st Jan 1970) or a String matching \"yyyy-MM-dd'T'HH:mm:ssZ\", "
			+ "where all parts beyond the year are optional")
	String endTime() default "";
	
}
