/**
 *  Copyright 2005-2017 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.jboss.fuse.patch.management.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import org.apache.karaf.features.internal.model.processing.FeaturesProcessing;
import org.apache.karaf.features.internal.service.FeaturesProcessingSerializer;

public final class InternalUtils {

    private InternalUtils() { }

    /**
     * Loads existing {@code etc/org.apache.karaf.features.xml}, possibly using {@code etc/versions.properties}
     * @param karafHome location of {@code ${karaf.home}}
     * @return
     */
    public static FeaturesProcessing loadFeatureProcessing(File karafHome) {
        return loadFeatureProcessing(
                new File(karafHome, "etc/org.apache.karaf.features.xml"),
                new File(karafHome, "etc/versions.properties")
        );
    }

    /**
     * Loads processing definition, possibly with additional version properties
     * @param featureProcessing location of feature processing file
     * @param properties properties for placeholder resolver
     * @return
     */
    public static FeaturesProcessing loadFeatureProcessing(File featureProcessing, File properties) {
        try {
            Properties props = new Properties();
            if (properties != null && properties.isFile()) {
                try (FileInputStream fis = new FileInputStream(properties)) {
                    props.load(fis);
                }
            }
            FeaturesProcessingSerializer serializer = new FeaturesProcessingSerializer();
            try (FileInputStream fis = new FileInputStream(featureProcessing)) {
                return serializer.read(fis, props);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Stores Feature processing file
     * @param model
     * @param featureProcessing
     * @param properties
     */
    public static void saveFeatureProcessing(FeaturesProcessing model, File featureProcessing, File properties) {
        try {
            FeaturesProcessingSerializer serializer = new FeaturesProcessingSerializer();
            try (FileOutputStream fos = new FileOutputStream(featureProcessing)) {
                serializer.write(model, fos);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
