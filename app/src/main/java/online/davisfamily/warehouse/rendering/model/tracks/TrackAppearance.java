package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy;

public class TrackAppearance {
	public final ColourPickerStrategy deckColour;
	public final ColourPickerStrategy rollerColour;
	public final ColourPickerStrategy conveyorBeltColour;
	public final ColourPickerStrategy conveyorMarkerColour;
	public final ColourPickerStrategy guideColour;
	public final ColourPickerStrategy transferDeckColour;

	public TrackAppearance(ColourPickerStrategy deckColour,
			ColourPickerStrategy rollerColour,
			ColourPickerStrategy guideColour) {
		this(deckColour, rollerColour, deckColour, rollerColour, guideColour, deckColour);
	}

	public TrackAppearance(ColourPickerStrategy deckColour,
			ColourPickerStrategy rollerColour,
			ColourPickerStrategy guideColour,
			ColourPickerStrategy transferDeckColour) {
		this(deckColour, rollerColour, deckColour, rollerColour, guideColour, transferDeckColour);
	}

	public TrackAppearance(ColourPickerStrategy deckColour,
			ColourPickerStrategy rollerColour,
			ColourPickerStrategy conveyorBeltColour,
			ColourPickerStrategy conveyorMarkerColour,
			ColourPickerStrategy guideColour,
			ColourPickerStrategy transferDeckColour) {
		super();
		this.deckColour = deckColour;
		this.rollerColour = rollerColour;
		this.conveyorBeltColour = conveyorBeltColour != null ? conveyorBeltColour : deckColour;
		this.conveyorMarkerColour = conveyorMarkerColour != null ? conveyorMarkerColour : rollerColour;
		this.guideColour = guideColour;
		this.transferDeckColour = transferDeckColour != null ? transferDeckColour : deckColour;
	}
}
