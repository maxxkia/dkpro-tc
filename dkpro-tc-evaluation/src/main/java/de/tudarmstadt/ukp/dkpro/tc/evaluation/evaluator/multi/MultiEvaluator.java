/*******************************************************************************
 * Copyright 2014
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universit�t Darmstadt
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
package de.tudarmstadt.ukp.dkpro.tc.evaluation.evaluator.multi;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import de.tudarmstadt.ukp.dkpro.tc.evaluation.confusion.matrix.ConfusionMatrix;
import de.tudarmstadt.ukp.dkpro.tc.evaluation.confusion.matrix.MultiConfusionMatrix;
import de.tudarmstadt.ukp.dkpro.tc.evaluation.evaluator.BipartitionBased;
import de.tudarmstadt.ukp.dkpro.tc.evaluation.evaluator.EvaluatorBase;


/**
 * @author Andriy Nadolskyy
 * 
 */
public class MultiEvaluator extends EvaluatorBase implements BipartitionBased {

	public MultiEvaluator(HashMap<String, Integer> class2number,
			LinkedList<String> readData, boolean softEvaluation) {
		super(class2number, readData, softEvaluation);
	}

	public ConfusionMatrix<HashMap<String, HashMap<String, Double>>> buildConfusionMatrix() {
		HashSet<String> labelCombinations = getSetOfLabelCombinations();
		
		// gold - prediction - value
		HashMap<String, HashMap<String, Double>> confusionMatrix = 
				new HashMap<String, HashMap<String, Double>>();
		HashMap<String, Double> tempDimension = new HashMap<String, Double>();
		for (String labelCombination : labelCombinations) {
			tempDimension.put(labelCombination, 0.0);
		}
		for (String labelCombination : labelCombinations) {
			confusionMatrix.put(labelCombination, (HashMap<String, Double>) tempDimension.clone());
		}
		
		for (String line : readData) {
			// consists of: prediction, gold label, threshold
			String[] splittedEvaluationData = line.split(";");
			String predictionValues = splittedEvaluationData[0];
			String gold = splittedEvaluationData[1];
			Double threshold = Double.valueOf(splittedEvaluationData[2]);
			
			String[] predictionValue = predictionValues.split(","); 
			String accumulatedLabelCombination = "";
			for (int i = 0; i < predictionValue.length; i++) {
				if (Double.valueOf(predictionValue[i]) >= threshold){
					if(! accumulatedLabelCombination.equals(""))
						accumulatedLabelCombination += ",";
					accumulatedLabelCombination += i; 
				}
			}
			
			// TODO: Fix it
			if (! accumulatedLabelCombination.equals("")){
				Double updatedValue = confusionMatrix.get(gold).get(accumulatedLabelCombination) + 1;
				confusionMatrix.get(gold).put(accumulatedLabelCombination, updatedValue);
			}
		}
		return new MultiConfusionMatrix(confusionMatrix, class2number);
	}

	/**
	 * get set of relevant label combinations: get it from the list of predictions and gold labels
	 * regarding to threshold
	 * 
	 * @return set of label combinations
	 * @throws IOException
	 */
	public HashSet<String> getSetOfLabelCombinations(){
		HashSet<String> labelCombinations = new HashSet<String>();
		for (String line : readData) {
			// consists of: prediction, gold label, threshold
			String[] splittedEvaluationData = line.split(";");
			String predictionValues = splittedEvaluationData[0];
			String gold = splittedEvaluationData[1];
			Double threshold = Double.valueOf(splittedEvaluationData[2]);
			
			String[] predictionValue = predictionValues.split(","); 
			String accumulatedLabelCombination = "";
			for (int i = 0; i < predictionValue.length; i++) {
				if (Double.valueOf(predictionValue[i]) >= threshold){
					if(! accumulatedLabelCombination.equals(""))
						accumulatedLabelCombination += ",";
					accumulatedLabelCombination += i; 
				}
			}
			
			//TODO: Fix it
			if (! accumulatedLabelCombination.equals("")){
				labelCombinations.add(accumulatedLabelCombination);
				labelCombinations.add(gold);
			}
		}
		return labelCombinations;
	}	
	
	@Override
	public HashMap<String, String> calculateEvaluationMeasures() {
		MultiConfusionMatrix confMatr = (MultiConfusionMatrix) buildConfusionMatrix();
		double[][][] decomposedConfusionMatrix = confMatr.decomposeConfusionMatrix();
		
		HashMap<String, String> results = 
				calculateLabelBasedEvaluationMeasures(decomposedConfusionMatrix);		
		return results;
	}

}