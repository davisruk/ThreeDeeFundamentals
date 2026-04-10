package online.davisfamily.warehouse.rendering.model.tote;

public class ToteEnvelope {
	public final float bottomWidth, bottomDepth, height;
	
	public ToteEnvelope(float bottomWidth, float bottomDepth, float height) {
		this.bottomDepth = bottomDepth;
		this.bottomWidth = bottomWidth;
		this.height = height;
	}
	
	public static ToteEnvelope createToteEnvelpe(ToteGeometry tote) {
		return new ToteEnvelope(tote.outerBottomWidth, tote.outerBottomDepth, tote.outerHeight);
	}
}
