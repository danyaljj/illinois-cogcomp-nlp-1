package edu.illinois.cs.cogcomp.nlp.pipeline;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.annotation.AnnotatorServiceConfigurator;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.configuration.Configurator;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.nlp.common.PipelineConfigurator;
import edu.illinois.cs.cogcomp.nlp.util.SimpleCachingPipeline;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Tests for SimpleCachingPipeline
 * Created by mssammon on 9/21/15.
 */
public class SimpleCachingPipelineTest
{

    private static SimpleCachingPipeline processor;
    private static HashSet<String> activeViews;

    @BeforeClass
    public static void init() throws IOException, AnnotatorException {
        Properties props = new Properties();
        props.setProperty( PipelineConfigurator.USE_NER_ONTONOTES.key, Configurator.FALSE );
        props.setProperty( PipelineConfigurator.USE_STANFORD_DEP.key, Configurator.TRUE );
        props.setProperty( PipelineConfigurator.USE_STANFORD_PARSE.key, Configurator.TRUE );
        props.setProperty( AnnotatorServiceConfigurator.FORCE_CACHE_UPDATE.key, Configurator.TRUE );

        props.setProperty( AnnotatorServiceConfigurator.CACHE_DIR.key, "simple-annotation-cache" );
        props.setProperty( AnnotatorServiceConfigurator.THROW_EXCEPTION_IF_NOT_CACHED.key, Configurator.FALSE );
        activeViews = new HashSet<>();
        activeViews.add( ViewNames.POS );
        activeViews.add( ViewNames.SHALLOW_PARSE );
        activeViews.add( ViewNames.NER_CONLL );
        processor = new SimpleCachingPipeline( new ResourceManager( props ) );
    }


    @Test
    public void testTokenizedInputToPipeline()
    {
        String[] firstSent = "What does the fox say ?".split( " " );
        String[] secondSent = "Fabio Aiolli says that in Italy the fox says \" Woof \" .".split( " " );

        List< String[] > input = new ArrayList<>( 2 );
        input.add( firstSent );
        input.add( secondSent );

        TextAnnotation ta = null;

        try {
            ta = processor.createBasicTextAnnotation("test", "test", input );
        } catch (AnnotatorException e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        try {
            processor.addViewsAndCache( ta, activeViews );
        } catch (AnnotatorException e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        assert( ta.hasView( ViewNames.NER_CONLL ) );
        assert( ta.getView( ViewNames.NER_CONLL ).getConstituents().size() == 3 );

    }



    @Test
    public void testSimpleCachingPipeline()
    {
        String text = "The only way to limit a dog's creativity is to place a foul-smelling bone under its nose. " +
                "For a cat, substitute a laser pointer for the bone.";

        String corpusId = "test";
        String textId = "testText";

        String fileName = null;
        try {
            fileName = SimpleCachingPipeline.getSavePath(((SimpleCachingPipeline) processor).pathToSaveCachedFiles, text);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        if ( IOUtils.exists( fileName ) ) // from previous run of this test
            try {
                IOUtils.rm( fileName );
            } catch (IOException e) {
                e.printStackTrace();
                fail( e.getMessage() );
            }

        assertTrue( !( new File( fileName ) ).exists() );

        try {
            TextAnnotation annotatedText = processor.createAnnotatedTextAnnotation( "", "", text );

            assertNotNull( annotatedText );
            assertTrue(annotatedText.hasView(ViewNames.POS));
            assertTrue( annotatedText.hasView(ViewNames.SHALLOW_PARSE ) );
            System.out.println( "checking file '" + fileName + "' now exists..." );
            assertTrue(IOUtils.exists(fileName));
        } catch (Exception e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        assertTrue( new File( fileName ).exists() );



        TextAnnotation ta = null;
        try {
            ta = SerializationHelper.deserializeTextAnnotationFromFile(fileName);
        } catch (IOException e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        assertTrue( ta.hasView( ViewNames.NER_CONLL ) );

        try {
            IOUtils.rm( fileName );
        } catch (IOException e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        assertTrue( !( new File( fileName ) ).exists() );

        try {
            TextAnnotation newTa = processor.createAnnotatedTextAnnotation(corpusId, textId, text );
            assertTrue( newTa.hasView( ViewNames.DEPENDENCY_STANFORD ));
        } catch (AnnotatorException e) {
            e.printStackTrace();
        }

        ta = null;

        assertTrue( new File( fileName ).exists() );

        try {
            ta = SerializationHelper.deserializeTextAnnotationFromFile(fileName);
        } catch (IOException e) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

        assertTrue( ta.hasView( ViewNames.NER_CONLL ) );

		// checks that inactive components are not applied...
        assertTrue( ta.hasView( ViewNames.PARSE_STANFORD ) );
        assertTrue( ta.hasView( ViewNames.SRL_VERB ) );
        assertTrue( ta.hasView( ViewNames.SRL_NOM ) );

    }

}