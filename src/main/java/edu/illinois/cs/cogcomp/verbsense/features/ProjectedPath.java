package edu.illinois.cs.cogcomp.verbsense.features;

import edu.illinois.cs.cogcomp.edison.features.DiscreteFeature;
import edu.illinois.cs.cogcomp.edison.features.Feature;
import edu.illinois.cs.cogcomp.edison.features.FeatureExtractor;
import edu.illinois.cs.cogcomp.edison.features.helpers.PathFeatureHelper;
import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.Relation;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.TreeView;
import edu.illinois.cs.cogcomp.edison.utilities.EdisonException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectedPath implements FeatureExtractor {

	private String parseViewName;

	public ProjectedPath(String parseViewName) {
		this.parseViewName = parseViewName;
	}

	@Override
	public Set<Feature> getFeatures(Constituent c) throws EdisonException {
		TextAnnotation ta = c.getTextAnnotation();

		TreeView parse = (TreeView) ta.getView(parseViewName);

		Set<Feature> feats = new HashSet<>();

        // Clone this to avoid concurrency problems
        Constituent c2 = parse.getParsePhrase(c).cloneForNewView("");

		if (!c2.getLabel().equals("VP"))
			return feats;

		boolean found = false;
		boolean done = false;

		while (!done) {
			List<Relation> rels = c2.getIncomingRelations();
			if (rels.size() == 0)
				done = true;
			else {
				Constituent parent = rels.get(0).getSource();
				if (parent.getLabel().equals("VP")) {
					found = true;
					c2 = parent;
				} else {
					done = true;
				}
			}
		}

		if (found) {
            // Clone this to avoid concurrency problems
            Constituent c1 = parse.getParsePhrase(c.getIncomingRelations().get(0).getSource()).cloneForNewView("");

			String path = PathFeatureHelper.getFullParsePathString(c1, c2, 400);
			feats.add(DiscreteFeature.create(path));
		}

		return feats;

	}

	@Override
	public String getName() {
		return "#proj-path";
	}

}
