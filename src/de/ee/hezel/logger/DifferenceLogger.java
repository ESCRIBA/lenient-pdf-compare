/*
* DifferenceLogger
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
package de.ee.hezel.logger;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author hezeln
 *
 */
public class DifferenceLogger implements ICompareLogger {

	boolean foundDifference;
	private File filePath;
	private PrintWriter currentLogFile;

	private File logPath;
	static String newline = System.getProperty("line.separator");
	
	public DifferenceLogger(File logPath, String filename)
	{
		this.logPath = logPath;
		prepareFile(filename);
	}
	
	private void prepareFile(String filename)
	{
		releaseResources();
		this.filePath = new File(logPath, filename+".log");
	}
	
	/* (non-Javadoc)
	 * @see de.ee.hezel.logger.ICompareLogger#handleLogMessage(de.ee.hezel.logger.ICompareLogger.LogType, java.lang.String)
	 */
	@Override
	public void log(String message) 
	{	
		if((this.currentLogFile = getPrintWriter()) != null)
		{
			// log to file
			this.currentLogFile.write(message+newline);
		}
	}
	
	private PrintWriter getPrintWriter()
	{
		if(this.currentLogFile == null && this.filePath != null)
		{
			try {
				return  new PrintWriter(this.filePath);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return this.currentLogFile;
	}
	
	
	
	@Override
	public void releaseResources()
	{
		if(this.currentLogFile !=  null)
		{
			this.currentLogFile.flush();
			this.currentLogFile.close();
			this.currentLogFile = null;
		}
	}

	@Override
	public File getLogPath()
	{
		return logPath;
	}
}
