/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.android.sync.test.SynchronizerHelpers.FailFetchWBORepository;
import org.mozilla.android.sync.test.helpers.ExpectSuccessRepositorySessionFinishDelegate;
import org.mozilla.gecko.background.testhelpers.WBORepository;
import org.mozilla.gecko.background.testhelpers.WaitHelper;
import org.mozilla.gecko.sync.CollectionConcurrentModificationException;
import org.mozilla.gecko.sync.SyncDeadlineReachedException;
import org.mozilla.gecko.sync.repositories.InactiveSessionException;
import org.mozilla.gecko.sync.repositories.InvalidSessionTransitionException;
import org.mozilla.gecko.sync.repositories.RepositorySession;
import org.mozilla.gecko.sync.repositories.RepositorySessionBundle;
import org.mozilla.gecko.sync.repositories.domain.BookmarkRecord;
import org.mozilla.gecko.sync.synchronizer.NonBufferingRecordsChannel;
import org.mozilla.gecko.sync.synchronizer.RecordsChannel;
import org.mozilla.gecko.sync.synchronizer.RecordsChannelDelegate;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TestRecordsChannel {

  private WBORepository sourceRepository;
  private RepositorySession sourceSession;
  private WBORepository sinkRepository;
  private RepositorySession sinkSession;

  private RecordsChannelDelegate rcDelegate;

  private AtomicInteger numFlowFetchFailed;
  private AtomicInteger numFlowStoreFailed;
  private AtomicInteger numFlowCompleted;
  private AtomicBoolean flowBeginFailed;

  private volatile RecordsChannel recordsChannel;
  private volatile Exception fetchException;
  private volatile Exception storeException;

  @Before
  public void setUp() throws Exception {
    numFlowFetchFailed = new AtomicInteger(0);
    numFlowStoreFailed = new AtomicInteger(0);
    numFlowCompleted = new AtomicInteger(0);
    flowBeginFailed = new AtomicBoolean(false);

    // Repositories and sessions will be set/created by tests.
    sourceRepository = null;
    sourceSession = null;
    sinkRepository = null;
    sinkSession = null;

    rcDelegate = new RecordsChannelDelegate() {
      @Override
      public void onFlowFetchFailed(RecordsChannel recordsChannel, Exception ex) {
        numFlowFetchFailed.incrementAndGet();
        fetchException = ex;
      }

      @Override
      public void onFlowStoreFailed(RecordsChannel recordsChannel, Exception ex, String recordGuid) {
        numFlowStoreFailed.incrementAndGet();
        storeException = ex;
      }

      @Override
      public void onFlowCompleted(RecordsChannel recordsChannel) {
        numFlowCompleted.incrementAndGet();
        try {
          sinkSession.finish(new ExpectSuccessRepositorySessionFinishDelegate(WaitHelper.getTestWaiter()) {
            @Override
            public void onFinishSucceeded(RepositorySession session, RepositorySessionBundle bundle) {
              try {
                sourceSession.finish(new ExpectSuccessRepositorySessionFinishDelegate(WaitHelper.getTestWaiter()) {
                  @Override
                  public void onFinishSucceeded(RepositorySession session, RepositorySessionBundle bundle) {
                    performNotify();
                  }
                });
              } catch (InactiveSessionException e) {
                WaitHelper.getTestWaiter().performNotify(e);
              }
            }
          });
        } catch (InactiveSessionException e) {
          WaitHelper.getTestWaiter().performNotify(e);
        }
      }

      @Override
      public void onFlowBeginFailed(RecordsChannel recordsChannel, Exception ex) {
        flowBeginFailed.set(true);
        WaitHelper.getTestWaiter().performNotify();
      }
    };
  }

  private void createSessions() {
    sourceSession = sourceRepository.createSession(null);
    sinkSession = sinkRepository.createSession(null);
  }

  public void doFlow(boolean nonBuffering) throws Exception {
    createSessions();
    assertNotNull(sourceSession);
    assertNotNull(sinkSession);
    if (nonBuffering) {
      recordsChannel = new NonBufferingRecordsChannel(sourceSession, sinkSession, rcDelegate);
    } else {
      recordsChannel = new RecordsChannel(sourceSession, sinkSession, rcDelegate);
    }
    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        try {
          recordsChannel.beginAndFlow();
        } catch (InvalidSessionTransitionException e) {
          WaitHelper.getTestWaiter().performNotify(e);
        }
      }
    });
  }

  // NB: records in WBORepository are stored in a HashMap, so don't assume an order.
  public static final BookmarkRecord[] inbounds = new BookmarkRecord[] {
    new BookmarkRecord("inboundSucc1", "bookmarks", 1, false),
    new BookmarkRecord("inboundSucc2", "bookmarks", 1, false),
    new BookmarkRecord("inboundFail1", "bookmarks", 1, false),
    new BookmarkRecord("inboundSucc3", "bookmarks", 1, false),
    new BookmarkRecord("inboundSucc4", "bookmarks", 1, false),
    new BookmarkRecord("inboundFail2", "bookmarks", 1, false),
  };
  public static final BookmarkRecord[] outbounds = new BookmarkRecord[] {
      new BookmarkRecord("outboundSucc1", "bookmarks", 1, false),
      new BookmarkRecord("outboundSucc2", "bookmarks", 1, false),
      new BookmarkRecord("outboundSucc3", "bookmarks", 1, false),
      new BookmarkRecord("outboundFail6", "bookmarks", 1, false),
      new BookmarkRecord("outboundSucc4", "bookmarks", 1, false),
      new BookmarkRecord("outboundSucc5", "bookmarks", 1, false),
  };

  protected WBORepository empty() {
    WBORepository repo = new SynchronizerHelpers.TrackingWBORepository();
    return repo;
  }

  protected WBORepository full() {
    WBORepository repo = new SynchronizerHelpers.TrackingWBORepository();
    for (BookmarkRecord outbound : outbounds) {
      repo.wbos.put(outbound.guid, outbound);
    }
    return repo;
  }

  protected WBORepository failingFetch(SynchronizerHelpers.FailMode failMode) {
    WBORepository repo = new FailFetchWBORepository(failMode);

    for (BookmarkRecord outbound : outbounds) {
      repo.wbos.put(outbound.guid, outbound);
    }
    return repo;
  }

  @Test
  public void testSuccess() throws Exception {
    sourceRepository = full();
    sinkRepository = empty();
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(0, numFlowStoreFailed.get());
    assertEquals(sourceRepository.wbos, sinkRepository.wbos);
    assertFalse(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertEquals(6, recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testSuccessNB() throws Exception {
    sourceRepository = full();
    sinkRepository = empty();
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(0, numFlowStoreFailed.get());
    assertEquals(sourceRepository.wbos, sinkRepository.wbos);
    assertFalse(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertEquals(6, recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testFetchFail() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.FETCH);
    sinkRepository = empty();
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);
    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < 6);
  }

  @Test
  public void testFetchFailNB() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.FETCH);
    sinkRepository = empty();
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);
    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < 6);
  }

  @Test
  public void testStoreFetchFailedCollectionModified() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.COLLECTION_MODIFIED);
    sinkRepository = empty();
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);

    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < sourceRepository.wbos.size());

    assertEquals(CollectionConcurrentModificationException.class, fetchException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(CollectionConcurrentModificationException.class, ex.getClass());
  }

  @Test
  public void testStoreFetchFailedCollectionModifiedNB() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.COLLECTION_MODIFIED);
    sinkRepository = empty();
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);

    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < sourceRepository.wbos.size());

    assertEquals(CollectionConcurrentModificationException.class, fetchException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(CollectionConcurrentModificationException.class, ex.getClass());
  }

  @Test
  public void testStoreFetchFailedDeadline() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.DEADLINE_REACHED);
    sinkRepository = empty();
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);

    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < sourceRepository.wbos.size());

    assertEquals(SyncDeadlineReachedException.class, fetchException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(SyncDeadlineReachedException.class, ex.getClass());
  }

  @Test
  public void testStoreFetchFailedDeadlineNB() throws Exception {
    sourceRepository = failingFetch(SynchronizerHelpers.FailMode.DEADLINE_REACHED);
    sinkRepository = empty();
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertTrue(numFlowFetchFailed.get() > 0);
    assertEquals(0, numFlowStoreFailed.get());
    assertTrue(sinkRepository.wbos.size() < 6);

    assertTrue(recordsChannel.didFetchFail());
    assertEquals(0, recordsChannel.getStoreFailureCount());
    assertTrue(recordsChannel.getStoreAttemptedCount() < sourceRepository.wbos.size());

    assertEquals(SyncDeadlineReachedException.class, fetchException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(SyncDeadlineReachedException.class, ex.getClass());
  }

  @Test
  public void testStoreSerialFail() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.SerialFailStoreWBORepository(
            SynchronizerHelpers.FailMode.STORE);
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(1, numFlowStoreFailed.get());
    // We will fail to store one of the records but expect flow to continue.
    assertEquals(5, sinkRepository.wbos.size());

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(1, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testStoreSerialFailNB() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.SerialFailStoreWBORepository(
            SynchronizerHelpers.FailMode.STORE);
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(1, numFlowStoreFailed.get());
    // We will fail to store one of the records but expect flow to continue.
    assertEquals(5, sinkRepository.wbos.size());

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(1, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testStoreSerialFailCollectionModified() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.SerialFailStoreWBORepository(
            SynchronizerHelpers.FailMode.COLLECTION_MODIFIED);
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(1, numFlowStoreFailed.get());
    // One of the records will fail, at which point we'll stop flowing them.
    final int sunkenRecords = sinkRepository.wbos.size();
    assertTrue(sunkenRecords > 0 && sunkenRecords < 6);

    assertFalse(recordsChannel.didFetchFail());
    // RecordChannel's storeFail count is only incremented for failures of individual records.
    assertEquals(0, recordsChannel.getStoreFailureCount());

    assertEquals(CollectionConcurrentModificationException.class, storeException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(CollectionConcurrentModificationException.class, ex.getClass());
  }

  @Test
  public void testStoreSerialFailCollectionModifiedNB() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.SerialFailStoreWBORepository(
            SynchronizerHelpers.FailMode.COLLECTION_MODIFIED);
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(1, numFlowStoreFailed.get());
    // One of the records will fail, at which point we'll stop flowing them.
    final int sunkenRecords = sinkRepository.wbos.size();
    assertTrue(sunkenRecords > 0 && sunkenRecords < 6);

    assertFalse(recordsChannel.didFetchFail());
    // RecordChannel's storeFail count is only incremented for failures of individual records.
    assertEquals(0, recordsChannel.getStoreFailureCount());

    assertEquals(CollectionConcurrentModificationException.class, storeException.getClass());
    final Exception ex = recordsChannel.getReflowException();
    assertNotNull(ex);
    assertEquals(CollectionConcurrentModificationException.class, ex.getClass());
  }

  @Test
  public void testStoreBatchesFail() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.BatchFailStoreWBORepository(3);
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(3, numFlowStoreFailed.get()); // One batch fails.
    assertEquals(3, sinkRepository.wbos.size()); // One batch succeeds.

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(3, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testStoreBatchesFailNB() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.BatchFailStoreWBORepository(3);
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(3, numFlowStoreFailed.get()); // One batch fails.
    assertEquals(3, sinkRepository.wbos.size()); // One batch succeeds.

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(3, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testStoreOneBigBatchFail() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.BatchFailStoreWBORepository(50);
    doFlow(false);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(6, numFlowStoreFailed.get()); // One (big) batch fails.
    assertEquals(0, sinkRepository.wbos.size()); // No batches succeed.

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(6, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }

  @Test
  public void testStoreOneBigBatchFailNB() throws Exception {
    sourceRepository = full();
    sinkRepository = new SynchronizerHelpers.BatchFailStoreWBORepository(50);
    doFlow(true);
    assertEquals(1, numFlowCompleted.get());
    assertEquals(0, numFlowFetchFailed.get());
    assertEquals(6, numFlowStoreFailed.get()); // One (big) batch fails.
    assertEquals(0, sinkRepository.wbos.size()); // No batches succeed.

    assertFalse(recordsChannel.didFetchFail());
    assertEquals(6, recordsChannel.getStoreFailureCount());
    // Number of store attempts.
    assertEquals(sourceRepository.wbos.size(), recordsChannel.getStoreAttemptedCount());
  }
}
