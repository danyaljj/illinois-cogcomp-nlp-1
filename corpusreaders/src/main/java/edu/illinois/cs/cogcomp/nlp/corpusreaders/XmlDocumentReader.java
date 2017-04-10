/**
 * This software is released under the University of Illinois/Research and Academic Use License. See
 * the LICENSE file in the root folder for details. Copyright (c) 2016
 *
 * Developed by: The Cognitive Computation Group University of Illinois at Urbana-Champaign
 * http://cogcomp.cs.illinois.edu/
 */
package edu.illinois.cs.cogcomp.nlp.corpusreaders;

import edu.illinois.cs.cogcomp.annotation.TextAnnotationBuilder;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.XmlTextAnnotation;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.TextCleaner;
import edu.illinois.cs.cogcomp.nlp.tokenizer.StatefulTokenizer;
import edu.illinois.cs.cogcomp.nlp.utility.TokenizerTextAnnotationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates an {@link XmlTextAnnotation} object per file for a corpus consisting of files containing xml
 * fragments. Created for the DEFT ERE collection for Belief and Sentiment task (LDC2016E27). All
 * documents appear to be forum data, not full xml, but xml-ish. LDC README IN LDC2016E27 INDICATES
 * THAT THESE DOCUMENTS ARE XML FRAGMENTS, NOT FULL XML. Therefore, they should be treated as raw
 * text, even though they contain xml-escaped character forms: character offsets for standoff
 * annotation will refer to these expanded forms. This reader generates a cleaned-up, text-only
 * version of the document and also retrieves information from the xml markup. The
 * {@link edu.illinois.cs.cogcomp.core.utilities.StringTransformation} that accompanies it maps the
 * cleaned-up text offsets back to the original xml file. The xml markup offsets correspond to the
 * original xml document.
 *
 * The Xml document structure consists of one or more "post" elements, each possibly containing one or
 * more "quote" elements (which may be nested) and which may have other tags (image files and other url-like
 * stuff, possibly html formatting), though these will generally be escaped. This reader handles
 * these problems.
 *
 * This reader (initial implementation) tries to clean up text as much as possible while preserving
 * character offsets of the original text. This is achieved by whitespacing the xml/other tags; the
 * Illinois Tokenizer should be able to handle this in an offset-preserving way.
 *
 * The TextAnnotations will be returned with TextID fields set to the name of the source file.
 *
 * WARNING! No effort is made to represent the inter-post/quoted segment structure.
 *
 * When trying to align annotations to the original file, beware the following annotation property
 * (explained in the README from the corpus:
 *
 * <quote>Because each CMP document is extracted verbatim from source XML files, certain characters
 * in its content (ampersands, angle brackets, etc.) are escaped according to the XML specification.
 * The offsets of text extents are based on treating this escaped text as-is (e.g. "&amp;" in a
 * cmp.txt file is counted as five characters).
 * 
 * Whenever any such string of "raw" text is included in a .rich_ere.xml file (as the text extent to
 * which an annotation is applied), a second level of escaping has been applied, so that XML parsing
 * of the ERE XML file will produce a string that exactly matches the source text. </quote>
 */
public class XmlDocumentReader extends AbstractIncrementalCorpusReader {
    private static Logger logger = LoggerFactory
            .getLogger(XmlDocumentReader.class);

    protected TextAnnotationBuilder taBuilder;
    protected String fileId;
    protected String newFileText;
    private int numTextAnnotations;
    private int numFiles;

    /**
     * assumes files are all from a single source directory.
     *
     * @param corpusName
     * @param sourceDirectory
     * @throws IOException
     */
    public XmlDocumentReader(String corpusName, String sourceDirectory)
            throws Exception {
        super(CorpusReaderConfigurator.buildResourceManager(corpusName, sourceDirectory));
        taBuilder = new TokenizerTextAnnotationBuilder(new StatefulTokenizer());
        numFiles = 0;
        numTextAnnotations = 0;
    }

    @Override
    public void reset() {
        super.reset();
        numFiles = 0;
        numTextAnnotations = 0;
    }

    /**
     * Exclude any files not possessing this extension.
     * TODO: make this configurable
     * @return the required file extension.
     */
    protected String getRequiredFileExtension() {
        return ".cmp.txt";
    }

    /**
     * generate a list of files comprising the corpus. Each is expected to generate one or more
     * TextAnnotation objects, though the way the iterator is implemented allows for corpus files to
     * generate zero TextAnnotations if you are feeling picky.
     *
     * @return a list of Path objects corresponding to files containing corpus documents to process.
     */
    @Override
    public List<List<Path>> getFileListing() throws IOException {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(getRequiredFileExtension());
            }
        };
        String[] fileList = IOUtils.lsFilesRecursive(super.getSourceDirectory(), filter);
        List<List<Path>> pathList = new ArrayList<>(fileList.length);
        for (String file : fileList)
            pathList.add(Collections.singletonList(Paths.get(file)));

        return pathList;
    }

    /**
     * This method can be overridden to do a more complex parsing.
     * 
     * @param original
     * @return the striped text.
     */
    protected String stripText(String original) {
        return TextCleaner.replaceXmlTags(original);
    }

    /**
     * Given an entry from the corpus file list generated by {@link #getFileListing()} , parse its
     * contents and get zero or more TextAnnotation objects.
     *
     * Base implementation assumes a single Path in each corpusFileListEntry, corresponding to a
     * file with source text plus any needed annotations. It also assumes that the file has no
     * markup.
     *
     * @param corpusFileListEntry corpus file containing content to be processed
     * @return List of TextAnnotation objects extracted from the corpus file
     */
    public List<TextAnnotation> getTextAnnotationsFromFile(List<Path> corpusFileListEntry)
            throws Exception {
        Path sourceTextAndAnnotationFile = corpusFileListEntry.get(0);
        fileId =
                sourceTextAndAnnotationFile.getName(sourceTextAndAnnotationFile.getNameCount() - 1)
                        .toString();
        logger.debug("read source file {}", fileId);
        numFiles++;
        String fileText = LineIO.slurp(sourceTextAndAnnotationFile.toString());
        newFileText = this.stripText(fileText);

        List<TextAnnotation> taList = new ArrayList<>(1);
        TextAnnotation ta = makeTextAnnotation();
        if (null != ta) {
            taList.add(ta);
            numTextAnnotations++;
        }

        return taList;
    }

    /**
     * generate a human-readable report of annotations read from the source file (plus whatever
     * other relevant statistics the user should know about).
     */
    @Override
    public String generateReport() {
        StringBuilder bldr = new StringBuilder();
        bldr.append("Number of files read: ").append(numFiles).append(System.lineSeparator());
        bldr.append("Number of TextAnnotations generated: ").append(numTextAnnotations)
                .append(System.lineSeparator());
        return bldr.toString();
    }

    /**
     * uses fields set by getTextAnnotationsFromFile()
     * 
     * @return
     * @throws Exception
     */
    @Override
    protected TextAnnotation makeTextAnnotation() throws Exception {
        return taBuilder.createTextAnnotation(corpusName, fileId, newFileText.toString());
    }
}
