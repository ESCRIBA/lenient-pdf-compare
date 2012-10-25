/*
* PDFInfoHolder
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
package de.ee.hezel.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;

import com.sun.pdfview.PDFFile;



/**
 * This class contains information, which pdf documents
 * should be compared against each other.
 * 
 * @author hezeln
 *
 */
public class PDFInfoHolder 
{	
	public enum DifferenceType { NONE, MISSINGDOCUMENT, MISSINGPAGE, MISSINGSTRUCTURE, VISUAL };
	
	static Logger log = Logger.getLogger(PDFInfoHolder.class.getName());
	
	private File pdfFile1;
	private File pdfFile2;

	private PDFHolder pdfStructure1;
	private PDFHolder pdfStructure2;
	
	private DifferenceType difference;
	
	// memory demanding resources
	private PDFFile pdf1;
	private PDFFile pdf2;
	
	public PDFInfoHolder(File pdfF1, File pdfF2)
	{
		this.pdfFile1 = pdfF1;
		this.pdfFile2 = pdfF2;
		this.difference = DifferenceType.NONE;
	}
	
	public void loadPDFFiles() throws Exception
	{
        try {
            pdf1 = loadPDFFile(this.pdfFile1);
        } catch (IOException e) {
        	throw new Exception("Unable to load reference PDF: " + this.pdfFile1.getName() + ". Reason: " + e.getMessage(), e);
        }
        
        try {
            pdf2 = loadPDFFile(this.pdfFile2);
        } catch (IOException e) {
        	log.error("Unable to load PDF file: " + this.pdfFile1.getName() + ". Reason: " + e.getMessage(), e);
        	this.difference = DifferenceType.MISSINGDOCUMENT;
        }
     }
	
	/**
	 * load pdf data from file
	 * 
	 * @param pdfname
	 * @return
	 * @throws IOException
	 */
	private PDFFile loadPDFFile(File pdfFile) throws IOException
	{
		// load the pdf file in the byte buffer
//		RandomAccessFile raf = new RandomAccessFile(pdfFile, "r");
//		FileChannel channel = raf.getChannel();
//		ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
//		
//		PDFFile pdf = new PDFFile(buf);
//		channel.close();
//		raf.close();
		
		// http://stackoverflow.com/questions/6112686/reduce-number-of-opened-files-in-java-code
		FileInputStream stream = null;
		FileChannel channel = null;
		PDFFile pdf = null;

		try {
			stream = new FileInputStream(pdfFile);
		    channel = stream.getChannel();
			ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
			pdf = new PDFFile(buf);
		} catch (IOException e) {
		    // handle exception
		} finally {
		    if (channel != null)
		    	channel.close();
		    if (stream != null)
		    	stream.close();
		}

		return pdf;
	}
	
	public void releasePDFFiles()
	{
		pdf1 = null;
		pdf2 = null;
     }
	
	public boolean checkDifference()
	{
		// überprüfe welche der beiden PDFs an welcher stelle unterschiedlich sind
		boolean isPDF1Diff = pdfStructure1.checkDifference();
		boolean isPDF2Diff = pdfStructure2.checkDifference();
		
		if(difference != DifferenceType.VISUAL)
			difference =  DifferenceType.VISUAL;
		
		// überprüfe nur die erste Struktur
		return isPDF1Diff || isPDF2Diff || isDifferent();
	}

	
	// ------------------------------------- getter and setter -----------------------------------------
	public PDFHolder getPDFStructure2() {
		return pdfStructure2;
	}

	public void setPDFStructure2(PDFHolder pdfStructure2) {
		this.pdfStructure2 = pdfStructure2;
	}

	public PDFHolder getPDFStructure1() {
		return pdfStructure1;
	}

	public void setPDFStructure1(PDFHolder pdfStructure1) {
		this.pdfStructure1 = pdfStructure1;
	}
	

	public File getPDFFile1() {
		return pdfFile1;
	}

	public void setPDFFile1(File pdfF1) {
		this.pdfFile1 = pdfF1;
	}

	public File getPDFFile2() {
		return pdfFile2;
	}

	public void setPDFFile(File pdfF2) {
		this.pdfFile2 = pdfF2;
	}

	public PDFFile getPDF1() {
		return pdf1;
	}
	
	public PDFFile getPDF2() {
		return pdf2;
	}
	
	/**
	 * returns only the file name
	 * 
	 * @return filename
	 */
	public String getFilename()
	{
		return pdfFile1.getName();
	}


	public DifferenceType getDifferent() {
		return difference;
	}
	
	public boolean isDifferent() {
		return (difference != DifferenceType.NONE);
	}

	public void setDifferent(DifferenceType different) {
		this.difference = different;
	}
	
}
