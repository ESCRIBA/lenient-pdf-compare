/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>MemoryManager</code> class is a utility to help manage the amount of memory
 * available to the application.  When memory intensive operations are about
 * occur, the <code>MemoryManager</code> is asked if it can allocate the needed amount of memory.
 * If there is not enough memory available, the <code>MemoryManager</code> will purge the
 * cache to try and free the requested amount of memory.
 */
public class MemoryManager {

    private static final Logger logger =
            Logger.getLogger(MemoryManager.class.toString());

    // internal reference to MemeoryMangaer, used for singleton pattern.
    private static MemoryManager instance;

    /**
     * Runtime object responsible for returning VM memory use information
     */
    protected final Runtime runtime = Runtime.getRuntime();

    /**
     * The minimum amount of free memory at which the memory manager will force
     * a purge of the cached pageTree.  This value can be set by the system
     * property org.icepdf.core.minMemory
     */
    // Old default was 300000, but PageTree would set it to 3000000, to help
    //   ensure that parsing font glyphs will not result in a memory exception
    protected long minMemory = 300000;

    /**
     * The maximum amount of memory allocated to the JVM.
     */
    protected long maxMemory = runtime.maxMemory();

    /**
     * When we decide to reduce our memory footprint, this is how many items we
     * purge at once.
     */
    protected int purgeSize;

    /**
     * If a memory-based ceiling, like maxMemory, is not sufficient, then you
     * can use maxSize to specify the maximum number of items that may
     * be opened before purging commences. A value of 0 (zero) means it
     * will not be used
     */
    protected int maxSize;

    protected WeakHashMap<Object, HashSet<MemoryManageable>> locked;
    protected ArrayList<MemoryManageable> leastRecentlyUsed;

    protected long cumulativeDurationManagingMemory;
    protected long cumulativeDurationNotManagingMemory;
    protected long previousTimestampManagedMemory;
    protected int percentageDurationManagingMemory;

    protected ArrayList<MemoryManagerDelegate> delegates;

    /**
     * Get an instance of the <code>MemoryManager</code>.  If there is not a <code>MemoryManager</code>
     * initiated, a new <code>MemoryManager</code> is is created and returned.
     *
     * @return the current <code>MemoryManager</code> object org.icepdf.core.minMemory
     */
    public static MemoryManager getInstance() {
        if (instance == null) {
            instance = new MemoryManager();
        }
        return instance;
    }

    /**
     * Creates a new instance of a <code>MemoryManager</code>.
     */
    protected MemoryManager() {
        // get min system memory
        try {
            int t = parse("org.icepdf.core.minMemory");
            if (t > 0) {
                minMemory = t;
            }
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error setting org.icepdf.core.minMemory");
        }

        purgeSize = Defs.sysPropertyInt("org.icepdf.core.purgeSize", 5);

        maxSize = Defs.sysPropertyInt("org.icepdf.core.maxSize", 0);


        locked = new WeakHashMap<Object, HashSet<MemoryManageable>>();
        leastRecentlyUsed = new ArrayList<MemoryManageable>(256);
        delegates = new ArrayList<MemoryManagerDelegate>(64);
    }

    public synchronized void lock(Object user, MemoryManageable mm) {
        if (user == null || mm == null)
            return;
//System.out.println("+-+ MM.lock()    user: " + user + ", mm: " + mm);
        HashSet<MemoryManageable> inUse = locked.get(user);
        if (inUse == null) {
            inUse = new HashSet<MemoryManageable>(256);
            locked.put(user, inUse);
        }
        inUse.add(mm);

        leastRecentlyUsed.remove(mm);
        leastRecentlyUsed.add(mm);

        if (maxSize > 0) {
            int numUsed = leastRecentlyUsed.size();
            int numUsedMoreThanShould = numUsed - maxSize;
            if (numUsedMoreThanShould > 0) {
//System.out.println("+-+ MM.lock()      numUsedMoreThanShould: " + numUsedMoreThanShould + ", maxSize: " + maxSize + ", numUsed: " + numUsed);
                int numToDo = Math.max(purgeSize, numUsedMoreThanShould);
                reduceMemory(numToDo);
            }
        }
    }

    public synchronized void release(Object user, MemoryManageable mm) {
        if (user == null || mm == null)
            return;
//System.out.println("+-+ MM.release() user: " + user + ", mm: " + mm);
        HashSet inUse = locked.get(user);
        if (inUse != null) {
            boolean removed = inUse.remove(mm);
            // remove locked reference if it no longer holds any mm objects.
            if (inUse.size() == 0) {
                locked.remove(user);
            }
//if( removed ) System.out.println("+-+ MM.release() mm was removed");
        }
    }

    public synchronized void registerMemoryManagerDelegate(MemoryManagerDelegate delegate) {
        if (!delegates.contains(delegate))
            delegates.add(delegate);
    }

    public synchronized void releaseAllByLibrary(Library library) {
//System.out.println("+-+ MM.releaseAllByLibrary() library: " + library);
        if (library == null)
            return;

        // Remove every MemoryManageable whose Library is library
        // Go in reverse order, so removals won't affect indexing
        for (int i = leastRecentlyUsed.size() - 1; i >= 0; i--) {
            MemoryManageable mm = leastRecentlyUsed.get(i);
//System.out.println("+-+ MM.releaseAllByLibrary() LRU " + i + " of " + leastRecentlyUsed.size() + "  mm: " + mm);
            Library lib = mm.getLibrary();
            if (lib == null) {
//System.out.println("*** MM.releaseAllByLibrary() mm.getLibrary() was null");
                continue;
            }
//System.out.println("+-+ MM.releaseAllByLibrary() lib: " + lib + ",  lib == library (remove mm): " + lib.equals(library));
            if (lib.equals(library)) {
                leastRecentlyUsed.remove(i);
            }
        }

        // Go through every user, and looks at the MemoryManageable(s)
        //   that it's locking
        ArrayList<Object> usersToRemove = new ArrayList<Object>();
        Set entries = locked.entrySet();
        for (Object entry1 : entries) {
            Map.Entry entry = (Map.Entry) entry1;
            Object user = entry.getKey();
//System.out.println("+-+ MM.releaseAllByLibrary() user: " + user);
            HashSet inUse = (HashSet) entry.getValue();
            if (inUse != null) {
                // Remove every MemoryManageable whose Library is library
                ArrayList<MemoryManageable> mmsToRemove = new ArrayList<MemoryManageable>();
                for (Object anInUse : inUse) {
                    MemoryManageable mm = (MemoryManageable) anInUse;
//System.out.println("+-+ MM.releaseAllByLibrary()   mm: " + mm);
                    if (mm != null) {
                        Library lib = mm.getLibrary();
                        if (lib == null) {
//System.out.println("*** MM.releaseAllByLibrary()   mm.getLibrary() was null");
                            continue;
                        }
//System.out.println("+-+ MM.releaseAllByLibrary()   lib: " + lib + ",  lib == library (remove mm): " + lib.equals(library));
                        if (lib.equals(library)) {
                            mmsToRemove.add(mm);
                        }
                    }
                }
                for (MemoryManageable aMmsToRemove : mmsToRemove) {
                    inUse.remove(aMmsToRemove);
                }
                mmsToRemove.clear();

                // If the user has no more MemoryManageable(s)
                //   locked, then remove it as well.
                // We don't immediately remove it, since the
                //   iterators are fail-fast. Instead, mark them
                //   for removal, and do it after iterating
                if (inUse.size() == 0) {
//System.out.println("+-+ MM.releaseAllByLibrary() remove user: " + user);
                    usersToRemove.add(user);
                }
            }
        }
        for (Object anUsersToRemove : usersToRemove) {
            locked.remove(anUsersToRemove);
        }
        usersToRemove.clear();

        for (int i = delegates.size() - 1; i >= 0; i--) {
            MemoryManagerDelegate mmd = delegates.get(i);
            boolean shouldRemove = false;
            if (mmd == null)
                shouldRemove = true;
            else {
                Library lib = mmd.getLibrary();
                if (lib == null)
                    shouldRemove = true;
                else {
                    if (lib.equals(library))
                        shouldRemove = true;
                }
            }
            if (shouldRemove)
                delegates.remove(i);
        }
    }


    /**
     * @return If potentially reduced some memory
     */
    protected synchronized boolean reduceMemory() {
        int numToDo = purgeSize;

        int aggressive = 0;
        int lruSize = leastRecentlyUsed.size();
        if (percentageDurationManagingMemory > 15 || lruSize > 100)
            aggressive = lruSize * 60 / 100;
        else if (lruSize > 50)
            aggressive = lruSize * 50 / 100;
        else if (lruSize > 20)
            aggressive = lruSize * 40 / 100;
        if (aggressive > numToDo)
            numToDo = aggressive;

        int numDone = reduceMemory(numToDo);

        boolean delegatesReduced = false;
        if (numDone == 0)
            delegatesReduced = reduceMemoryWithDelegates(true);
        else if (numDone < numToDo)
            delegatesReduced = reduceMemoryWithDelegates(false);

        return ((numDone > 0) || delegatesReduced);
    }

    protected int reduceMemory(int numToDo) {
//System.out.println("+-+ MM.reduceMemory() numToDo: " + numToDo + ", LRU size: " + leastRecentlyUsed.size());
        int numDone = 0;
        try {
            int leastRecentlyUsedIndex = 0;
            while (numDone < numToDo && leastRecentlyUsedIndex < leastRecentlyUsed.size()) {
//System.out.println("+-+ MM.reduceMemory()   index: " + leastRecentlyUsedIndex + ", size: " + leastRecentlyUsed.size());
                MemoryManageable mm = leastRecentlyUsed.get(leastRecentlyUsedIndex);
//System.out.println("+-+ MM.reduceMemory()   isLocked: " + isLocked(mm) + ", mm: " + mm);
                if (!isLocked(mm)) {
                    mm.reduceMemory();
                    numDone++;
                    leastRecentlyUsed.remove(leastRecentlyUsedIndex);
                } else
                    leastRecentlyUsedIndex++;
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Problem while reducing memory", e);
        }
//System.out.println("+-+ MM.reduceMemory()   managing: " + cumulativeDurationManagingMemory + ", not: " + cumulativeDurationNotManagingMemory + "      managing: " + percentageDurationManagingMemory + "%");
        return numDone;
    }

    protected synchronized boolean isLocked(MemoryManageable mm) {
        Set entries = locked.entrySet();
        // this can get pretty inefficient if locked is large
        for (Object entry1 : entries) {
            Map.Entry entry = (Map.Entry) entry1;
            HashSet inUse = (HashSet) entry.getValue();
            if (inUse != null && inUse.contains(mm))
                return true;
        }
        return false;
    }

    protected synchronized boolean reduceMemoryWithDelegates(boolean aggressively) {
        int reductionPolicy = aggressively ? MemoryManagerDelegate.REDUCE_AGGRESSIVELY
                : MemoryManagerDelegate.REDUCE_SOMEWHAT;
        boolean anyReduced = false;
        for (MemoryManagerDelegate mmd : delegates) {
            if (mmd == null)
                continue;
            boolean reduced = mmd.reduceMemory(reductionPolicy);
            anyReduced |= reduced;
        }
        return anyReduced;
    }


    /**
     * Utility method to parse memory values specified by the k, K, m or M and
     * return the corresponding number of bytes.
     *
     * @param memoryValue memory value to parse
     * @return the number of bytes
     */
    private static int parse(String memoryValue) {
        String s = Defs.sysProperty(memoryValue);
        if (s == null) {
            return -1;
        }
        int mult = 1;
        char c = s.charAt(s.length() - 1);
        if (c == 'k' || c == 'K') {
            mult = 1024;
            s = s.substring(0, s.length() - 1);
        }
        if (c == 'm' || c == 'M') {
            mult = 1024 * 1024;
            s = s.substring(0, s.length() - 1);
        }

        return mult * Integer.parseInt(s);
    }

    /**
     * Set the minimum amount of memory. Basically, if the amount
     * of free heap is under this value, the core will go into
     * the memory  recovery mode.
     *
     * @param m minimum amount of memory that should be kept free on the heap
     */
    public void setMinMemory(long m) {
        minMemory = m;
    }

    /**
     * Get the minimum amount of memory
     *
     * @return minimum amount of memory that should be kept free on the heap.
     */
    public long getMinMemory() {
        return minMemory;
    }

    /**
     * Get runtime free memory.
     *
     * @return free memory in bytes.
     */
    public long getFreeMemory() {
        return runtime.freeMemory();
    }

    /**
     * Will return true if the system is not in a low memory
     * condition after allocation of specified number of bytes.
     */
    private boolean canAllocate(int bytes, boolean doGC) {
        long mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }

        // Do we allow the heap to grow?
        long total = runtime.totalMemory();

        if (maxMemory > total) {
            mem += maxMemory - total;
            if ((mem - bytes) > minMemory) {
                return true;
            }
        }

        // This is so that checkMemory() can try to clear out
        //  some cached pages _before_ running the garbage collector,
        //  which saves a lot of CPU
        if (!doGC)
            return false;

        // lets purge some pages
        reduceMemory();

        // Nope, try GC
        System.gc();
        mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }

        // Nope, try heavy GC
        System.runFinalization();
        System.gc();
        mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }

        // We are low on memory!
        //System.out.println("Failed -not enough Available Memory " + mem);
        return false;
    }

    /**
     * Check whether the runtime is low on memory. Page initialization and other
     * large memory allocation operations should call this
     * method before (during) attempts to allocate resources.
     *
     * @return If this method returns true, such a component should stop its
     *         operation and free up the memory it has allocated.
     */
    public boolean isLowMemory() {
        return !canAllocate(0, true);
    }

    /**
     * @param memoryNeeded  memory looking to allocate
     * @return true if it's sure we can allocate memoryNeeded number of bytes
     */
    public boolean checkMemory(int memoryNeeded) {
        long beginTime = System.currentTimeMillis();
        int count = 0;
        // try and allocate memory, but quit after ten tries.
        while (!canAllocate(memoryNeeded, count > 0)) {
            // cache files on memory check
            boolean reducedSomething = reduceMemory();
            if (!reducedSomething && count > 0) {
                finishedMemoryProcessing(beginTime);
                return false;
            }
            count++;
            if (count > 10) {
                finishedMemoryProcessing(beginTime);
                return false;
            }
        }
        finishedMemoryProcessing(beginTime);
        return true;
    }

    private void finishedMemoryProcessing(long beginTime) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - beginTime;
        if (duration > 0)
            cumulativeDurationManagingMemory += duration;
        if (previousTimestampManagedMemory != 0) {
            duration = beginTime - previousTimestampManagedMemory;
            if (duration > 0)
                cumulativeDurationNotManagingMemory += duration;
        }
        previousTimestampManagedMemory = endTime;

        long totalDuration = cumulativeDurationManagingMemory + cumulativeDurationNotManagingMemory;
        if (totalDuration > 0) {
            percentageDurationManagingMemory =
                    (int) (cumulativeDurationManagingMemory * 100 / totalDuration);
        }
    }
}
