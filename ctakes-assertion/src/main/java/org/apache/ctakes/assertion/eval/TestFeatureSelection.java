/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.assertion.eval;

import java.io.File;

import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.transform.InstanceDataWriter;
import org.cleartk.ml.feature.transform.InstanceStream;

public class TestFeatureSelection {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		File directory = new File("/Users/m081914/work/sharpattr/ctakes/ctakes-assertion-res/resources/model/sharptrain-xval/fold_0/polarity");
		
		InstanceDataWriter.INSTANCES_OUTPUT_FILENAME = "training-data.liblinear";
		// Extracting features and writing instances
		Iterable<Instance<String>> instances = InstanceStream.loadFromDirectory(directory);
		
//		FeatureSelection<String> featureSelection; 
//		featureSelection = PolarityCleartkAnalysisEngine.createFeatureSelection(1f);
//		featureSelection.train(instances);
//		featureSelection.save(PolarityCleartkAnalysisEngine.createFeatureSelectionURI(directory));

	}

}
