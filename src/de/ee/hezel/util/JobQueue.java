/*
* JobQueue
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jentsch
 *
 */
public class JobQueue implements JobListener {

	private final ArrayList<Job> jobs;
	private final int size;
	private final ArrayList<Long> jobTimes;

	public JobQueue(int size) {
		super();
		this.size = size;
		this.jobs = new ArrayList<Job>();
		this.jobTimes = new ArrayList<Long>();
	}
	
	public synchronized void addJob(Job job) {
		while (jobs.size() >= this.size) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		jobs.add(job);
		job.addJobListener(this);
		new Thread(job, job.toString()).start();
	}
	
	public synchronized void removeJob(Job job) {
		job.removeJobListener(this);
		jobs.remove(job);
		notifyAll();
	}
	
	public synchronized boolean hasJobs() {
		return jobs.size() > 0;
	}
	
	public List<Long> getJobTimes() {
		return Collections.unmodifiableList(jobTimes);
	}

	public void finished(Job job) {
		synchronized (jobTimes) {
			jobTimes.add(new Long(job.getRunningTime()));
		}
		removeJob(job);
	}

}
