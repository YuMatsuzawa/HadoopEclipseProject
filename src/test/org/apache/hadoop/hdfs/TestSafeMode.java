/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.FSConstants.SafeModeAction;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.hdfs.server.namenode.SafeModeException;
import org.apache.hadoop.ipc.RemoteException;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test makes sure that if SafeMode is manually entered, NameNode does not
 * come out of safe mode even after the startup safemode conditions are met.
 */
public class TestSafeMode {
  
  static Log LOG = LogFactory.getLog(TestSafeMode.class);
  
  @Test
  public void testManualSafeMode() throws IOException {
    MiniDFSCluster cluster = null;
    FileSystem fs = null;
    try {
      Configuration conf = new Configuration();
      // disable safemode extension to make the test run faster.
      conf.set("dfs.safemode.extension", "1");
      cluster = new MiniDFSCluster(conf, 1, true, null);
      cluster.waitActive();
      
      fs = cluster.getFileSystem();
      Path file1 = new Path("/tmp/testManualSafeMode/file1");
      Path file2 = new Path("/tmp/testManualSafeMode/file2");
      
      LOG.info("Created file1 and file2.");
      
      // create two files with one block each.
      DFSTestUtil.createFile(fs, file1, 1000, (short)1, 0);
      DFSTestUtil.createFile(fs, file2, 2000, (short)1, 0);    
      cluster.shutdown();
      
      // now bring up just the NameNode.
      cluster = new MiniDFSCluster(conf, 0, false, null);
      cluster.waitActive();
      
      LOG.info("Restarted cluster with just the NameNode");
      
      NameNode namenode = cluster.getNameNode();
      
      assertTrue("No datanode is started. Should be in SafeMode", 
                 namenode.isInSafeMode());
      
      // manually set safemode.
      namenode.setSafeMode(SafeModeAction.SAFEMODE_ENTER);
      
      // now bring up the datanode and wait for it to be active.
      cluster.startDataNodes(conf, 1, true, null, null);
      cluster.waitActive();
      
      LOG.info("Datanode is started.");
      
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ignored) {}
      
      assertTrue("should still be in SafeMode", namenode.isInSafeMode());
      
      namenode.setSafeMode(SafeModeAction.SAFEMODE_LEAVE);
      assertFalse("should not be in SafeMode", namenode.isInSafeMode());
    } finally {
      if(fs != null) fs.close();
      if(cluster!= null) cluster.shutdown();
    }
  }

  @Test
  public void testSafeModeWhenZeroBlockLocations() throws IOException {
    MiniDFSCluster cluster = null;
    FileSystem fs = null;
    try {
      Configuration conf = new Configuration();
      // disable safemode extension to make the test run faster.
      conf.set("dfs.safemode.extension", "1");
      cluster = new MiniDFSCluster(conf, 1, true, null);
      cluster.waitActive();

      fs = cluster.getFileSystem();
      Path file1 = new Path("/tmp/testManualSafeMode/file1");
      Path file2 = new Path("/tmp/testManualSafeMode/file2");

      LOG.info("Created file1 and file2.");

      // create two files with one block each.
      DFSTestUtil.createFile(fs, file1, 1000, (short) 1, 0);
      DFSTestUtil.createFile(fs, file2, 2000, (short) 1, 0);
      checkGetBlockLocationsWorks(fs, file1);

      NameNode namenode = cluster.getNameNode();
      namenode.setSafeMode(SafeModeAction.SAFEMODE_ENTER);
      Assert.assertTrue("should still be in SafeMode", namenode.isInSafeMode());
      // getBlock locations should still work since block locations exists
      checkGetBlockLocationsWorks(fs, file1);
      namenode.setSafeMode(SafeModeAction.SAFEMODE_LEAVE);
      assertFalse("should not be in SafeMode", namenode.isInSafeMode());
      Assert.assertFalse("should not be in SafeMode", namenode.isInSafeMode());

      // Now 2nd part of the tests where there aren't block locations
      cluster.shutdownDataNodes();
      cluster.shutdownNameNode();

      // now bring up just the NameNode.
      cluster.restartNameNode();
      cluster.waitActive();

      LOG.info("Restarted cluster with just the NameNode");

      namenode = cluster.getNameNode();

      Assert.assertTrue("No datanode is started. Should be in SafeMode",
          namenode.isInSafeMode());
      FileStatus stat = fs.getFileStatus(file1);
      try {
        fs.getFileBlockLocations(stat, 0, 1000);
        Assert.assertTrue("Should have got safemode exception", false);
      } catch (SafeModeException e) {
        // as expected
      } catch (RemoteException re) {
        if (!re.getClassName().equals(SafeModeException.class.getName()))
          Assert.assertTrue("Should have got safemode exception", false);
      }

      namenode.setSafeMode(SafeModeAction.SAFEMODE_LEAVE);
      Assert.assertFalse("Should not be in safemode", namenode.isInSafeMode());
      checkGetBlockLocationsWorks(fs, file1);

    } finally {
      if (fs != null)
        fs.close();
      if (cluster != null)
        cluster.shutdown();
    }
  }

  void checkGetBlockLocationsWorks(FileSystem fs, Path fileName)
      throws IOException {
    FileStatus stat = fs.getFileStatus(fileName);
    try {
      fs.getFileBlockLocations(stat, 0, 1000);
    } catch (SafeModeException e) {
      Assert.assertTrue("Should have not got safemode exception", false);
    } catch (RemoteException re) {
      Assert.assertTrue("Should have not got safemode exception", false);
    }
  }
}
