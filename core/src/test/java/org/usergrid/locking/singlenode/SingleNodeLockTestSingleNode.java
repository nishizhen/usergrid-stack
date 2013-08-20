/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.locking.singlenode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.locking.Lock;
import org.usergrid.locking.LockManager;
import org.usergrid.locking.exception.UGLockException;

public class SingleNodeLockTestSingleNode {

  private static final Logger LOG = LoggerFactory.getLogger(SingleNodeLockTestSingleNode.class);

  private static LockManager manager;

  private ExecutorService pool;

  private  ReentrantLock tsLock = new ReentrantLock();

  @BeforeClass
  public static void setUp() throws Exception {

    manager = new SingleNodeLockManagerImpl();

  }

   @Before
    public void start()
    {
        // Create a different thread to lock the same node, that is held by the main thread.
        pool = Executors.newFixedThreadPool(1);
    }

  @After
  public void tearDown() throws Exception {
    pool.shutdownNow();
  }

  /**
   * Locks a path and launches a thread which also locks the same path.
   * 
   * @throws UGLockException
   * 
   */
  @Test
  public void testLock() throws InterruptedException, ExecutionException, UGLockException {

    final UUID application = UUID.randomUUID();
    final UUID entity = UUID.randomUUID();

    LOG.info("Locking:" + application.toString() + "/" + entity.toString());

    // Lock a node twice to test reentrancy and validate.
    Lock lock = manager.createLock(application, entity.toString());
    lock.lock();
    lock.lock();

    boolean wasLocked = lockInDifferentThread(application, entity);
    Assert.assertEquals(false, wasLocked);

    // Unlock once
    lock.unlock();

    // Try from the thread expecting to fail since we still hold one reentrant
    // lock.
    wasLocked = lockInDifferentThread(application, entity);
    Assert.assertEquals(false, wasLocked);

    // Unlock completely
    LOG.info("Releasing lock:" + application.toString() + "/" + entity.toString());
    lock.unlock();

    // Try to effectively get the lock from the thread since the current one has
    // already released it.
    wasLocked = lockInDifferentThread(application, entity);
    Assert.assertEquals(true, wasLocked);
  }

  /**
   * Locks a couple of times and try to clean up. Later oin another thread
   * successfully acquire the lock
   * 
   */
  @Test
  public void testLock2() throws InterruptedException, ExecutionException, UGLockException {

    final UUID application = UUID.randomUUID();
    final UUID entity = UUID.randomUUID();
    final UUID entity2 = UUID.randomUUID();

    LOG.info("Locking:" + application.toString() + "/" + entity.toString());

    // Acquire to locks. One of them twice.
    Lock lock = manager.createLock(application, entity.toString());
    lock.lock();
    lock.lock();

    Lock second = manager.createLock(application, entity2.toString());
    second.lock();

    // Cleanup the locks for main thread
    LOG.info("Cleaning up locks for current thread...");
    lock.unlock();
    lock.unlock();

    second.unlock();

    boolean locked = lockInDifferentThread(application, entity);
    assertTrue(locked);

    locked = lockInDifferentThread(application, entity2);
    assertTrue(locked);
  }

 
  
  /**
   * Acquires a lock in a different thread.
   * 
   * @param application
   * @param entity
   * @return
   */
  private boolean lockInDifferentThread(final UUID application, final UUID entity) {
    Future<Boolean> status = pool.submit(new Callable<Boolean>() {

      @Override
      public Boolean call() throws Exception {

        Lock lock = manager.createLock(application, entity.toString());

        // False here means that the lock WAS NOT ACQUIRED. And that is
        // what we expect.

        boolean locked = lock.tryLock(0, TimeUnit.MILLISECONDS);

        // shouldn't lock, so unlock to avoid polluting future tests
        if (locked) {
          lock.unlock();
        }

        return locked;

      }
    });

    boolean wasLocked = true;
    try {
      wasLocked = status.get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      wasLocked = false;
    }

    return wasLocked;
  }

    @Test
    public void testTryLockException(){
        try{

            SingleNodeLockImpl snl = new SingleNodeLockImpl(tsLock);

            assertEquals(true,snl.tryLock(1, TimeUnit.MINUTES));     ;
        }
        catch ( UGLockException e)   {
           assertEquals("Couldn't get the lock", e.getMessage());
        }

    }

}