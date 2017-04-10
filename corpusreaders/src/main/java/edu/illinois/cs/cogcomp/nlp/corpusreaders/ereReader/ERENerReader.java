/**
 * This software is released under the University of Illinois/Research and Academic Use License. See
 * the LICENSE file in the root folder for details. Copyright (c) 2016
 *
 * Developed by: The Cognitive Computation Group University of Illinois at Urbana-Champaign
 * http://cogcomp.cs.illinois.edu/
 */
package edu.illinois.cs.cogcomp.nlp.corpusreaders.ereReader;

import edu.illinois.cs.cogcomp.core.utilities.AnnotationFixer;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.utilities.StringTransformation;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.aceReader.SimpleXMLParser;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.aceReader.XMLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.*;


/**
 * Reads ERE data and instantiates TextAnnotations with the corresponding NER view. Also provides
 * functionality to support combination with readers of other ERE annotations from the same source.
 *
 * ERE annotations are provided in stand-off form: each source file (in xml, and from which
 * character offsets are computed) has one or more corresponding annotation files (also in xml).
 * Each annotation file corresponds to a span of the source file, and contains all information about
 * entities, relations, and events for that span. Entity and event identifiers presumably carry
 * across spans from the same document.
 *
 * This reader allows the user to generate either a mention view or an NER view. NERs can be
 * identified in a mention view via its type attribute.
 *
 * TODO: ascertain whether NER mentions can overlap. Probably not. TODO: allow non-token-level
 * annotations (i.e. subtokens)
 *
 * This code is based on Tom Redman's code for generating CoNLL-format ERE NER data.
 * 
 * @author mssammon
 */
public class ERENerReader extends EREDocumentReader {

    public static final String IS_FOUND = "isFoundInText";
    private static final String NAME = EREDocumentReader.class.getCanonicalName();
    private static final Logger logger = LoggerFactory.getLogger(ERENerReader.class);
    private final boolean addNominalMentions;
    private final String viewName;
    private final boolean addFillers;

    private int numOverlaps = 0;
    private int numOffsetErrors = 0;
    // private int numConstituent = 0;

    private int starts[];
    private int ends[];
    /**
     * ERE annotation offsets appear to include some errors, such as including a leading space
     */
    private boolean allowOffsetSlack;

    /**
     * ERE annotations allow for sub-word annotation.
     */
    private boolean allowSubwordOffsets;
    private Map<String, Constituent> mentionIdToConstituent;
    private Map<String, Set<String>> entityIdToMentionIds;
    private int numEntitiesInSource;
    private int numEntitiesGenerated;
    private int numMentionsInSource;
    private int numMentionsGenerated;
//    private int numFillersInSource;
//    private int numFillersGenerated;


    /**
     * @param corpusName the name of the corpus, this can be anything.
     * @param sourceDirectory the name of the directory containing the file.
     * @param addNominalMentions a flag that if true, indicates that all mentions should be read,
     *        and that the view created should be named {#ViewNames.MENTION_ERE}. Otherwise, only
     *        named entities (proper nouns/personal names) are read.
     * @throws Exception
     */
    public ERENerReader(String corpusName, String sourceDirectory, boolean addNominalMentions, boolean throwExceptionOnXmlTagMismatch)
            throws Exception {
        super(corpusName, sourceDirectory, throwExceptionOnXmlTagMismatch);
        this.addNominalMentions = addNominalMentions;
        this.viewName = addNominalMentions ? ViewNames.MENTION_ERE : ViewNames.NER_ERE;
        allowOffsetSlack = true;
        allowSubwordOffsets = true;
        /*
         * fillers are arguments of relations/events that don't have a referent entity -- they are
         * too general and cannot be determined to co-refer with any other mentions. e.g. position
         * titles
         */
        addFillers = true;

        mentionIdToConstituent = new HashMap<>();
        entityIdToMentionIds = new HashMap<>();

        this.numMentionsInSource = 0;
        this.numMentionsGenerated = 0;
        this.numEntitiesInSource = 0;
        this.numEntitiesGenerated = 0;
    }

    @Override
    public void reset() {
        super.reset();
        this.numMentionsInSource = 0;
        this.numMentionsGenerated = 0;
        this.numEntitiesInSource = 0;
        this.numEntitiesGenerated = 0;
    }


    @Override
    public List<XmlTextAnnotation> getAnnotationsFromFile(List<Path> corpusFileListEntry)
            throws Exception {

        mentionIdToConstituent.clear();
        entityIdToMentionIds.clear();

        XmlTextAnnotation sourceTa = super.getAnnotationsFromFile(corpusFileListEntry).get(0);
        TextAnnotation ta = sourceTa.getTextAnnotation();
        SpanLabelView tokens = (SpanLabelView) ta.getView(ViewNames.TOKENS);
        compileOffsets(tokens);
        SpanLabelView nerView = new SpanLabelView(getViewName(), NAME, ta, 1.0, false);

        // now pull all mentions we deal with. Start from file list index 1, as index 0 was source
        // text
        for (int i = 1; i < corpusFileListEntry.size(); ++i) {
            Document doc = SimpleXMLParser.getDocument(corpusFileListEntry.get(i).toFile());
            getEntitiesFromFile(doc, nerView, sourceTa);

            if (addFillers)
                getFillersFromFile(doc, nerView, sourceTa);
        }

        sourceTa.getTextAnnotation().addView(getViewName(), nerView);

        if (addNominalMentions) {
            addCorefView(sourceTa);
            AnnotationFixer.rationalizeBoundaryAnnotations(sourceTa.getTextAnnotation(), ViewNames.COREF_ERE);
        }
        else
            AnnotationFixer.rationalizeBoundaryAnnotations(sourceTa.getTextAnnotation(), getViewName());

        // logger.info("number of constituents created: {}", numConstituent );
        logger.debug("number of overlaps preventing creation: {}", numOverlaps);
        logger.debug("number of missed offsets (annotation error): {}", numOffsetErrors);

        return Collections.singletonList(sourceTa);
    }


    private void addCorefView(XmlTextAnnotation xmlTa) {

        TextAnnotation ta = xmlTa.getTextAnnotation();
        CoreferenceView cView = new CoreferenceView(ViewNames.COREF_ERE, ta);
        for (String eId : entityIdToMentionIds.keySet()) {
            Set<String> mentionIds = entityIdToMentionIds.get(eId);
            Constituent canonical = null;
            List<Constituent> otherMents = new LinkedList<>();
            for (String mId : mentionIds) {
                Constituent ment = mentionIdToConstituent.get(mId);
                Constituent corefMent =
                        new Constituent(ment.getLabel(), ViewNames.COREF_ERE, ta,
                                ment.getStartSpan(), ment.getEndSpan());
                for (String att : ment.getAttributeKeys())
                    corefMent.addAttribute(att, ment.getAttribute(att));

                if (null == canonical
                        || (canonical.size() < corefMent.size() && !canonical.getAttribute(
                                EntityMentionTypeAttribute).equals(NAM))) {
                    canonical = corefMent;
                    otherMents.remove(canonical);
                } else
                    otherMents.add(corefMent);
            }
            cView.addCorefEdges(canonical, otherMents);
        }
        ta.addView(cView.getViewName(), cView);
    }



    protected void getFillersFromFile(Document doc, View nerView, XmlTextAnnotation xmlTa) throws XMLException {
        Element element = doc.getDocumentElement();
        Element fillerElement = SimpleXMLParser.getElement(element, FILLERS);
        NodeList fillerNl = fillerElement.getElementsByTagName(FILLER);

        for (int i = 0; i < fillerNl.getLength(); ++i)
            readFiller(fillerNl.item(i), nerView, xmlTa);
    }

    /**
     * WARNING: filler can have null value.
     * 
     * @param fillerNode
     * @param view
     */
    private void readFiller(Node fillerNode, View view, XmlTextAnnotation xmlTa) throws XMLException {
        NamedNodeMap nnMap = fillerNode.getAttributes();
        String fillerId = nnMap.getNamedItem(ID).getNodeValue();
        int offset = Integer.parseInt(nnMap.getNamedItem(OFFSET).getNodeValue());
        int length = Integer.parseInt(nnMap.getNamedItem(LENGTH).getNodeValue());
        String fillerForm = SimpleXMLParser.getContentString((Element) fillerNode);
        if (null == fillerForm || "".equals(fillerForm))
            throw new IllegalStateException("ERROR: did not find surface form for filler "
                    + nnMap.getNamedItem(ID).getNodeValue());
        IntPair offsets = getTokenOffsets(offset, offset + length, fillerForm, xmlTa);
        if (null != offsets) {
            if (-1 == offsets.getFirst() || -1 == offsets.getSecond()) {
                throw new IllegalStateException("ERROR: got an indication of deleted span for filler." +
                "Since filler should not be an entity, EITHER it was in a quoted span, and therefore " +
                "should not have been annotated, or it's in a deleted span that should not have been deleted (check" +
                " EREDocumentReader's use of XmlDocumentProcessor; were the right tags provided at construction?)");
            }

            String fillerType = nnMap.getNamedItem(TYPE).getNodeValue();
            if (offsets.getSecond() < offsets.getFirst()) {
                logger.warn("for filler {}, second offset is less than first (first, second:{})",
                        fillerId, "(" + offsets.getFirst() + "," + offsets.getSecond());
            }
            Constituent fillerConstituent =
                    new Constituent(fillerType, view.getViewName(), view.getTextAnnotation(),
                            offsets.getFirst(), offsets.getSecond() + 1);
            fillerConstituent.addAttribute(EntityMentionIdAttribute, fillerId);
            fillerConstituent.addAttribute(EntityMentionTypeAttribute, FILL);
            view.addConstituent(fillerConstituent);
            mentionIdToConstituent.put(fillerId, fillerConstituent);
        } else
            logger.warn("could not create filler with id '{}'", nnMap.getNamedItem(ID)
                    .getNodeValue());
    }

    /**
     * Read entity mentions and populate the view provided.
     * 
     * @param doc XML document containing entity information.
     * @param nerView View to populate with new entity mentions
     * @throws XMLException
     */
    protected void getEntitiesFromFile(Document doc, View nerView, XmlTextAnnotation xmlTa) throws XMLException {
        Element element = doc.getDocumentElement();
        Element entityElement = SimpleXMLParser.getElement(element, ENTITIES);
        NodeList entityNL = entityElement.getElementsByTagName(ENTITY);
        for (int j = 0; j < entityNL.getLength(); ++j) {
            readEntity(entityNL.item(j), nerView, xmlTa);
        }
    }



    /**
     * get the start and end offsets of all constituents and store them
     * note that these are based on the cleaned-up text, so need to be mapped back
     * to the original text.
     * 
     * @param tokens SpanLabelView containing Token info (from TextAnnotation)
     */
    private void compileOffsets(SpanLabelView tokens) {
        List<Constituent> constituents = tokens.getConstituents();
        int n = constituents.size();
        starts = new int[n];
        ends = new int[n];
        int i = 0;
        for (Constituent cons : tokens.getConstituents()) {
            starts[i] = cons.getStartCharOffset();
            ends[i] = cons.getEndCharOffset();
            i++;
        }
    }

    /**
     * Find the index of the first constituent at startOffset.
     * 
     * @param startOffset the character offset we want.
     * @return the index of the first constituent.
     */
    private int findStartIndex(int startOffset) {
        for (int i = 0; i < starts.length; i++) {
            if (startOffset == starts[i])
                return i;
            if (startOffset < starts[i]) {
                if (allowOffsetSlack)
                    if (startOffset == starts[i] - 1)
                        return i;
                throw new RuntimeException("Index " + startOffset + " was not exact.");
            }
        }
        throw new RuntimeException("Index " + startOffset + " was out of range.");
    }


    /**
     * Find the index of the first constituent *near* startOffset.
     * 
     * @param startOffset the character offset we want.
     * @return the index of the first constituent.
     */
    private int findStartIndexIgnoreError(int startOffset) {
        for (int i = 0; i < starts.length; i++) {
            if (startOffset <= starts[i])
                return i;
        }
        throw new RuntimeException("Index " + startOffset + " was out of range.");
    }


    /**
     * Find the index of the first token constituent that has end char offset "endOffset" and return
     * the value one higher than that index (to instantiate Constituents, which use one-past-the-end
     * indexing).
     * 
     * @param endOffset the character offset for which we want a corresponding token index.
     * @return the index of the token.
     */
    private int findEndIndex(int endOffset, String rawText) {
        int prevOffset = 0;
        for (int i = 0; i < ends.length; i++) {
            if (endOffset == ends[i])
                return i;
            if (endOffset < ends[i]) {
                if (allowSubwordOffsets && endOffset == ends[i] - 1)
                    return i;
                else if (allowOffsetSlack && endOffset == prevOffset + 1
                        && rawText.substring(prevOffset, prevOffset + 1).matches("\\s+"))
                    return i - 1;
                throw new RuntimeException("End Index " + endOffset + " was not exact.");
            }
            prevOffset = ends[i];
        }
        throw new RuntimeException("Index " + endOffset + " was out of range.");
    }

    /**
     * Find the index of the first constituent at startOffset. Return that index + 1 (for
     * past-the-end indexing used by Constituents)
     * 
     * @param endOffset the character offset we want.
     * @return one plus the index of the first token that has that end character offset.
     */
    private int findEndIndexIgnoreError(int endOffset) {
        for (int i = 0; i < ends.length; i++) {
            if (endOffset <= ends[i])
                if (i > 0 && Math.abs(endOffset - ends[i]) > Math.abs(endOffset - ends[i - 1]))
                    return i;
                else
                    return i + 1;
        }
        throw new RuntimeException("Index " + endOffset + " was out of range.");
    }


    /**
     * read the entities from the gold standard xml and produce appropriate constituents in the
     * view. NOTE: the constituents will not be ordered when we are done.
     *
     * <entity id="ent-56bd16d7_2_1620" type="FAC" specificity="nonspecific"> <entity_mention
     * id="m-56bd16d7_2_480" noun_type="NOM" source="ENG_DF_001241_20150407_F0000007T" offset="1645"
     * length="11"> <mention_text>restaurants</mention_text> <nom_head
     * source="ENG_DF_001241_20150407_F0000007T" offset="1645" length="11">restaurants</nom_head>
     * </entity_mention> </entity>
     *
     * @param eNode the entity node, contains the more specific mentions of that entity.
     * @param view the span label view we will add the labels to.
     * @throws XMLException
     */
    public void readEntity(Node eNode, View view, XmlTextAnnotation xmlTa) throws XMLException {
        NamedNodeMap nnMap = eNode.getAttributes();
        String label = nnMap.getNamedItem(TYPE).getNodeValue();
        String eId = nnMap.getNamedItem(ID).getNodeValue();
        String specificity = nnMap.getNamedItem(SPECIFICITY).getNodeValue();

        numEntitiesInSource++;
        // now for specifics get the mentions.
        NodeList nl = ((Element) eNode).getElementsByTagName(ENTITY_MENTION);

        boolean isMentionAdded = false;
        for (int i = 0; i < nl.getLength(); ++i) {
            Node mentionNode = nl.item(i);
            Constituent mentionConstituent = getMention(mentionNode, label, view, xmlTa);
            if (null == mentionConstituent) { // mention may reference xml markup
                recordNullMentionInfo(label, eId, specificity, mentionNode, xmlTa);
            }
            else {
                mentionConstituent.addAttribute(EntityIdAttribute, eId);
                mentionConstituent.addAttribute(EntitySpecificityAttribute, specificity);
                view.addConstituent(mentionConstituent);
                isMentionAdded = true;
                numMentionsGenerated++;

                Set<String> mentionIds = entityIdToMentionIds.get(eId);
                if (null == mentionIds) {
                    mentionIds = new HashSet<>();
                    entityIdToMentionIds.put(eId, mentionIds);
                }
                mentionIds.add(mentionConstituent.getAttribute(EntityMentionIdAttribute));
            }
        }
        if (isMentionAdded)
            numEntitiesGenerated++;
    }

    /**
     * for a mention that could not be mapped to a set of tokens in the cleaned text, record the information
     *    to allow use of information by downstream systems in the XmlTextAnnotation object associated with the
     *    source xml.
     * @param label
     * @param eId
     * @param specificity
     * @param mentionNode
     * @param xmlTa
     */
    private void recordNullMentionInfo(String label, String eId, String specificity, Node mentionNode, XmlTextAnnotation xmlTa) throws XMLException {

        NamedNodeMap nnMap = mentionNode.getAttributes();
        String mId = nnMap.getNamedItem(ID).getNodeValue();
        String nounType = nnMap.getNamedItem(NOUN_TYPE).getNodeValue();

        /*
         * expect one child
         */
        NodeList mnl = ((Element) mentionNode).getElementsByTagName(MENTION_TEXT);
        boolean notFound = false;
        String mentionForm = null;

        if (mnl.getLength() > 0) {
            mentionForm = SimpleXMLParser.getContentString((Element) mnl.item(0));
        } else {
            logger.error("No surface form found for mention with id {}.", mId);
        }

        int offset = Integer.parseInt(nnMap.getNamedItem(OFFSET).getNodeValue());
        int length = Integer.parseInt(nnMap.getNamedItem(LENGTH).getNodeValue());

        IntPair origOffsets = new IntPair(offset, offset + length);

        Map<IntPair, Map<String, String>> spanInfo = xmlTa.getXmlMarkup();

        boolean isFound = true;
        Map<String, String> mentionInfo = spanInfo.get(origOffsets);

        if (!spanInfo.containsKey(origOffsets)) {
            logger.warn("could not find offset pair (" + offset + "," + (offset + length) + ") in xml markup info " +
                "in XmlTextAnnotation. Entity id, label, form are: " + eId + "," + label + "," + mentionForm + ".");
            isFound = false;
            mentionInfo = new HashMap<>();
            spanInfo.put(origOffsets, mentionInfo);
        }
        mentionInfo.put(ENTITY_ID, eId);
        mentionInfo.put(ENTITY_MENTION_ID, mId);
        mentionInfo.put(SPECIFICITY, specificity);
        mentionInfo.put(NOUN_TYPE, nounType);
        mentionInfo.put(IS_FOUND, Boolean.toString(isFound));
    }


    private Constituent getMention(Node mentionNode, String label, View view, XmlTextAnnotation xmlTa) throws XMLException {
        Constituent mentionConstituent = null;
        NamedNodeMap nnMap = mentionNode.getAttributes();
        String noun_type = nnMap.getNamedItem(NOUN_TYPE).getNodeValue();
        String mId = nnMap.getNamedItem(ID).getNodeValue();

        if (noun_type.equals(PRO) || noun_type.equals(NOM)) {
            if (!addNominalMentions)
                return null;
        }

        /*
         * update this count here to avoid creating discrepancy in file count vs created count if
         * user does not add nominal mentions
         */
        numMentionsInSource++;
        // we have a valid mention(a "NAM" or a "NOM"), add it to out view.
        int offset = Integer.parseInt(nnMap.getNamedItem(OFFSET).getNodeValue());
        int length = Integer.parseInt(nnMap.getNamedItem(LENGTH).getNodeValue());

        /*
         * expect one child
         */
        NodeList mnl = ((Element) mentionNode).getElementsByTagName(MENTION_TEXT);

        String mentionForm = null;
        if (mnl.getLength() > 0) {
            mentionForm = SimpleXMLParser.getContentString((Element) mnl.item(0));
        } else {
            logger.error("No surface form found for mention with id {}.", mId);
            return null;
        }

        IntPair offsets = getTokenOffsets(offset, offset + length, mentionForm, xmlTa);
        if (null == offsets)
            return null;
        else if (-1 == offsets.getFirst() && -1 == offsets.getSecond()) { // offsets correspond to deleted span
            return null; // handled by next layer up, which records the info separately
        }

        String headForm = null;
        IntPair headTokenOffsets = null;
        mnl = ((Element) mentionNode).getElementsByTagName(MENTION_HEAD);
        if (mnl.getLength() > 0) {

            Node headNode = mnl.item(0);
            nnMap = mentionNode.getAttributes();
            headForm = headNode.getNodeValue();
            int headStart = Integer.parseInt(nnMap.getNamedItem(OFFSET).getNodeValue());
            int headLength = Integer.parseInt(nnMap.getNamedItem(LENGTH).getNodeValue());

            headTokenOffsets = getTokenOffsets(headStart, headStart + headLength, headForm, xmlTa);
        }
        if (null == headTokenOffsets)
            headTokenOffsets = offsets;

        IntPair headCharOffsets =
                getCharacterOffsets(headTokenOffsets.getFirst(), headTokenOffsets.getSecond());

        try {
            mentionConstituent =
                    new Constituent(label, getViewName(), view.getTextAnnotation(),
                            offsets.getFirst(), offsets.getSecond() + 1);
            mentionConstituent.addAttribute(EntityMentionTypeAttribute, noun_type);
            mentionConstituent.addAttribute(EntityMentionIdAttribute, mId);
            mentionConstituent.addAttribute(EntityHeadStartCharOffset,
                    Integer.toString(headCharOffsets.getFirst()));
            mentionConstituent.addAttribute(EntityHeadEndCharOffset,
                    Integer.toString(headCharOffsets.getSecond()));
            mentionIdToConstituent.put(mId, mentionConstituent);
        } catch (IllegalArgumentException iae) {
            numOverlaps++;
        }
        return mentionConstituent;
    }

    /**
     * find the start and end character offsets for the corresponding token index. Expects the
     * actual token index for the endTokOffset, NOT one-past-the-end.
     *
     * @param startTokOffset
     * @param endTokOffset
     * @return
     */
    private IntPair getCharacterOffsets(int startTokOffset, int endTokOffset) {

        if (startTokOffset > starts.length)
            throw new IllegalArgumentException("Start token offset '" + startTokOffset
                    + "' exceeds size of stored token offset array.");
        if (endTokOffset > ends.length)
            throw new IllegalArgumentException("End token offset '" + endTokOffset
                    + "' exceeds size of stored token offset array.");
        int startChar = starts[startTokOffset];
        int endChar = ends[endTokOffset];

        return new IntPair(startChar, endChar);
    }

// TODO: need to handle poster names, which are no longer kept in the cleaned text

    /**
     * find the token offsets in the TextAnnotation that correspond to the source character offsets for the given
     *    mention
     * @param origStartOffset start character offset from xml markup
     * @param origEndOffset end character offset from xml markup
     * @param mentionForm mention form from xml markup
     * @param xmlTa XmlTextAnnotation object storing original xml, transformed text, extracted xml markup,
     *              and corresponding TextAnnotation
     * @return Intpair(-1, -1) if the specified offsets correspond to deleted span (and hence likely a name mention
     *          in xml metadata, e.g. post author); null if no mapped tokens could be found (possibly, indexes refer
     *          to the middle of a single token because tokenizer can't segment some strings); or the corresponding
     *          token indexes
     */
    private IntPair getTokenOffsets(int origStartOffset, int origEndOffset, String mentionForm, XmlTextAnnotation xmlTa) {

        StringTransformation st = xmlTa.getXmlSt();
        int adjStart = st.computeModifiedOffsetFromOriginal(origStartOffset);
        int adjEnd = st.computeModifiedOffsetFromOriginal(origEndOffset);

        if (adjStart == adjEnd) { // probably, maps to span deleted when creating cleaned-up text
            return new IntPair(-1, -1);
        }

        IntPair returnOffset = null;
        int si = 0, ei = 0;
        TextAnnotation ta = xmlTa.getTextAnnotation();
        String rawText = ta.getText();
        String rawStr = rawText.substring(adjStart, adjEnd);
        String origStr = st.getOrigText().substring(origStartOffset, origEndOffset);
        logger.debug("source xml str: '" + origStr + "' (" + origStartOffset + "," + origEndOffset + ")");
        try {
            si = findStartIndex(adjStart);
            ei = findEndIndex(adjEnd, rawText);
            returnOffset = new IntPair(si, ei);
        } catch (IllegalArgumentException iae) {
            logger.error("could not find token offsets for mention form '" + mentionForm + ", start, end orig: (" +
                    origStartOffset + "," + origEndOffset + "); adjusted: (" + adjStart + "," + adjEnd + ")." );
            System.exit(1);
        } catch (RuntimeException re) {
            numOffsetErrors++;
            logger.error("Error finding text for '{}' at offsets {}:", rawStr, (adjStart + "-" + adjEnd));
            boolean siwaszero = false;
            if (si == 0) {
                siwaszero = true;
            }
            si = findStartIndexIgnoreError(adjStart);
            ei = findEndIndexIgnoreError(adjEnd);
            if (siwaszero)
                logger.error("Could not find start token : text='" + mentionForm + "' at adjusted offsets " + adjStart
                        + " to " + adjEnd);
            else
                logger.error("Could not find end token : text='" + mentionForm + "' at adjusted offsets " + adjStart
                        + " to " + adjEnd);
            int max = ta.getTokens().length;
            int start = si >= 2 ? si - 2 : 0;
            int end = (ei + 2) < max ? ei + 2 : max;
            StringBuilder bldr = new StringBuilder();
            for (int jj = start; jj < end; jj++) {
                bldr.append(" ");
                if (jj == si)
                    bldr.append(":");
                bldr.append(ta.getToken(jj));
                if (jj == ei)
                    bldr.append(":");
                bldr.append(" ");
            }
            bldr.append("\n");
            logger.error(bldr.toString());
        }
        return returnOffset;
    }

    public String getViewName() {
        return viewName;
    }

    /**
     * after reading a file's entity information, allows the client to find a Constituent
     * corresponding to an entity mention id. Returns 'null' if the Constituent does not exist (due
     * to a problem with the annotation file (inaccurate offsets), or tokenization is incorrect
     * (target name is part of compound term), or other constraints apply (e.g. if overlapping
     * entity mentions are prohibited)
     * 
     * @param mentionId mentionId parsed from the annotation file
     * @return Constituent corresponding to the mentionId, or null if it is not found
     */
    protected Constituent getMentionConstituent(String mentionId) {
        return mentionIdToConstituent.get(mentionId);
    }

    /**
     * Generates report of Entities and Mentions read and generated. Note that these may differ:
     * this reader relies on its own tokenization (none is provided in the source corpus) and if
     * token segmentation differs, mentions specified in the source may not be found in the text by
     * this reader.
     * 
     * @return String describing annotations read and generated.
     */
    @Override
    public String generateReport() {
        StringBuilder bldr = new StringBuilder(super.generateReport());
        bldr.append("Number of entities in source: ").append(numEntitiesInSource)
                .append(System.lineSeparator());
        bldr.append("Number of entities generated: ").append(numEntitiesGenerated)
                .append(System.lineSeparator());
//        bldr.append("Number of fillers in source: ").append(numFillersInSource)
//                .append(System.lineSeparator());
//        bldr.append("Number of fillers generated: ").append(numFillersGenerated)
//                .append(System.lineSeparator());
        bldr.append("Number of mentions in source: ").append(numMentionsInSource)
                .append(System.lineSeparator());
        bldr.append("Number of mentions generated: ").append(numMentionsGenerated)
                .append(System.lineSeparator());

        return bldr.toString();
    }
}
