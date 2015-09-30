/*
 * Copyright 2015 Gilga Einziger. All Rights Reserved.
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
package com.clearspring.analytics.stream.frequency;

import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.admission.Frequency;
import com.typesafe.config.Config;

/**
 * A version of the TinyLFU sketch based on a regular conservative update sketch. The difference is
 * that a counter may not exceed a maximum and any time the sum of events reach a predefined values
 * we divide all counters by 2 in what is called a reset operation.
 * <p>
 * For more details see <a href="http://www.cs.technion.ac.il/~gilga/TinyLFU_PDP2014.pdf">TinyLFU: A
 * Highly Efficient Cache Admission Policy</a>.
 * <p>
 * The CountMinSketch parameters are described in <a href=
 * "https://github.com/twitter/algebird/blob/develop/algebird-core/src/main/scala/com/twitter/algebird/CountMinSketch.scala">
 * Twitter's implementation</a>.
 *
 * @author gilga1983@gmail.com (Gilga Einziger)
 */
public final class CountMin64TinyLfu<E> implements Frequency<E> {
  private static final int MAX_COUNT = 15;

  final CountMinSketch sketch;
  final int sampleSize;
  int size;

  public CountMin64TinyLfu(Config config) {
    BasicSettings settings = new BasicSettings(config);
    sketch = new ConservativeAddSketch(settings.tinyLfu().countMin64().eps(),
        settings.tinyLfu().countMin64().confidence(), settings.randomSeed());
    sampleSize = 10 * settings.maximumSize();
  }

  /** Returns the estimated usage frequency of the item. */
  @Override
  public int frequency(Object o) {
    return (int) sketch.estimateCount(o.hashCode());
  }

  @Override
  public void increment(Object o) {
    int hash = o.hashCode();
    if (sketch.estimateCount(hash) < MAX_COUNT) {
      sketch.add(hash, 1);
    }
    size += 1;
    resetIfNeeded();
  }

  private void resetIfNeeded() {
    if (size > sampleSize) {
      size /= 2;
      for (int i = 0; i < sketch.depth; i++) {
        for (int j = 0; j < sketch.width; j++) {
          size -= sketch.table[i][j] & 1;
          sketch.table[i][j] >>>= 1;
        }
      }
    }
  }
}
