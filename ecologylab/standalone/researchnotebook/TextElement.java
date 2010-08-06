package ecologylab.standalone.researchnotebook;

import ecologylab.serialization.ElementState;
import ecologylab.serialization.Hint;

public class TextElement extends ElementState{
	@simpl_scalar float bias; 
	@simpl_scalar String stroke_color; 
	@simpl_scalar String font_color; 
	
	@simpl_composite Text text; 
	@simpl_composite TextChunk text_chunk; 	
}