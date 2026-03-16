package online.davisfamily.threedee.model;

public class SquareBasedStrategyImpl implements ColourPickerStrategy {
	int[] colours;
	
	public SquareBasedStrategyImpl (int[] faceColours) {
		colours = faceColours;
	}
	// squares / rectangles are made up of 2 triangles, both will be same colour
	@Override
	public int chooseColour(int triangleIndex) {
		return colours[triangleIndex/2];
	}

}
