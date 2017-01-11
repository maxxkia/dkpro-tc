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
package org.dkpro.tc.groovyexamples.regression.pair;

import org.dkpro.tc.groovyexamples.utils.GroovyDemosTest_Base;
import org.junit.Before;
import org.junit.Test;

/**
 * This test just ensures that the experiment runs without throwing
 * any exception.
 */
public class RegressionDemoTest extends GroovyDemosTest_Base
{
    RegressionDemo groovyExperiment;

    @Before
    public void setup()
        throws Exception
    {
        super.setup();

        groovyExperiment = new RegressionDemo();
    }

    @Test
    public void testGroovyTrainTest()
        throws Exception
    {
        groovyExperiment.runTrainTest();
    }
    
    @Test
    public void testGroovyCrossValidation()
        throws Exception
    {
        groovyExperiment.runCrossValidation();
    }
}
