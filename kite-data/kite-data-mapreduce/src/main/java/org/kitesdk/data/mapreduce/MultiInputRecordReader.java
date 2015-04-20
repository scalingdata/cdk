/*
 * Copyright 2015 ScalingData
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kitesdk.data.mapreduce;

import java.io.IOException;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiInputRecordReader<E> extends RecordReader<E, Void> {

  private static final Logger LOG = LoggerFactory.getLogger(MultiInputRecordReader.class);

  private final InputFormat<E, Void> delegateInputFormat;
  private MultiInputSplit inputSplit;
  private TaskAttemptContext attemptContext;
  private int currentSplitIdx;
  private RecordReader<E, Void> delegateRecordReader;

  public MultiInputRecordReader(InputFormat<E, Void> delegate) {
    this.delegateInputFormat = delegate;
  }

  @Override
  public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
    if (split instanceof MultiInputSplit) {
      inputSplit = (MultiInputSplit) split;
      attemptContext = context;
      currentSplitIdx = 0;

      LOG.debug("Creating record reader for split: {}",
        inputSplit.getSplit(currentSplitIdx));
      delegateRecordReader = delegateInputFormat.createRecordReader(
        inputSplit.getSplit(currentSplitIdx), attemptContext);
      delegateRecordReader.initialize(inputSplit.getSplit(currentSplitIdx),
        attemptContext);
    } else {
      throw new RuntimeException("Unexpected type for InputSplit: "
        + split.getClass().getName());
    }
  }

  @Override
  public boolean nextKeyValue() throws IOException, InterruptedException {
    boolean hasNext = delegateRecordReader.nextKeyValue();

    while (!hasNext) {
      LOG.debug("Closing record reader for split: {}",
        inputSplit.getSplit(currentSplitIdx));
      delegateRecordReader.close();
      delegateRecordReader = null;

      currentSplitIdx++;
      if (!inputSplit.hasSplit(currentSplitIdx)) {
        return false;
      }

      LOG.debug("Creating record reader for split: {}",
        inputSplit.getSplit(currentSplitIdx));
      delegateRecordReader = delegateInputFormat.createRecordReader(
          inputSplit.getSplit(currentSplitIdx), attemptContext);
      delegateRecordReader.initialize(inputSplit.getSplit(currentSplitIdx),
        attemptContext);
      hasNext = delegateRecordReader.nextKeyValue();
    }

    return hasNext;
  }

  @Override
  public E getCurrentKey() throws IOException, InterruptedException {
    return delegateRecordReader.getCurrentKey();
  }

  @Override
  public Void getCurrentValue() throws IOException, InterruptedException {
    return delegateRecordReader.getCurrentValue();
  }

  @Override
  public float getProgress() throws IOException, InterruptedException {
    float baseProgress = ((float)currentSplitIdx)/((float)inputSplit.getNumSplits());

    float delegateProgress = 0.0f;
    if (delegateRecordReader != null) {
      delegateProgress = delegateRecordReader.getProgress();
    }
    float incrementalProgress = delegateProgress*1.0f/(inputSplit.getNumSplits());

    return baseProgress + incrementalProgress;
  }

  @Override
  public void close() throws IOException {
    if (delegateRecordReader != null) {
      LOG.warn("Delegate record reader not closed.");

      LOG.debug("Closing record reader for split: {}",
        inputSplit.getSplit(currentSplitIdx));
      delegateRecordReader.close();
      delegateRecordReader = null;

      currentSplitIdx++;
    }
  }
  
}
