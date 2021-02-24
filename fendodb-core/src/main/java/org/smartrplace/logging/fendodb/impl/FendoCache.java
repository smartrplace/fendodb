package org.smartrplace.logging.fendodb.impl;

import java.util.List;

import org.ogema.core.channelmanager.measurements.SampledValue;

interface FendoCache {
	
	FendoInstanceCache getCache(String encodedRecordedData, String filename);
	void clearCache();

	/**
	 * One instance per FileObject
	 */
	static interface FendoInstanceCache {

		void cache(List<SampledValue> values);
		void invalidate();
		List<SampledValue> getCache();

	}
	
	static FendoCache noopCache() {
		
		final FendoInstanceCache NOOP_INSTANCE_CACHE = new FendoInstanceCache() {
			
			@Override
			public void invalidate() {
			}
			
			@Override
			public List<SampledValue> getCache() {
				return null;
			}
			
			@Override
			public void cache(List<SampledValue> values) {			
			}
		};
		return new FendoCache(){

			@Override
			public FendoInstanceCache getCache(String encodedRecordedData, String filename) {
				return NOOP_INSTANCE_CACHE;
			}

			@Override
			public void clearCache() {
			}
			
		};
		
	}
	
}
