/**
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package org.dkpro.tc.ml.weka.writer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.dkpro.tc.api.exception.TextClassificationException;
import org.dkpro.tc.api.features.Feature;
import org.dkpro.tc.api.features.Instance;
import org.dkpro.tc.api.features.MissingValue;
import org.dkpro.tc.core.Constants;
import org.dkpro.tc.core.io.DataWriter;
import org.dkpro.tc.core.ml.TcShallowLearningAdapter.AdapterNameEntries;
import org.dkpro.tc.core.task.uima.FeatureType;
import org.dkpro.tc.ml.weka.WekaClassificationAdapter;
import org.dkpro.tc.ml.weka.util.AttributeStore;
import org.dkpro.tc.ml.weka.util.WekaUtils;

import com.google.gson.Gson;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.core.converters.ArffSaver;
import weka.core.converters.Saver;

/*
 * Datawriter for the Weka machine learning tool.
 */
public class WekaDataWriter
    implements DataWriter, Constants
{
    BufferedWriter bw = null;
    Gson gson = new Gson();
    private boolean useSparse;
    private String learningMode;
    private boolean applyWeighting;
    private File outputFolder;
    private File arffTarget;
    boolean isRegression;

    AttributeStore attributeStore;
    Attribute outcomeAttribute;
    ArffSaver saver;
    Instances masterInstance;
    private String[] outcomes;

    @Override
    public void init(File outputFolder, boolean useSparse, String learningMode,
            boolean applyWeighting, String [] outcomes)
                throws Exception
    {
        this.outputFolder = outputFolder;
        this.useSparse = useSparse;
        this.learningMode = learningMode;
        this.applyWeighting = applyWeighting;
        this.outcomes = outcomes;

        arffTarget = new File(outputFolder, WekaClassificationAdapter.getInstance()
                .getFrameworkFilename(AdapterNameEntries.featureVectorsFile));

        // Caution: DKPro Lab imports (aka copies!) the data of the train task
        // as test task. We use
        // appending mode for streaming. We might errornously append the old
        // training file with
        // testing data!
        // Force delete the old training file to make sure we start with a
        // clean, empty file
        if (arffTarget.exists()) {
            FileUtils.forceDelete(arffTarget);
        }

        isRegression = learningMode.equals(LM_REGRESSION);

        if (!arffTarget.exists()) {
            arffTarget.mkdirs();
            arffTarget.createNewFile();
        }

    }

    @Override
    public void writeGenericFormat(Collection<Instance> instances)
        throws Exception
    {
        initGeneric();

        bw.write(gson.toJson(instances.toArray(new Instance[0])) + System.lineSeparator());

        bw.close();
        bw = null;
    }

    private void initGeneric()
        throws IOException
    {
        if (bw != null) {
            return;
        }
        bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(new File(outputFolder, GENERIC_FEATURE_FILE), true), "utf-8"));
    }

    @Override
    public void transformFromGeneric()
        throws Exception
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(outputFolder, GENERIC_FEATURE_FILE)), "utf-8"));

        String line = null;
        while ((line = reader.readLine()) != null) {
            Instance[] restoredInstances = gson.fromJson(line, Instance[].class);
            writeClassifierFormat(Arrays.asList(restoredInstances), classiferReadsCompressed());
        }

        reader.close();
        FileUtils.deleteQuietly(new File(outputFolder, GENERIC_FEATURE_FILE));
    }

    private Attribute createOutcomeAttribute(List<String> outcomeValues, boolean isRegresion)
    {
        if (isRegresion) {
            return new Attribute(CLASS_ATTRIBUTE_NAME);
        }
        else {
            // make the order of the attributes predictable
            Collections.sort(outcomeValues);
            return new Attribute(CLASS_ATTRIBUTE_NAME, outcomeValues);
        }
    }

    private double[] getFeatureValues(AttributeStore attributeStore, Instance instance)
    {
        double[] featureValues = new double[attributeStore.getAttributes().size()];

        for (Feature feature : instance.getFeatures()) {
            
            try {
                Attribute attribute = attributeStore.getAttribute(feature.getName());
                Object featureValue = feature.getValue();

                double attributeValue;
                if (featureValue instanceof Number) {
                    // numeric attribute
                    attributeValue = ((Number) feature.getValue()).doubleValue();
                }
                else if (featureValue instanceof Boolean) {
                    // boolean attribute
                    attributeValue = (Boolean) featureValue ? 1.0d : 0.0d;
                }
                else if (featureValue instanceof MissingValue) {
                    // missing value
                    attributeValue = WekaFeatureEncoder.getMissingValueConversionMap()
                            .get(((MissingValue) featureValue).getType());
                }
                else if (featureValue == null) {
                    // null
                    throw new IllegalArgumentException(
                            "You have an instance which doesn't specify a value for the feature "
                                    + feature.getName());
                }
                else {
                    // nominal or string
                    Object stringValue = feature.getValue();
                    if (!attribute.isNominal() && !attribute.isString()) {
                        throw new IllegalArgumentException(
                                "Attribute neither nominal nor string: " + stringValue);
                    }

                    int valIndex = attribute.indexOfValue(stringValue.toString());
                    if (valIndex == -1) {
                        if (attribute.isNominal()) {
                            throw new IllegalArgumentException(
                                    "Value not defined for given nominal attribute!");
                        }
                        else {
                            attribute.addStringValue(stringValue.toString());
                            valIndex = attribute.indexOfValue(stringValue.toString());
                        }
                    }
                    attributeValue = valIndex;
                }
                int offset = attributeStore.getAttributeOffset(attribute.name());

                if (offset != -1) {
                    featureValues[offset] = attributeValue;
                }
            }
            catch (NullPointerException e) {
                // ignore unseen attributes
            }
        }
        return featureValues;
    }

    @Override
    public void writeClassifierFormat(Collection<Instance> instances, boolean compress)
        throws Exception
    {

        Instances masterInstance = initalConfiguration(instances);

        // Instances wekaInstances = new Instances(WekaUtils.RELATION_NAME,
        // attributeStore.getAttributes(), instances.size());
        // wekaInstances.setClass(outcomeAttribute);

        for (Instance inst : instances) {
            double[] featureValues = getFeatureValues(attributeStore, inst);

            weka.core.Instance wekaInstance;

            if (useSparse) {
                wekaInstance = new SparseInstance(1.0, featureValues);
            }
            else {
                wekaInstance = new DenseInstance(1.0, featureValues);
            }

            wekaInstance.setDataset(masterInstance);

            String outcome = inst.getOutcome();
            if (isRegression) {
                wekaInstance.setClassValue(Double.parseDouble(outcome));
            }
            else {
                wekaInstance.setClassValue(outcome);
            }

            Double instanceWeight = inst.getWeight();
            if (applyWeighting) {
                wekaInstance.setWeight(instanceWeight);
            }

            // preprocessingFilter.input(wekaInstance);
            // saver.writeIncremental(preprocessingFilter.output());
            saver.writeIncremental(wekaInstance);
        }
    }

    private Instances initalConfiguration(Collection<Instance> instances)
        throws TextClassificationException, IOException
    {
        if (saver != null) {
            return masterInstance;
        }
        saver = new ArffSaver();
        saver.setRetrieval(Saver.INCREMENTAL);
        saver.setFile(arffTarget);
        saver.setCompressOutput(true);

        attributeStore = new AttributeStore();

        List<String> lines = FileUtils.readLines(
                new File(outputFolder, Constants.FILENAME_FEATURES_DESCRIPTION), "utf-8");

        for (String l : lines) {
            String[] split = l.split("\t");
            String featureName = split[0];

            if (!attributeStore.containsAttributeName(featureName)) {
                FeatureType type = FeatureType.valueOf(split[1]);
                String enumType = null;
                if (type == FeatureType.ENUM) {
                    enumType = split[2];
                }
                Attribute attribute = WekaFeatureEncoder
                        .featureToAttributeUsingFeatureDescription(featureName, type, enumType);
                attributeStore.addAttribute(featureName, attribute);
            }

        }

        // Make sure "outcome" is not the name of an attribute
         List<String> outcomeList = Arrays.asList(outcomes);
        // FIXME: Das muss wieder raus später
        outcomeAttribute = createOutcomeAttribute(outcomeList, isRegression);
        if (attributeStore.containsAttributeName(CLASS_ATTRIBUTE_NAME)) {
            System.err.println(
                    "A feature with name \"outcome\" was found. Renaming outcome attribute");
            outcomeAttribute = outcomeAttribute.copy(CLASS_ATTRIBUTE_PREFIX + CLASS_ATTRIBUTE_NAME);
        }
        attributeStore.addAttribute(outcomeAttribute.name(), outcomeAttribute);

        masterInstance = new Instances(WekaUtils.RELATION_NAME, attributeStore.getAttributes(),
                instances.size());
        masterInstance.setClass(outcomeAttribute);
        // FIXME: darf man vermutlich auch nur einmal machen
        saver.setInstances(masterInstance);

        return masterInstance;

    }

    @Override
    public boolean canStream()
    {
        return true;
    }

    @Override
    public boolean classiferReadsCompressed()
    {
        return true;
    }

    @Override
    public String getGenericFileName()
    {
        return GENERIC_FEATURE_FILE;
    }

    @Override
    public void close()
        throws Exception
    {
        saver.writeIncremental(null);
    }

}
