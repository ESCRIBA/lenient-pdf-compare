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
public class PDFInfoHolder {
	
	static Logger log = Logger.getLogger(PDFInfoHolder.class.getName());
	
	private File pdfFile1;
	private File pdfFile2;

	private PDFHolder pdfStructure1;
	private PDFHolder pdfStructure2;
	
	private PDFFile pdf1;
	private PDFFile pdf2;
	
	private boolean isDifferent;
	
	public PDFInfoHolder(File pdfF1, File pdfF2)
	{
		this.pdfFile1 = pdfF1;
		this.pdfFile2 = pdfF2;
		this.isDifferent = false;	
		
		loadPDFFiles(pdfF1, pdfF2);
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


	public boolean isDifferent() {
		return isDifferent;
	}

	public void setDifferent(boolean isDifferent) {
		this.isDifferent = isDifferent;
	}
	
	public boolean checkDifference()
	{
		// überprüfe welche der beiden PDFs an welcher stelle unterschiedlich sind
		boolean isPDF1Diff = pdfStructure1.checkDifference();
		boolean isPDF2Diff = pdfStructure2.checkDifference();
		
		// überprüfe nur die erste Struktur
		this.isDifferent = isPDF1Diff || isPDF2Diff || this.isDifferent;
		return this.isDifferent;
	}

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
	
	private void loadPDFFiles(File pdfF1, File pdfF2)
	{
        try {
            pdf1 = loadPDFFile(pdfF1);
        } catch (IOException e) {
            log.error("Unable to load PDF file: " + pdfF1.getName() + ". Reason: " + e.getMessage(), e);
            this.isDifferent = true;
        }
        
        try {
            pdf2 = loadPDFFile(pdfF2);
        } catch (IOException e) {
        	log.error("Unable to load PDF file: " + pdfF2.getName() + ". Reason: " + e.getMessage(), e);
        	this.isDifferent = true;
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
		RandomAccessFile raf = new RandomAccessFile(pdfFile, "r");
		FileChannel channel = raf.getChannel();
		ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
		
		PDFFile s = new PDFFile(buf);
		channel.close();
		raf.close();

		return s;
	}
}
