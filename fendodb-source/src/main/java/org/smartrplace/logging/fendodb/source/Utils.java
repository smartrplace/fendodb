package org.smartrplace.logging.fendodb.source;

import java.io.IOException;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;

class Utils {
	
	static CloseableDataRecorder open(final DataRecorderReference ref) throws IOException {
		final FendoDbConfiguration cfg = FendoDbConfigurationBuilder.getInstance(ref.getConfiguration())
				.setReadOnlyMode(true)
				.build();
		return ref.getDataRecorder(cfg);
	}

}
