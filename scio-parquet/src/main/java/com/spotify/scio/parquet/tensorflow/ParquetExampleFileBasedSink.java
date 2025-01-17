/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.parquet.tensorflow;

import com.spotify.scio.parquet.BeamOutputFile;
import com.spotify.scio.parquet.WriterUtils;
import me.lyh.parquet.tensorflow.ExampleParquetWriter;
import me.lyh.parquet.tensorflow.Schema;
import org.apache.beam.sdk.io.FileBasedSink;
import org.apache.beam.sdk.io.fs.ResourceId;
import org.apache.beam.sdk.io.hadoop.SerializableConfiguration;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.util.MimeTypes;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.tensorflow.proto.example.Example;

import java.nio.channels.WritableByteChannel;

public class ParquetExampleFileBasedSink extends FileBasedSink<Example, Void, Example> {

  private final String schemaString;
  private final SerializableConfiguration conf;
  private final CompressionCodecName compression;

  public ParquetExampleFileBasedSink(
      ValueProvider<ResourceId> baseOutputFileName,
      FileBasedSink.DynamicDestinations<Example, Void, Example> dynamicDestinations,
      Schema schema,
      Configuration conf,
      CompressionCodecName compression) {
    super(baseOutputFileName, dynamicDestinations);
    this.schemaString = schema.toJson();
    this.conf = new SerializableConfiguration(conf);
    this.compression = compression;
  }

  @Override
  public FileBasedSink.WriteOperation<Void, Example> createWriteOperation() {
    return new ParquetExampleWriteOperation(this, schemaString, conf, compression);
  }

  // =======================================================================
  // WriteOperation
  // =======================================================================

  static class ParquetExampleWriteOperation extends FileBasedSink.WriteOperation<Void, Example> {
    private final String schemaString;
    private final SerializableConfiguration conf;
    private final CompressionCodecName compression;

    ParquetExampleWriteOperation(
        FileBasedSink<Example, Void, Example> sink,
        String schemaString,
        SerializableConfiguration conf,
        CompressionCodecName compression) {
      super(sink);
      this.schemaString = schemaString;
      this.conf = conf;
      this.compression = compression;
    }

    @Override
    public Writer<Void, Example> createWriter() throws Exception {
      return new ParquetExampleWriter(this, Schema.fromJson(schemaString), conf, compression);
    }
  }

  // =======================================================================
  // Writer
  // =======================================================================

  static class ParquetExampleWriter extends FileBasedSink.Writer<Void, Example> {

    private final Schema schema;
    private final SerializableConfiguration conf;
    private final CompressionCodecName compression;
    private ParquetWriter<Example> writer;

    public ParquetExampleWriter(
        FileBasedSink.WriteOperation<Void, Example> writeOperation,
        Schema schema,
        SerializableConfiguration conf,
        CompressionCodecName compression) {
      super(writeOperation, MimeTypes.BINARY);
      this.schema = schema;
      this.conf = conf;
      this.compression = compression;
    }

    @Override
    protected void prepareWrite(WritableByteChannel channel) throws Exception {
      BeamOutputFile outputFile = BeamOutputFile.of(channel);
      ExampleParquetWriter.Builder builder =
          ExampleParquetWriter.builder(outputFile).withSchema(schema);
      writer = WriterUtils.build(builder, conf.get(), compression);
    }

    @Override
    public void write(Example value) throws Exception {
      writer.write(value);
    }

    @Override
    protected void finishWrite() throws Exception {
      writer.close();
    }
  }
}
