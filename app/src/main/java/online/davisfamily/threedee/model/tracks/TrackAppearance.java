package online.davisfamily.threedee.model.tracks;

import online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy;

public class TrackAppearance {
	public final ColourPickerStrategy deckColour;
	public final ColourPickerStrategy rollerColour;
	public final ColourPickerStrategy guideColour;
	public final ColourPickerStrategy transferDeckColour;

	public TrackAppearance(ColourPickerStrategy deckColour,
			ColourPickerStrategy rollerColour,
			ColourPickerStrategy guideColour) {
		this(deckColour, rollerColour, guideColour, deckColour);
	}

	public TrackAppearance(ColourPickerStrategy deckColour,
			ColourPickerStrategy rollerColour,
			ColourPickerStrategy guideColour,
			ColourPickerStrategy transferDeckColour) {
		super();
		this.deckColour = deckColour;
		this.rollerColour = rollerColour;
		this.guideColour = guideColour;
		this.transferDeckColour = transferDeckColour != null ? transferDeckColour : deckColour;
	}
}
