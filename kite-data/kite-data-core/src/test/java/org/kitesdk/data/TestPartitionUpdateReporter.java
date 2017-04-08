/*
 * Copyright 2017 Cloudera, Inc.
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

package org.kitesdk.data;

import static org.kitesdk.data.spi.filesystem.DatasetTestUtilities.USER_SCHEMA;

import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kitesdk.data.spi.DatasetRepository;
import org.kitesdk.data.spi.filesystem.FileSystemDatasetRepository;

import java.io.IOException;
import java.util.Set;

public class TestPartitionUpdateReporter {

  private Configuration conf;
  private FileSystem fileSystem;
  private Path testDirectory;
  private Dataset<Object> users;
  private DatasetRepository repo;

  @Before
  public void setUp() throws IOException {
    this.conf = new Configuration();
    this.fileSystem = FileSystem.get(conf);
    this.testDirectory = new Path(Files.createTempDir().getAbsolutePath());
    this.repo = new FileSystemDatasetRepository(conf, testDirectory);

    PartitionStrategy partitionStrategy = new PartitionStrategy.Builder()
      .hash("username", 2).build();
    users = repo.create("ns", "users",
      new DatasetDescriptor.Builder()
        .schema(USER_SCHEMA)
        .partitionStrategy(partitionStrategy)
        .build());
  }

  @After
  public void tearDown() throws IOException {
    fileSystem.delete(testDirectory, true);
  }

  @Test
  public void testPartitionsUpdated() throws IOException {
    final Set<String> updatedPartitions = Sets.newHashSet();
    GenericData.Record record = new GenericRecordBuilder(USER_SCHEMA)
      .set("username", "test1").set("email", "a@example.com").build();
    DatasetWriter<Object> writer = users.newWriter();
    if (writer instanceof PartitionUpdateReporter) {
      PartitionUpdateReporter updateReporter = (PartitionUpdateReporter) writer;
      updateReporter.register(new PartitionUpdateListener() {
        @Override
        public void partitionUpdated(String partition) {
          updatedPartitions.add(partition);
        }
      });
    }
    try {
      writer.write(record);
    } finally {
      Closeables.close(writer, true);
    }

    Assert.assertEquals("Unexpected number of updated partitions", 1, updatedPartitions.size());
    Assert.assertTrue(updatedPartitions.contains("username_hash=1"));
  }
}
