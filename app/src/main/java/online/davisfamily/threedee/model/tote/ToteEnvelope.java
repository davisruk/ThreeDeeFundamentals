package online.davisfamily.threedee.model.tote;

public class ToteEnvelope {
	public final float bottomWidth, bottomDepth, height;
	
	public ToteEnvelope(float bottomWidth, float bottomDepth, float height) {
		this.bottomDepth = bottomDepth;
		this.bottomWidth = bottomWidth;
		this.height = height;
	}
	
	public static ToteEnvelope createToteEnvelpe(Tote tote) {
		return new ToteEnvelope(tote.outerBottomWidth, tote.outerBottomDepth, tote.outerHeight);
	}
}
