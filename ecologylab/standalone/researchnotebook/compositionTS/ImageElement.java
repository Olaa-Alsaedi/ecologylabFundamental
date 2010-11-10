package ecologylab.standalone.researchnotebook.compositionTS;

import ecologylab.net.ParsedURL;
import ecologylab.serialization.ElementState;
import ecologylab.serialization.enums.Hint;

public class ImageElement extends ElementState{
	@simpl_scalar float bias;
	@simpl_scalar ParsedURL href; 
	@simpl_scalar boolean has_transparency;
	@simpl_scalar int min_alpha;
	@simpl_scalar int alpha_radius;
	
	@simpl_composite @simpl_hints(Hint.XML_LEAF) Image image; 
}
