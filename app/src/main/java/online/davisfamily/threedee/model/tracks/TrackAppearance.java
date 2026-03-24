package online.davisfamily.threedee.model.tracks;

import online.davisfamily.threedee.model.ColourPickerStrategy;

public class TrackAppearance {
	public final ColourPickerStrategy deckColour, rollerColour, guideColour;

	public TrackAppearance(ColourPickerStrategy deckColour, ColourPickerStrategy rollerColour,
			ColourPickerStrategy guideColour) {
		super();
		this.deckColour = deckColour;
		this.rollerColour = rollerColour;
		this.guideColour = guideColour;
	}
	
	
}
