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

import com.google.common.collect.Lists;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;

public class MultiInputSplit extends InputSplit implements Writable {

  private final List<InputSplit> splits;
  private String[] locations;
  private long length;

  public MultiInputSplit() {
    splits = Lists.newArrayList();
    length = 0;
  }

  public MultiInputSplit(String location) {
    this();
    this.locations = new String[] { location };
  }

  @Override
  public long getLength() throws IOException, InterruptedException {
    return length;
  }

  @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="EI_EXPOSE_REP",
    justification="Safe operation defined by InputSplit interface")
  @Override
  public String[] getLocations() throws IOException, InterruptedException {
    return locations;
  }

  public void addSplit(InputSplit split) throws IOException, InterruptedException {
    splits.add(split);
    length += split.getLength();
  }

  public InputSplit getSplit(int index) {
    return splits.get(index);
  }

  public boolean hasSplit(int index) {
    return splits.size() > index;
  }

  public int getNumSplits() {
    return splits.size();
  }

  @Override
  public void write(DataOutput out) throws IOException {
    out.writeUTF(splits.get(0).getClass().getName());
    
    out.writeInt(splits.size());
    for (InputSplit split : splits) {
      if (split instanceof Writable) {
        ((Writable)split).write(out);
      } else {
        throw new RuntimeException("Class " + split.getClass().getName()
          + " does not implement " + Writable.class.getName());
      }
    }

    out.writeLong(length);
  }

  @Override
  public void readFields(DataInput in) throws IOException {

    String splitClassName = in.readUTF();
    Class<?> splitClass = new Configuration().getClassByNameOrNull(splitClassName);
    if (splitClass == null) {
      throw new RuntimeException("Class " + splitClassName + " not found");
    }

    if (!Writable.class.isAssignableFrom(splitClass)) {
      throw new RuntimeException("Class " + splitClassName
        + " does not implement " + Writable.class.getName());
    }

    Class<? extends Writable> writableClass = splitClass.asSubclass(Writable.class);

    int nSplits = in.readInt();

    for (int i = 0; i < nSplits; i++) {
      Writable writable = newWritable(writableClass);
      writable.readFields(in);
      splits.add(asInputSplit(writable));
    }

    this.length = in.readLong();
  }

  private Writable newWritable(Class<? extends Writable> writableClass) {
    try {
      return writableClass.newInstance();
    } catch (InstantiationException ex) {
      throw new RuntimeException(ex);
    } catch (IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }
  }

  private InputSplit asInputSplit(Writable writable) {
    if (writable instanceof InputSplit) {
      return (InputSplit) writable;
    }
    throw new RuntimeException("Class " + writable.getClass().getName()
      + " is not a sub-class of " + InputSplit.class.getName());
  }
  
}
