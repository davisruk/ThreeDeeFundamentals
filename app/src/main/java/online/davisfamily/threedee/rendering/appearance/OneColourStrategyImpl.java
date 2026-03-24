package online.davisfamily.threedee.rendering.appearance;

public class OneColourStrategyImpl implements ColourPickerStrategy {
	int colour;
	public OneColourStrategyImpl(int colour) {
		this.colour = colour;
	}
	
	@Override
	public int chooseColour(int triangleIndex) {
		// TODO Auto-generated method stub
		return colour;
	}
}
