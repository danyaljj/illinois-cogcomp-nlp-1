package edu.illinois.cs.cogcomp.nlp.common;

public class PipelineVars
{

    public static final String USE_POS = "usePos";
    public static final String USE_CHUNKER = "useChunker";
    public static final String USE_STANFORD_PARSE = "useStanfordParse";
//    public static final String USE_TOKENS = "useTokens";
    
 // i.e. if true, use whitespace from raw text to identify token boundaries
    public static final String RESPECT_TOKENIZATION = "respectTokenization";
    public static final String USE_LEMMA = "useLemmatizer";
	public static final String USE_NER = "useNer"; 
	public static final String NER_CONFIG = "nerConfigFile";
    
}
