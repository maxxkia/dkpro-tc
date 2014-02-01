package de.tudarmstadt.ukp.dkpro.tc.features.ngram.meta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.tc.api.features.meta.MetaCollector;
import de.tudarmstadt.ukp.dkpro.tc.features.ngram.LuceneFeatureExtractorBase;
import de.tudarmstadt.ukp.dkpro.tc.features.ngram.LuceneNGramFeatureExtractor;

public abstract class LuceneBasedMetaCollector
    extends MetaCollector
{
    public final static String LUCENE_DIR = "lucence";
    
    public static final String LUCENE_ID_FIELD = "id";

    @ConfigurationParameter(name = LuceneFeatureExtractorBase.PARAM_LUCENE_DIR, mandatory = true)
    private File luceneDir;

    protected IndexWriter indexWriter;
    
    private String currentDocumentId;
    private Document currentDocument;
    
    private FieldType fieldType;
    
    @Override
    public void initialize(UimaContext context)
        throws ResourceInitializationException
    {
        super.initialize(context);

        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_44, null);
        
        try {
            indexWriter = new IndexWriter(FSDirectory.open(luceneDir), config);
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
        
        currentDocumentId = null;
        currentDocument = null;
        
        fieldType = new FieldType();
        fieldType.setIndexed(true);
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        fieldType.setStored(true);
        fieldType.setOmitNorms(true);
        fieldType.setTokenized(false);
        fieldType.freeze();
    }
    
    protected void addField(JCas jcas, String fieldName, String value) {
        if (currentDocument == null || !currentDocumentId.equals(getDocumentId(jcas))) {
            currentDocumentId = getDocumentId(jcas);
            currentDocument = new Document();
            currentDocument.add(new StringField(
                    LUCENE_ID_FIELD,
                    currentDocumentId,
                    Field.Store.YES
            ));
        }

        Field field = new Field(
                fieldName,
                value,
                fieldType
        );
        currentDocument.add(field);
    }
    
    protected void writeToIndex()
        throws IOException
    {
        indexWriter.addDocument(currentDocument);
    }
    
    @Override
    public void collectionProcessComplete()
        throws AnalysisEngineProcessException
    {
        super.collectionProcessComplete();

        try {
            indexWriter.commit();
            indexWriter.close();
        } catch (CorruptIndexException e) {
            throw new AnalysisEngineProcessException(e);
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }
    
    @Override
    public Map<String, String> getParameterKeyPairs()
    {
        Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(LuceneNGramFeatureExtractor.PARAM_LUCENE_DIR, LUCENE_DIR);
        return mapping;
    }
    
    protected String getDocumentId(JCas jcas)
    {
        return DocumentMetaData.get(jcas).getDocumentTitle();
    }
}