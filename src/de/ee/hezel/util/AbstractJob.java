/*
* AbstractJob
* 
* Copyright (c) 2012, E&E information consultants AG. All rights reserved.
* Authors:
*   Peter Jentsch
*   Nico Hezel
*   
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
* MA 02110-1301 USA
*/
package de.ee.hezel.util;

/**
 * @author jentsch
 *
 */
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractJob implements Job {
    private final List<JobListener> listeners;

    private long runningTime;

    protected AbstractJob() {
       listeners = new ArrayList<JobListener>();
    }

    @Override
    public void run() {
        long t0 = System.currentTimeMillis();
        try {
            executeJobAction();
        } finally {
            runningTime = System.currentTimeMillis() - t0;
            synchronized (this) {
                ArrayList<JobListener> l = new ArrayList<JobListener>(listeners);
                for (Iterator<JobListener> it = l.iterator(); it.hasNext(); ) {
                    it.next().finished(this);
                }
            }
        }
    }

    protected abstract void executeJobAction();

    public synchronized void addJobListener(JobListener listener) {
        listeners.add(listener);
    }
    
    public synchronized void removeJobListener(JobListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public long getRunningTime() {
        return runningTime;
    }

}
