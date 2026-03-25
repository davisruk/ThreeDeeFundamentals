package online.davisfamily.threedee.model.tracks;

import online.davisfamily.threedee.model.tote.ToteEnvelope;

public class TrackSpec {
	public final ToteEnvelope toteEnvelope;
	
	// running surface
	public final float sideClearance;
	public final float deckThickness;
	public final float deckTopY;
	
	// side guides
	public final boolean includeGuides;
	public final float guideHeight;
	public final float guideThickness;
	public final float guideGap;
	public final float connectionGuideCutback;
	
	// rollers
	public final boolean includeRollers;
	public final float rollerPitch;
	public final float rollerWidthInset;
	public final float rollerHeight;
	public final float rollerDepthAlongPath;

	
	// transfer zone rendering
	public final boolean suppressRollersInTransferZones;
	public final boolean suppressGuidesInTransferZones;
	
	// tessellation
	public float sampleStep;

	public TrackSpec(ToteEnvelope toteEnvelope, float sideClearance, float deckThickness, float deckTopY,
			boolean includeGuides, float guideHeight, float guideThickness, float guideGap, float connectionGuideCutback, boolean includeRollers,
			float rollerPitch, float rollerWidthInset, float rollerHeight, float rollerDepthAlongPath,
			float sampleStep) {
		this(toteEnvelope,
				sideClearance,
				deckThickness,
				deckTopY,
				includeGuides,
				guideHeight,
				guideThickness,
				guideGap,
				connectionGuideCutback,
				includeRollers,
				rollerPitch,
				rollerWidthInset,
				rollerHeight,
				rollerDepthAlongPath,
				true,
				false,
				sampleStep);
	}

	public TrackSpec(
			ToteEnvelope toteEnvelope,
			float sideClearance,
			float deckThickness,
			float deckTopY,
			boolean includeGuides,
			float guideHeight,
			float guideThickness,
			float guideGap,
			float connectionGuideCutback,
			boolean includeRollers,
			float rollerPitch,
			float rollerWidthInset,
			float rollerHeight,
			float rollerDepthAlongPath,
			boolean suppressRollersInTransferZones,
			boolean suppressGuidesInTransferZones,
			float sampleStep) {

		this.toteEnvelope = toteEnvelope;
		this.sideClearance = sideClearance;
		this.deckThickness = deckThickness;
		this.deckTopY = deckTopY;
		this.includeGuides = includeGuides;
		this.guideHeight = guideHeight;
		this.guideThickness = guideThickness;
		this.guideGap = guideGap;
		this.connectionGuideCutback = connectionGuideCutback;
		this.includeRollers = includeRollers;
		this.rollerPitch = rollerPitch;
		this.rollerWidthInset = rollerWidthInset;
		this.rollerHeight = rollerHeight;
		this.rollerDepthAlongPath = rollerDepthAlongPath;
		this.suppressRollersInTransferZones = suppressRollersInTransferZones;
		this.suppressGuidesInTransferZones = suppressGuidesInTransferZones;
		this.sampleStep = sampleStep;
	}

	public float getRunningWidth() {
		return toteEnvelope.bottomWidth + (2f * sideClearance);
	}
	
	public float getGuideInnerWidth() {
		return getRunningWidth() + (2f * guideGap);
	}
	
	public float getOverallWidth() {
		if (!includeGuides) return getRunningWidth();
		return getGuideInnerWidth() + (2f * guideThickness);
	}
}
