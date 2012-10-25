/*
* PDFComparator
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
package de.ee.hezel;

import java.io.File;
import java.util.Set;

import org.apache.log4j.Logger;

import de.ee.hezel.logger.DifferenceLogger;
import de.ee.hezel.model.PDFInfoHolder;
import de.ee.hezel.model.PDFInfoHolder.DifferenceType;
import de.ee.hezel.model.PDFPageHolder;
import de.ee.hezel.util.AbstractJob;
import de.ee.hezel.util.Job;
import de.ee.hezel.util.JobListener;
import de.ee.hezel.util.JobQueue;

/**
 * @author hezeln
 *
 */
public class PDFComparator implements JobListener {

	static Logger log = Logger.getLogger(PDFComparator.class.getName());
	static final int PARALLEL_JOBS = Integer.getInteger(PDFComparator.class.getName() + ".PARALLEL_JOBS", 4);
	
	private File logPath;
    private final int compareType;
    private boolean foundDifference;

    public PDFComparator(File logPath, int compareType) {
        this.compareType = compareType;    
        this.logPath = logPath;
    }

    /**
     * parallelized the comparison tasks
     * 
     * @param path1
     * @param path2
     * @param outputDir
     * @param prefix
     * @return
     */
	public boolean run(File path1, File path2, File outputDir, String prefix)
	{
		long start = System.currentTimeMillis();	    
		
		// is any pdf pair different
		foundDifference = false;
		
		// get all pdf pairs
		Set<PDFInfoHolder> pdfInfoHolders = PDFCorpusAnalyser.getSimplePDFInfoHolders(path1, path2, prefix);

		// start 4 different tasks at the same time
		JobQueue queue = new JobQueue(PARALLEL_JOBS);
		for (PDFInfoHolder pdfInfoHolder : pdfInfoHolders) 
		{    
			CompareJob compareJob = new CompareJob(outputDir, pdfInfoHolder);
			compareJob.addJobListener(this);
			queue.addJob(compareJob);
		}
		
		// waiting for all jobs to be finished
        while (queue.hasJobs()) {
            try {
                synchronized (queue) {
                    queue.wait();
                }
            } catch (InterruptedException e) {
            	log.error("Interrupted while waiting for jobs to finish", e);
            }
        }
		
		long end = System.currentTimeMillis();
		log.info("Execution time: "+ (end-start)+"ms");
		
		return foundDifference;
	}
	
    @Override
    public void finished(Job job) {
        foundDifference |= ((CompareJob) job).hasDifference();     
    }
    
	private class CompareJob extends AbstractJob {
	    
		private DifferenceLogger  dlog;
	    private File outputDir;
        private boolean foundDifference;
        private PDFInfoHolder pdfInfoHolder;

        private PDFVisualComparator pdfVisualComparator;
        private PDFVisualiseDifference pdfVisualiseDifference;
        private PDFStructureComparator pdfStructureComparator;
    	private PDFCorpusAnalyser pdfCorpusAnaliser;
        
        CompareJob(File outputDir, PDFInfoHolder pdfInfoHolder) {
            this.outputDir = outputDir;
            this.pdfInfoHolder = pdfInfoHolder;
            
            // each jobs need its own result logger
            // and logger for the difference-images
            dlog = new DifferenceLogger(logPath, pdfInfoHolder.getFilename());
 
            pdfCorpusAnaliser = new PDFCorpusAnalyser(dlog);
            pdfVisualComparator = new PDFVisualComparator(outputDir, dlog, pdfInfoHolder);
            pdfVisualiseDifference = new PDFVisualiseDifference(outputDir, dlog, pdfInfoHolder);
            pdfStructureComparator = new PDFStructureComparator((compareType == 1), dlog, pdfInfoHolder);
	    }

    	/**
    	 * Start the analyze process.
    	 * Find and mark the differences.
    	 * Output them in a difference-image
    	 * @throws Exception 
    	 */
        @Override
        protected void executeJobAction() {

        	long start1 = System.currentTimeMillis();
        	try {
        		 // load the pdf file content
        		 pdfInfoHolder.loadPDFFiles();
                 
                 log.info(pdfInfoHolder.getFilename()+": Process "+pdfInfoHolder.getFilename()+".pdf");
                 
                 // Analyze the content of the pdf document
                 log.info(pdfInfoHolder.getFilename()+": analyse PDF structure ...");
                 pdfCorpusAnaliser.analyse(pdfInfoHolder);
                 
                 // compare SIMPLE or STRUCTURAL
                 log.info(pdfInfoHolder.getFilename()+": compare PDF structure ...");
                 pdfStructureComparator.compare();
                 
                 // compare VISUAL and print the result or simply display the already found differences
                 log.info(pdfInfoHolder.getFilename()+": visualise differences ...");
                 if(compareType == 3)
                     pdfVisualComparator.compare();
                 else
                     pdfVisualiseDifference.visualise();
                 
                 // print the results
                 printResult(pdfInfoHolder);

                 // found differences!?
                 foundDifference = pdfInfoHolder.isDifferent();
                 
			} catch (Exception e) {
				log.error(pdfInfoHolder.getFilename()+":"+e.getMessage(), e);
			} 

        	// calc time needed
            long end1 = System.currentTimeMillis();
            log.info(pdfInfoHolder.getFilename()+": processing took "+ (end1-start1)+"ms");
            log.info("");
            
        	// release all resources
            releaseResources();
        }

        public boolean hasDifference() {
            return foundDifference;
        }

        public void releaseResources()
        {
        	if(dlog != null)
        		dlog.releaseResources();
        	
        	// are not needed anymore
        	dlog = null;
        	pdfInfoHolder.releasePDFFiles();
        	pdfInfoHolder = null;
        	pdfCorpusAnaliser = null;
        	pdfVisualComparator = null;
        	pdfVisualiseDifference = null;
        	pdfStructureComparator = null;
        }
	}

	
	private void printResult(PDFInfoHolder pdfInfoHolder)
	{
		if(pdfInfoHolder.isDifferent() && pdfInfoHolder.getDifferent() != DifferenceType.MISSINGDOCUMENT)
		{
			String differencesOnPage = "";
			for (PDFPageHolder pdfPageHolder : pdfInfoHolder.getPDFStructure1().getPageHolders()) {
					
				// get counter part pdf to check for differences
				PDFPageHolder pdfPageHolder2 = pdfInfoHolder.getPDFStructure2().getPageHolder(pdfPageHolder.getPageNumber());
				if(pdfPageHolder.isDifferent() || (pdfPageHolder2 != null && pdfPageHolder2.isDifferent()))
					differencesOnPage += (pdfPageHolder.getPageNumber()+1)+",";
			}	
			log.info(pdfInfoHolder.getFilename()+": found differences on pages: "+differencesOnPage);
		}
        else
         	log.info(pdfInfoHolder.getFilename()+": no differences found");
	}

}
