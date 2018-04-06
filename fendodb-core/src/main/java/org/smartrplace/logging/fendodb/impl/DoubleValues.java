/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.ExecutionException;
import org.ogema.core.channelmanager.measurements.DoubleValue;

/**
 *
 * @author jlapp
 */
public class DoubleValues {

    private final static LoadingCache<Double, DoubleValue> CACHE
            = CacheBuilder.newBuilder().maximumSize(4096).build(new CacheLoader<Double, DoubleValue>() {
                @Override
                public DoubleValue load(Double key) throws Exception {
                    return new DoubleValue(key);
                }
            });

    public static DoubleValue of(float f) {
        try {
            return CACHE.get((double) f);
        } catch (ExecutionException ex) {
            return new DoubleValue(f);
        }
    }

    public static DoubleValue of(Double d) {
        try {
            return CACHE.get(d);
        } catch (ExecutionException ex) {
            return new DoubleValue(d);
        }
    }

}
