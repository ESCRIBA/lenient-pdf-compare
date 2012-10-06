/*
* PDFCompareMain
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
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * This program compares PDF documents.
 * Two directories gets searched and every
 * identically named PDF document gets compared. 
 * 
 * The program returns if all documents are the same.
 * 
 * If desired the differences can be visualized and
 * saved in an image file.
 * 
 * @author hezeln
 *
 */
public class PDFCompareMain {

	static String newline = System.getProperty("line.separator");
	
	static Logger log = Logger.getLogger(PDFCompareMain.class.getName());
	
	/**
	 * Args[]
	 * 		[1. path]
	 * 		[2. path]
	 * 		-output [true/false]
	 * 		-visualise [output path] 
	 * 
	 * return value
	 * 		1 = not enough parameters
	 * 		2 = 
	 * 
	 * @param args 
	 * @return int 
	 */
	public static void main(String[] args) {		
		// not enough parameters
		if (args.length < 1) {
			System.out.println("usage: java -jar PDFCompare.jar "
				    + "<path 1> <path 2> [-output <true/false>] [-visualise <path 3>] [-log <path 4>] [-compare <compare type>] [-prefix <pdf prefix>]" + newline
		    	    + newline
		    		+ "<path 1> = path with PDF documents from old version" + newline
		    		+ "<path 2> = path with PDF documents from new version" + newline
		    		+ "[output] = console output" + newline
		    		+ "[visualise] = output folder for visualizing differnces " + newline
		    		+ "[log] = path for log files and differnce images" + newline
		    		+ "[compare type] = type of comparison <\"SIMPLE\" | \"STRUCTURAL\" | \"VISUAL\">" + newline
		    		+ "[prefix] = compare only pdfs where the name starts with this prefix" + newline);
		    return;
		}
		
		boolean output = false;
		File targetPath = null, logPath = null;
		int compareType = 1; // Modes: SIMPLE/STRUCTURAL/VISUAL
        String prefix = null;
		
		// read the incoming arguments
		for (int i = 2; i < args.length; i++) {
		    if (args[i].equals("-output")) {
		    	output = Boolean.parseBoolean(args[++i]);
		    } else if (args[i].equals("-visualise")) {
		    	targetPath = new File(args[++i]);
		    } else if (args[i].equals("-log")) {
		    	logPath = new File(args[++i]);
		    } else if (args[i].equals("-compare")) {
		    	String nextArg = args[++i];
		    	if(nextArg.equalsIgnoreCase("STRUCTURAL"))
		    		compareType = 2;
		    	else if(nextArg.equalsIgnoreCase("VISUAL"))
		    		compareType = 3;
		    } else if ((args[i]).equals("-prefix")) {
		        prefix = args[++i];
		    }
		}
		
		// delete the old difference image
		if(targetPath != null && targetPath.exists()) {
//		    int confirmation = JOptionPane.showConfirmDialog(null, "Output directory not empty. Delete all contents of '" + targetPath + "' and continue?", "Confirmation", JOptionPane.YES_NO_OPTION);
//		    if (confirmation == JOptionPane.YES_OPTION) {
//                try {
//                    FileUtils.deleteDirectory(targetPath);
//                } catch (IOException e1) {
//                    throw new RuntimeException("Unable to clean output directory: " + e1.getMessage(), e1);
//                }
//		    } else {
//		        return;
//		    }
//		    targetPath.mkdirs();
			
			try {
				FileUtils.deleteDirectory(targetPath);
			} catch (IOException e1) {
				throw new RuntimeException("Unable to clean output directory: " + e1.getMessage(), e1);
			}
			targetPath.mkdirs();
		}
		if(!targetPath.exists())
			targetPath.mkdir();
		
		// configure log4j
		Properties props = getLog4jProperties(output, logPath);
		LogManager.resetConfiguration();
		PropertyConfigurator.configure(props);
		
		// check the output paths
		File path1 = new File(args[0]);
		File path2 = new File(args[1]);
		if(!path1.isDirectory() || !path2.isDirectory())
		{
			if(!path1.isDirectory())
				log.error("[Path 1] does not exist");
			if(!path2.isDirectory())
				log.error("[Path 2] does not exist");
		}

		// compare the files in path 1 with the files in path 2 and save the
		// results in path 3 (if given)
		PDFComparator pdfComparer = new PDFComparator(logPath, compareType);
        boolean foundDifference = pdfComparer.run(path1, path2, targetPath, prefix);
		
		// if only a single number should be returned
		if(!output)
		{
			// no differences
			if(!foundDifference)
				System.exit(0);
			else
				System.exit(1);
		}
	}
	
	public static Properties getLog4jProperties(boolean output, File logPath) {				
		Properties props = new Properties();
		
		if(output)
		{
			props.setProperty("log4j.rootLogger", "debug, stdout, errorfile, resultfile");
	
			props.setProperty("log4j.appender.stdout", "org.apache.log4j.ConsoleAppender");
			props.setProperty("log4j.appender.stdout.layout", "org.apache.log4j.PatternLayout");
			props.setProperty("log4j.appender.stdout.layout.ConversionPattern", "%5p [%t] (%F:%L) - %m%n");
		}
		else
			props.setProperty("log4j.rootLogger", "debug, errorfile, resultfile");
		
		File logfile = new File(logPath, "_error.log");
		props.setProperty("log4j.appender.errorfile", "org.apache.log4j.FileAppender");
		props.setProperty("log4j.appender.errorfile.File", logfile.getAbsolutePath());
		props.setProperty("log4j.appender.errorfile.Append", "false");
		props.setProperty("log4j.appender.errorfile.Threshold", "ERROR");
		props.setProperty("log4j.appender.errorfile.layout", "org.apache.log4j.PatternLayout");
		props.setProperty("log4j.appender.errorfile.layout.ConversionPattern", "%d %p %F - %m%n");
		
		File logfile1 = new File(logPath, "_results.log");
		props.setProperty("log4j.appender.resultfile", "org.apache.log4j.FileAppender");
		props.setProperty("log4j.appender.resultfile.File", logfile1.getAbsolutePath());
		props.setProperty("log4j.appender.resultfile.Append", "false");
		
		// Filter in Properties ab 1.2.16 
		// http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PropertyConfigurator.html
		props.setProperty("log4j.appender.resultfile.filter.ID", "org.apache.log4j.varia.LevelRangeFilter");
		props.setProperty("log4j.appender.resultfile.filter.ID.LevelMin", "INFO");
		props.setProperty("log4j.appender.resultfile.filter.ID.LevelMax", "INFO");
		props.setProperty("log4j.appender.resultfile.layout", "org.apache.log4j.PatternLayout");
		props.setProperty("log4j.appender.resultfile.layout.ConversionPattern", "%d %p %F - %m%n");
		
		return props;
	}
}
