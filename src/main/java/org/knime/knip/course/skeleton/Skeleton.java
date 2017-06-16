package org.knime.knip.course.skeleton;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Skeleton {@link Command}.
 * 
 * @author Stefan Helfrich (University of Konstanz)
 *
 * @param <T>
 *            type of input image
 */
@Plugin( menuPath = "DeveloperPlugins>Skeletong", headless = true, type = Command.class, description = "Skeleton with one input image and one double output" )
public class Skeleton< T extends RealType< T > > implements Command
{

	@Parameter
	private Img< T > input;

	@Parameter( type = ItemIO.OUTPUT )
	private double doubleOut;

	@Override
	public void run()
	{
		doubleOut = 1.0d;
	}

}
