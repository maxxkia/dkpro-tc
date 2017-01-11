/*******************************************************************************
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.dkpro.tc.features.pair.similarity;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.tc.api.features.FeatureStore;
import org.dkpro.tc.api.features.Instance;
import org.dkpro.tc.core.Constants;
import org.dkpro.tc.core.io.JsonDataWriter;
import org.dkpro.tc.core.task.uima.DocumentModeAnnotator;
import org.dkpro.tc.fstore.simple.DenseFeatureStore;
import org.dkpro.tc.testing.TestPairReader;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import com.google.gson.Gson;

import de.tudarmstadt.ukp.dkpro.core.morpha.MorphaLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.snowball.SnowballStemmer;
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;


public abstract class PPipelineTestBase
{
    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();
    
	protected List<Instance> instanceList;
	protected List<List<String>> outcomeList;
	protected TreeSet<String> featureNames;
	
    protected File lucenePath;
    protected File outputPath;
    protected Object[] parameters;
    protected AnalysisEngineDescription metaCollector;
    protected AnalysisEngineDescription featExtractorConnector;
    
    protected void initialize() throws Exception{
        lucenePath = folder.newFolder();
        outputPath = folder.newFolder();
        
        instanceList = new ArrayList<Instance>();
        outcomeList = new ArrayList<List<String>>();
    }
    protected String setTestPairsLocation(){
    	return "src/test/resources/data/textpairs.txt";
    }

    protected void runPipeline()
            throws Exception
    {
        List<Object> parameterList = new ArrayList<Object>(Arrays.asList(parameters));
               
        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                TestPairReader.class, 
                TestPairReader.PARAM_INPUT_FILE, setTestPairsLocation()
        );
        
        AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(BreakIteratorSegmenter.class);
        AnalysisEngineDescription stemmer = AnalysisEngineFactory.createEngineDescription(SnowballStemmer.class);
        AnalysisEngineDescription lemmatizer = AnalysisEngineFactory.createEngineDescription(MorphaLemmatizer.class);
        AnalysisEngineDescription posTagger = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
        AnalysisEngineDescription pairAnno = AnalysisEngineFactory.createEngineDescription(DocumentModeAnnotator.class, DocumentModeAnnotator.PARAM_FEATURE_MODE, Constants.FM_PAIR);

        AggregateBuilder builder = new AggregateBuilder();
        builder.add(segmenter, Constants.INITIAL_VIEW, Constants.PART_ONE);
        builder.add(segmenter, Constants.INITIAL_VIEW, Constants.PART_TWO);
        builder.add(pairAnno, Constants.INITIAL_VIEW, Constants.PART_ONE);
        builder.add(pairAnno, Constants.INITIAL_VIEW, Constants.PART_TWO);        
        builder.add(stemmer, Constants.INITIAL_VIEW, Constants.PART_ONE);
        builder.add(stemmer, Constants.INITIAL_VIEW, Constants.PART_TWO);
        builder.add(lemmatizer, Constants.INITIAL_VIEW, Constants.PART_ONE);
        builder.add(lemmatizer, Constants.INITIAL_VIEW, Constants.PART_TWO);
        builder.add(posTagger, Constants.INITIAL_VIEW, Constants.PART_ONE);
        builder.add(posTagger, Constants.INITIAL_VIEW, Constants.PART_TWO);

        getMetaCollector(parameterList);

        getFeatureExtractorCollector(parameterList);

        // run meta collector
        SimplePipeline.runPipeline(reader, builder.createAggregateDescription(), metaCollector);

        // run FE(s)
        SimplePipeline.runPipeline(reader, builder.createAggregateDescription(), featExtractorConnector);

        Gson gson = new Gson();
        FeatureStore fs = gson.fromJson(FileUtils.readFileToString(new File(outputPath, JsonDataWriter.JSON_FILE_NAME)), DenseFeatureStore.class);
        assertEquals(1, fs.getNumberOfInstances());
        assertEquals(1, fs.getUniqueOutcomes().size());
        
        featureNames = fs.getFeatureNames();

        for (int i=0; i<fs.getNumberOfInstances(); i++) {
            instanceList.add(fs.getInstance(i));
            outcomeList.add(fs.getOutcomes(i));
        }
    }
	protected abstract void getFeatureExtractorCollector(List<Object> parameterList)
		throws ResourceInitializationException;

	protected abstract void getMetaCollector(List<Object> parameterList)
		throws ResourceInitializationException;
}