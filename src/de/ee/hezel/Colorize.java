/**
 * 
 */
package de.ee.hezel;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Translation from gimp source code to java done by nwodb.com
 * http://stackoverflow.com/questions/23763/colorizing-images-in-java
 *
 */
public class Colorize {

	public static final int MAX_COLOR = 256;
	
	public static final float LUMINANCE_RED   = 0.2126f;
	public static final float LUMINANCE_GREEN = 0.7152f;
	public static final float LUMINANCE_BLUE  = 0.0722f;
	
	double hue        =   0;
	double saturation =  20;
	double lightness  =   0;
	
	int [] lum_red_lookup;
	int [] lum_green_lookup;
	int [] lum_blue_lookup;
	
	int [] final_red_lookup;
	int [] final_green_lookup;
	int [] final_blue_lookup;

	public Colorize( int red, int green, int blue )
	{
		float[] hsbvals = new float[3];
		Color.RGBtoHSB(red, green, blue, hsbvals);

		hue = hsbvals[0] * 360f;
		saturation = hsbvals[1] * 100f;
		lightness = hsbvals[2] * 255f;
		doInit();
	}

	public Colorize( double t_hue, double t_sat, double t_bri )
	{
		hue = t_hue;
		saturation = t_sat;
		lightness = t_bri;
		doInit();
	}

	public Colorize( double t_hue, double t_sat )
	{
		hue = t_hue;
		saturation = t_sat;
		doInit();
	}

	public Colorize( double t_hue )
	{
		hue = t_hue;
		doInit();
	}

	public Colorize()
	{
		doInit();
	}
	
	private void doInit()
	{
		lum_red_lookup   = new int [MAX_COLOR];
		lum_green_lookup = new int [MAX_COLOR];
		lum_blue_lookup  = new int [MAX_COLOR];
	
		double temp_hue = hue / 360f;
		double temp_sat = saturation / 100f;
	
		final_red_lookup   = new int [MAX_COLOR];
		final_green_lookup = new int [MAX_COLOR];
		final_blue_lookup  = new int [MAX_COLOR];
	
		for( int i = 0; i < MAX_COLOR; ++i )
		{
			lum_red_lookup  [i] = ( int )( i * LUMINANCE_RED );
			lum_green_lookup[i] = ( int )( i * LUMINANCE_GREEN );
			lum_blue_lookup [i] = ( int )( i * LUMINANCE_BLUE );
	
			double temp_light = (double)i / 255f;
		
			Color color = new Color( Color.HSBtoRGB( (float)temp_hue, 
		                                             (float)temp_sat, 
		                                             (float)temp_light ) );
		
			final_red_lookup  [i] = ( int )( color.getRed() );
			final_green_lookup[i] = ( int )( color.getGreen() );
			final_blue_lookup [i] = ( int )( color.getBlue() );
		}
	}

	public void doColorize( BufferedImage image )
	{
		int height = image.getHeight();
		int width;
	
		while( height-- != 0 )
		{
			width = image.getWidth();
			while( width-- != 0 )
			{
				Color color = new Color( image.getRGB( width, height ) );
			
				int lum = lum_red_lookup  [color.getRed  ()] +
			    		  lum_green_lookup[color.getGreen()] +
			    		  lum_blue_lookup [color.getBlue ()];
	
				if( lightness > 0 )
				{
					lum = (int)((double)lum * (100f - lightness) / 100f);
					lum += 255f - (100f - lightness) * 255f / 100f;
				}
				else if( lightness < 0 )
				{
					lum = (int)(((double)lum * lightness + 100f) / 100f);
				}
	
				if(lum > 255) lum = 255;
				
				Color final_color = new Color( final_red_lookup[lum],
	                                    	   final_green_lookup[lum],
	                                    	   final_blue_lookup[lum],
	                                    	   color.getAlpha() );
	
				image.setRGB( width, height, final_color.getRGB() );
			}
		}
	}
}