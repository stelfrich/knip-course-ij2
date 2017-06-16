/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2017
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * --------------------------------------------------------------------- *
 *
 */
package org.knime.knip.course.examples;

import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Geometric.Centroid;
import net.imagej.ops.Ops.Geometric.Contour;
import net.imagej.ops.slice.SlicesII;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.roi.geometric.Polygon;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.IntegerType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Given a ROI and two chosen dimensions, this node slices the ROI in two
 * dimensional slices and computes for each slice the minimum and maximum radius
 * from the centroid to the contour of this slice.
 * 
 * @author Tim-Oliver Buchholz (University of Konstanz)
 * @author Stefan Helfrich (University of Konstanz)
 * 
 * @param <L> type of labeling
 */
@Plugin(type = Command.class, headless = true,
	menuPath = "DeveloperPlugins>Min Max",
	description = "Comptes the min and max radius from centroid to contour.")
public class MinMax<L extends IntegerType<L>> implements Command {

	@Parameter
	private OpService opService;

	@Parameter
	private ImgLabeling<L, ?> inputLabeling;

	// Currently, array inputs are not supported
	@Parameter
	private String selectedDimIndices;

	@Parameter(type = ItemIO.OUTPUT)
	private double[] minMaxs;

	private UnaryFunctionOp<LabelRegion<L>, RealLocalizable> centroidFunction;

	/**
	 * A {@link LabelRegion} to {@link Polygon} converting Op.
	 */
	private UnaryFunctionOp<LabelRegion<L>, Polygon> converter;

	/**
	 * The ops are initialized with the first actual data.
	 * 
	 * @param region the first real LabelRegion
	 */
	private void init(final LabelRegion<L> region) {
		centroidFunction = Functions.unary(opService, Centroid.class,
			RealLocalizable.class, region);
		converter = Functions.unary(opService, Contour.class, Polygon.class, region,
			true);
	}

	/**
	 * Computes for each {@link LabelRegion} in an {@link ImgLabeling} a pair of
	 * the minimum and maximum radius. The ROIs get sliced to 2D slices which then
	 * will be processed individually.
	 */
	@Override
	public void run() {
		// Convert dimensions from String input
		String[] split = selectedDimIndices.split(",");
		int[] selectedDims = new int[split.length];
		for (int i = 0; i < split.length; i++) {
			selectedDims[i] = Integer.parseInt(split[i]);
		}

		if (selectedDims.length != 2) {
			// If a selected dimension does not exist, inform the user.
			throw new IllegalArgumentException(
				"Selected dimensions result in none two dimensional ROIs.");
		}

		final SlicesII<LabelingType<L>> slices = new SlicesII<>(inputLabeling,
			selectedDims, true);
		// final SlicesII<LabelingType<L>> slices = null;

		for (final RandomAccessibleInterval<LabelingType<L>> slice : slices) {
			// Get all ROIs of this slice.
			final LabelRegions<L> regions = new LabelRegions<>(slice);

			int regionCount = 0;
			for (final LabelRegion<L> region : regions) {
				if (centroidFunction == null || converter == null) {
					// Initialize ops with the first available ROI.
					init(region);
					minMaxs = new double[regions.getExistingLabels().size() * 2];
				}

				double[] computeMinMaxRadius = computeMinMaxRadius(region);
				minMaxs[regionCount] = computeMinMaxRadius[0];
				minMaxs[regionCount + 1] = computeMinMaxRadius[1];
				regionCount++;
			}
		}
	}

	/**
	 * Compute the min and max radius for one ROI.
	 * 
	 * @param region the ROI
	 * @return the min and max radius
	 */
	private double[] computeMinMaxRadius(final LabelRegion<L> region) {
		final RealLocalizable centroid = centroidFunction.calculate(region);
		final Polygon poly = converter.calculate(region);

		double minDist = Double.MAX_VALUE;
		double maxDist = 0;
		double tmpDist;
		for (final RealLocalizable v : poly.getVertices()) {
			tmpDist = Math.sqrt(Math.pow(centroid.getDoublePosition(0) - v
				.getDoublePosition(0), 2) + Math.pow(centroid.getDoublePosition(1) - v
					.getDoublePosition(1), 2));
			minDist = tmpDist < minDist ? tmpDist : minDist;
			maxDist = tmpDist > maxDist ? tmpDist : maxDist;
		}

		return new double[] { minDist, maxDist };
	}
}
