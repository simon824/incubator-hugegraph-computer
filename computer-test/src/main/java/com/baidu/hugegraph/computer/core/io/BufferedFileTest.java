/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.computer.core.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.slf4j.Logger;

import com.baidu.hugegraph.computer.core.UnitTestBase;
import com.baidu.hugegraph.computer.core.common.Constants;
import com.baidu.hugegraph.testutil.Assert;
import com.baidu.hugegraph.util.Log;

public class BufferedFileTest {

    private static final Logger LOG = Log.logger(
                                          BufferedFileTest.class);
    private static final int BUFFER_SIZE = 128;

    @Test
    public void testConstructor() throws IOException {
        File file  = this.createTempFile();
        try {
            try (BufferedFileOutput output = new BufferedFileOutput(file)) {
                Assert.assertEquals(0, output.position());
            }
            try (BufferedFileInput input = new BufferedFileInput(file)) {
                Assert.assertEquals(0, input.position());
            }
            Assert.assertThrows(IllegalArgumentException.class, () -> {
                new BufferedFileOutput(new RandomAccessFile(file,
                                       Constants.FILE_MODE_WRITE),
                                       1);
            }, e -> {
                Assert.assertContains("The parameter bufferSize must be >= 8",
                                      e.getMessage());
            });
            Assert.assertThrows(IllegalArgumentException.class, () -> {
                new BufferedFileInput(new RandomAccessFile(file,
                                      Constants.FILE_MODE_READ),
                                      1);
            }, e -> {
                Assert.assertContains("The parameter bufferSize must be >= 8",
                                      e.getMessage());
            });
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testInt() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = -128; i <= 127; i++) {
                    output.writeInt(i);
                }
                output.writeInt(Integer.MAX_VALUE);
                output.writeInt(Integer.MIN_VALUE);
            }
            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = -128; i <= 127; i++) {
                    Assert.assertEquals(i, input.readInt());
                }
                Assert.assertEquals(Integer.MAX_VALUE, input.readInt());
                Assert.assertEquals(Integer.MIN_VALUE, input.readInt());
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testWriteIntWithPosition() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = -128; i <= 127; i++) {
                    output.writeInt(i);
                }
                output.writeInt(0, 1);
                output.writeInt(12, 2);
                // Next buffer
                output.writeInt(200, 3);
                // Previous buffer
                output.writeInt(100, 4);
                output.writeInt(Integer.MAX_VALUE);
                output.writeInt(Integer.MIN_VALUE);
            }

            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = 0; i < 256; i++) {
                    int expectValue = i - 128;
                    int position = i * 4;
                    int readValue = input.readInt();
                    if (position != 0 && position != 12 &&
                        position != 200 && position != 100) {
                        Assert.assertEquals(expectValue, readValue);
                    }
                }
                input.seek(0);
                Assert.assertEquals(1, input.readInt());
                input.seek(12);
                Assert.assertEquals(2, input.readInt());
                input.seek(200);
                Assert.assertEquals(3, input.readInt());
                input.seek(100);
                Assert.assertEquals(4, input.readInt());
                input.seek(256 * 4);
                Assert.assertEquals(Integer.MAX_VALUE, input.readInt());
                Assert.assertEquals(Integer.MIN_VALUE, input.readInt());
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testByteArray() throws IOException {
        int loopTimes = 129;
        byte[] array = UnitTestBase.randomBytes(10);
        File file = this.createTempFile();
        try (BufferedFileOutput output = this.createOutput(file)) {
            for (int i = 0; i < loopTimes; i++) {
                output.write(array);
            }
        }

        byte[] arrayRead = new byte[10];
        try (DataInputStream dis = new DataInputStream(
                                   new FileInputStream(file))) {
            for (int i = 0; i < loopTimes; i++) {
                dis.readFully(arrayRead);
                Assert.assertArrayEquals(array, arrayRead);
            }
        }
        try (BufferedFileInput input = this.createInput(file)) {
            for (int i = 0; i < loopTimes; i++) {
                input.readFully(arrayRead);
                Assert.assertArrayEquals(array, arrayRead);
            }
        }
        FileUtils.deleteQuietly(file);
    }

    @Test
    public void testLargeByteArray() throws IOException {
        int loopTimes = 10;
        int arraySize = 1280; // large than buffer size
        byte[] array = UnitTestBase.randomBytes(arraySize);
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = 0; i < loopTimes; i++) {
                    output.write(array);
                }
            }

            byte[] arrayRead = new byte[arraySize];
            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = 0; i < loopTimes; i++) {
                    input.readFully(arrayRead);
                    Assert.assertArrayEquals(array, arrayRead);
                }
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testInputSeekAtRandom() throws IOException {
        int size = 128;
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = 0; i < size; i++) {
                    output.writeInt(i);
                }
                for (int i = size; i >= 0; i--) {
                    output.seek(i * 4);
                    output.writeInt(size - i);
                }
            }
            Random random = new Random(1001);
            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = 0; i <= 10; i++) {
                    long position = 4 * random.nextInt(size);
                    input.seek(position);
                    Assert.assertEquals(size - position / 4, input.readInt());
                }
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testInputSeekOutRange() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                output.writeInt(1);
                output.writeInt(2);
                output.writeInt(3);
            }

            try (BufferedFileInput input = this.createInput(file)) {
                Assert.assertEquals(1, input.readInt());
                input.skip(4);
                Assert.assertThrows(EOFException.class, () -> {
                    input.seek(12); // Out of range
                }, e -> {
                    Assert.assertContains("reach the end of file",
                                          e.getMessage());
                });
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testOutputSeekOutRange() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                output.seek(100L);
                output.writeInt(1);
                output.seek(511L);
                output.writeInt(2);
            }

            try (BufferedFileInput input = this.createInput(file)) {
                input.seek(100L);
                Assert.assertEquals(1, input.readInt());
                input.seek(511L);
                Assert.assertEquals(2, input.readInt());
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testSeekAtEnd() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = -128; i <= 127; i++) {
                    output.writeInt(i);
                }
                // Overwrite last 2 elements
                output.seek(256 * 4 - 8);
                output.writeInt(Integer.MAX_VALUE);
                output.writeInt(Integer.MIN_VALUE);
            }

            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = -128; i <= 125; i++) {
                    Assert.assertEquals(i, input.readInt());
                }
                Assert.assertEquals(Integer.MAX_VALUE, input.readInt());
                Assert.assertEquals(Integer.MIN_VALUE, input.readInt());
                input.seek(input.position() - 8);
                Assert.assertEquals(Integer.MAX_VALUE, input.readInt());
                Assert.assertEquals(Integer.MIN_VALUE, input.readInt());
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testSkip() throws IOException {
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = -128; i <= 127; i++) {
                    output.writeByte(i);
                }
                output.skip(4L);
                output.writeByte(127);
                output.skip(4L);
                output.skip(1280L);
                output.writeByte(1);
                Assert.assertThrows(IllegalArgumentException.class, () -> {
                    output.skip(-1);
                }, e -> {
                    e.getMessage().contains("The parameter bytesToSkip must " +
                                            "be >= 0");
                });
            }

            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = -128; i <= 127; i++) {
                    Assert.assertEquals(i, input.readByte());
                }
                input.skip(4);
                Assert.assertEquals(127, input.readByte());
                input.skip(4);
                input.skip(1280);
                Assert.assertEquals((byte) 1, input.readByte());
                Assert.assertThrows(IllegalArgumentException.class, () -> {
                    input.skip(-1);
                }, e -> {
                    e.getMessage().contains("The parameter bytesToSkip must " +
                                            "be >= 0");
                });
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testPosition() throws IOException {
        int size = 1024;
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = 0; i < size; i++) {
                    Assert.assertEquals(i * 4, output.position());
                    output.writeInt(i);
                }
            }
            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = 0; i < size; i++) {
                    Assert.assertEquals(i * 4, input.position());
                    Assert.assertEquals(i, input.readInt());
                }

                Random random = new Random();
                for (int i = 0; i < 10; i++) {
                    long position = 4 * random.nextInt(size);
                    input.seek(position);
                    Assert.assertEquals(position / 4, input.readInt());
                    Assert.assertEquals(position + 4, input.position());
                }
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testAvailable() throws IOException {
        int size = 1024;
        File file = this.createTempFile();
        try {
            try (BufferedFileOutput output = this.createOutput(file)) {
                for (int i = 0; i < size; i++) {
                    output.writeInt(i);
                }
            }
            try (BufferedFileInput input = this.createInput(file)) {
                for (int i = 0; i < size; i++) {
                    Assert.assertEquals(4096 - i * 4, input.available());
                    Assert.assertEquals(i, input.readInt());
                }
            }
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testLongPerformanceUnsafe() throws IOException {
        long size = 1024;
        long startTime;
        long endTime;
        long time;
        File file = new File("long-unsafe.bin");
        try {
            startTime = System.currentTimeMillis();
            try (BufferedFileOutput output = new BufferedFileOutput(file)) {
                for (long i = 0; i < size / 8; i++) {
                    output.writeLong(i);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Write {} bytes use BufferedFileOutput.writeLong " +
                     "takes {} ms", size, time);

            startTime = System.currentTimeMillis();
            try (BufferedFileInput input = new BufferedFileInput(file)) {
                for (long i = 0; i < size / 8; i++) {
                    input.readLong();
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use BufferedFileInput.readLong " +
                     "takes {} ms", size, time);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testLongPerformanceNormal() throws IOException {
        long size = 1024;
        long startTime;
        long endTime;
        long time;

        File file = new File("long-data.bin");
        try {
            startTime = System.currentTimeMillis();
            try (DataOutputStream output = new DataOutputStream(
                                           new BufferedOutputStream(
                                           new FileOutputStream(file)))) {
                for (long i = 0; i < size / 8; i++) {
                    output.writeLong(i);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Write {} bytes use DataOutputStream.writeLong" +
                     " takes {} ms", size, time);

            startTime = System.currentTimeMillis();
            try (DataInputStream input = new DataInputStream(
                                         new BufferedInputStream(
                                         new FileInputStream(file)))) {
                for (long i = 0; i < size / 8; i++) {
                    input.readLong();
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use DataInputStream.readLong " +
                     "takes {} ms", size, time);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testIntPerformanceIntUnsafe() throws IOException {
        int size = 1024;
        long startTime;
        long endTime;
        File file = new File("int-unsafe.bin");
        try {
            startTime = System.currentTimeMillis();
            try (BufferedFileOutput output = new BufferedFileOutput(file)) {
                for (int i = 0; i < size / 4; i++) {
                    output.writeInt(i);
                }
            }
            endTime = System.currentTimeMillis();
            long time = endTime - startTime;
            LOG.info("Write {} bytes use BufferedFileOutput.writeInt " +
                     "takes {} ms", size, time);

            startTime = System.currentTimeMillis();
            try (BufferedFileInput input = new BufferedFileInput(file)) {
                for (int i = 0; i < size / 4; i++) {
                    input.readInt();
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use BufferedFileInput.readInt " +
                     "takes {} ms", size, time);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testIntPerformanceNormal() throws IOException {
        int size = 1024;
        long startTime;
        long endTime;
        long time;
        File dataFile = new File("int-data-out.bin");
        try {
            startTime = System.currentTimeMillis();
            try (DataOutputStream output = new DataOutputStream(
                                           new BufferedOutputStream(
                                           new FileOutputStream(dataFile)))) {
                for (int i = 0; i < size / 4; i++) {
                    output.writeInt(i);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Write {} bytes use DataOutputStream.writeInt " +
                     "takes {} ms", size, time);

            startTime = System.currentTimeMillis();
            try (DataInputStream input = new DataInputStream(
                                         new BufferedInputStream(
                                         new FileInputStream(dataFile)))) {
                for (int i = 0; i < size / 4; i++) {
                    input.readInt();
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use DataInputStream.readInt " +
                     "takes {} ms", size, time);
        } finally {
            FileUtils.deleteQuietly(dataFile);
        }
    }

    @Test
    public void testByteArrayPerformanceUnsafe() throws IOException {
        int size = 1024;
        long startTime;
        long endTime;
        long time;
        byte[] writeArray = UnitTestBase.randomBytes(16);
        byte[] readArray = new byte[16];

        File file = new File("int-unsafe-out.bin");
        try {
            startTime = System.currentTimeMillis();
            try (BufferedFileOutput output = new BufferedFileOutput(file)) {
                for (int i = 0; i < size / writeArray.length; i++) {
                    output.write(writeArray);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Write {} bytes use BufferedFileOutput" +
                     ".write takes {} ms", size, time);

            startTime = System.currentTimeMillis();
            try (BufferedFileInput input = new BufferedFileInput(file)) {
                for (int i = 0; i < size / readArray.length; i++) {
                    input.readFully(readArray);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use BufferedFileInput.readFully " +
                     "takes {} ms", size, time);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Test
    public void testByteArrayPerformanceNormal() throws IOException {
        int size = 1024;
        long startTime;
        long endTime;
        long time;
        byte[] writeArray = UnitTestBase.randomBytes(16);
        byte[] readArray = new byte[16];
        File dataFile = new File("int-data-out.bin");
        try {
            startTime = System.currentTimeMillis();
            try (DataOutputStream output = new DataOutputStream(
                                           new BufferedOutputStream(
                                           new FileOutputStream(dataFile)))) {
                for (int i = 0; i < size / writeArray.length; i++) {
                    output.write(writeArray);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Write {} bytes use DataOutputStream.write takes {} ms",
                     size, time);

            startTime = System.currentTimeMillis();
            try (DataInputStream input = new DataInputStream(
                                         new BufferedInputStream(
                                         new FileInputStream(dataFile)))) {
                for (int i = 0; i < size / writeArray.length; i++) {
                    input.readFully(readArray);
                }
            }
            endTime = System.currentTimeMillis();
            time = endTime - startTime;
            LOG.info("Read {} bytes use DataInputStream.readFully takes {} ms",
                     size, time);
        } finally {
            FileUtils.deleteQuietly(dataFile);
        }
    }

    private static File createTempFile() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), null);
    }

    private static BufferedFileOutput createOutput(File file)
                                      throws FileNotFoundException {
        return new BufferedFileOutput(new RandomAccessFile(file,
                                      Constants.FILE_MODE_WRITE),
                                      BUFFER_SIZE);
    }

    private static BufferedFileInput createInput(File file)
                                                 throws IOException {
        return new BufferedFileInput(new RandomAccessFile(file,
                                     Constants.FILE_MODE_READ),
                                     BUFFER_SIZE);
    }
}