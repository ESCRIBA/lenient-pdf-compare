## Description
PDFCompare can compare PDF documents from one directory, with PDF files in another directory. The only criteria is they have to have the same file name.

The comparing process can be done in three modes:

# SIMPLE
	Is suited for comparing simple text. It marks words that are different between the two PDF documents.  The tested text have to be roughly on the same height, this way it is possible to find additional line breaks and pages. The content of images or the font size and family does not get analyzed. 

# STRUCTURAL
	This mode is similar to SIMPLE, only that here the text have to be at the approximately  same vertical position. Thereby changes in size of nearby tables or images can be detected.  The overall image does have to be more similar.

# VISUAL
	The 3rd mode uses an other approach. It compares the PDF documents visually with each other. In this pixel-level comparison, differences within graphics or changes of font families can be recognized. The overall image have to be similar, except some varieties in anti-Aliasing.


## Build

To get an executable run the ant script. The "zip" target creates an archive under the "dist" directory. Which contains all necessary libraries and the PDFCompare.jar file. The enclosed batch file shows an example on how to use the tool.

## Usage

The .jar file can be also started via command line.

	java -jar PDFCompare.jar <path 1> <path 2> [-output <true/false>] [-visualise <path 3>] [-log <path 4>] [-compare <compare type>] [-prefix <pdf prefix>]

The first two parameters are mandatory. They define the path to the directories containing the PDF documents. Sub directories are ignored. All other parameters are optional.

* output <true | false> = should the log text get displayed on the console
* visualise <path 3> = save image showing the difference visually there
* log <path 4> = save all log file there (creates a _output.log for common log output and a log file for each failed comparison)
* compare <compare type> = three different comparison modes: SIMPLE, STRUCTURAL, VISUAL
* prefix = compare only PDF which start the this prefix

## License

LGPL-3. For more information see [COPYING.txt](https://github.com/ESCRIBA/lenient-pdf-compare/blob/master/COPYING) file.

