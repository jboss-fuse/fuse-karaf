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
package org.jboss.fuse.patch.management;

import java.io.File;

import org.apache.karaf.features.internal.model.processing.FeaturesProcessing;
import org.apache.karaf.features.internal.service.FeaturesProcessingSerializer;
import org.jboss.fuse.patch.management.impl.InternalUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureProcessingTest {

    @Test
    public void readProcessingWithPlaceholders() {
        FeaturesProcessing fp1 = InternalUtils.loadFeatureProcessing(new File("src/test/resources/processing/oakf.1.xml"),
                null);
        FeaturesProcessing fp2 = InternalUtils.loadFeatureProcessing(new File("src/test/resources/processing/oakf.1.xml"),
                new File("src/test/resources/processing/oakf.1.properties"));

        assertThat(fp1.getBundleReplacements().getOverrideBundles().get(1).getReplacement())
                .isEqualTo("mvn:org.jboss.fuse.test/test-2/${version.test2}");
        assertThat(fp2.getBundleReplacements().getOverrideBundles().get(1).getReplacement())
                .isEqualTo("mvn:org.jboss.fuse.test/test-2/1.3");
    }

    @Test
    public void writeProcessingInstructions() {
        FeaturesProcessing fp1 = InternalUtils.loadFeatureProcessing(new File("src/test/resources/processing/oakf.1.xml"),
                null);

        fp1.getBlacklistedBundles().add("mvn:x/y");
        fp1.getBlacklistedFeatures().add(new FeaturesProcessing.BlacklistedFeature("f", "1"));

        FeaturesProcessingSerializer serializer = new FeaturesProcessingSerializer();
        serializer.write(fp1, System.out);
        System.out.flush();
    }

}
