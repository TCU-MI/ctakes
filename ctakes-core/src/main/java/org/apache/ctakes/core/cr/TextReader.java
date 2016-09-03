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
package org.apache.ctakes.core.cr;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.collection.CasInitializer;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.xml.sax.SAXException;

/**
 * UIMA CollectionReader that reads in CASes from XMI files.
 */
public class TextReader extends JCasCollectionReader_ImplBase {

  public static final String PARAM_FILES = "files";

  @ConfigurationParameter(
      name = PARAM_FILES,
      mandatory = true,
      description = "The text files to be loaded")
  private List<File> files;

  private Iterator<File> filesIter;

  private int completed;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    this.filesIter = files.iterator();
    this.completed = 0;
  }

  @Override
  public Progress[] getProgress() {
    return new Progress[] { new ProgressImpl(this.completed, this.files.size(), Progress.ENTITIES) };
  }

  @Override
  public boolean hasNext() throws IOException, CollectionException {
    return this.filesIter.hasNext();
  }

  @Override
  public void getNext(JCas jCas) throws IOException, CollectionException {
    File currentFile = this.filesIter.next();
    String filename = currentFile.getName();
    FileInputStream fileInputStream = new FileInputStream(currentFile);
    InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
    
    CasInitializer casInitializer = getCasInitializer();

    if (casInitializer != null)
    {
      casInitializer.initializeCas(bufferedReader, jCas.getCas());  
    }
    else  //No CAS Initializer, so read file and set document text ourselves
    {       
      try
      {
        byte[] contents = new byte[(int)currentFile.length() ];
        fileInputStream.read( contents );   
        String text;
        text = new String(contents); 
        //put document in CAS (assume CAS)
        jCas.setDocumentText(text);
      }
      finally
      {
        if (fileInputStream != null)
          fileInputStream.close();
      }  
        
    }

    DocumentID documentIDAnnotation = new DocumentID(jCas);
    documentIDAnnotation.setDocumentID(filename);
    documentIDAnnotation.addToIndexes();

    
    this.completed += 1;
  }

  
}