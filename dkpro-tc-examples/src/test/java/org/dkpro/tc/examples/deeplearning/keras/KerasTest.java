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
package org.dkpro.tc.examples.deeplearning.keras;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.util.FileUtil;
import org.dkpro.tc.examples.single.sequence.LabFolderTrackerReport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;

public class KerasTest
{
    private static final String PREDICTION_FILE = "/root/prediction.txt";

    DockerClient docker;
    ContainerConfig containerConfig;
    ContainerCreation creation;
    String id;

    @Before
    public void setup()
        throws Exception
    {

        // try {
        // DeepLearningKerasSeq2SeqTrainTest.main(null);
        // }
        // catch (Exception e) {
        // // Ignore silently, this will crash for sure on systems where Keras is not installed at
        // // the expected location
        // }

        docker = DefaultDockerClient.fromEnv().build();

        containerConfig = ContainerConfig.builder().image("dkprotc").attachStdout(Boolean.TRUE)
                .attachStderr(Boolean.TRUE).attachStdin(Boolean.TRUE).tty(true).user("root")
                .build();

        creation = docker.createContainer(containerConfig);
        id = creation.id();

    }

    @After
    public void cleanUp()
        throws Exception
    {
        // Kill container
        docker.killContainer(id);

        // Remove container
        // docker.removeContainer(id);

        // Close the docker client
        docker.close();
    }

    @Test
    public void runKerasTrainTest()
        throws Exception
    {
        // verifyOutputFolders();

        LabFolderTrackerReport.vectorizationTaskTrain = new File(
                "/Users/toobee/Documents/Eclipse/dkpro-tc/dkpro-tc-examples/target/results/DeepLearningKerasSeq2SeqTrainTest/org.dkpro.lab/repository/VectorizationTask-Train-KerasSeq2Seq-20170607085853128")
                        .getAbsolutePath();
        LabFolderTrackerReport.vectorizationTaskTest = new File(
                "/Users/toobee/Documents/Eclipse/dkpro-tc/dkpro-tc-examples/target/results/DeepLearningKerasSeq2SeqTrainTest/org.dkpro.lab/repository/VectorizationTask-Test-KerasSeq2Seq-20170607085853679")
                        .getAbsolutePath();
        LabFolderTrackerReport.preparationTask = new File(
                "/Users/toobee/Documents/Eclipse/dkpro-tc/dkpro-tc-examples/target/results/DeepLearningKerasSeq2SeqTrainTest/org.dkpro.lab/repository/PreparationTask-KerasSeq2Seq-20170607085852347")
                        .getAbsolutePath();

        
        prepareDockerExperimentExecution();
        System.err.println("Experiment prepared");

        runCode();
        System.err.println("Experiment executed");
        
        sanityCheckPredictionFile(retrievePredictions());
        System.err.println("Experiment results validated");
    }

    private void prepareDockerExperimentExecution() throws Exception
    {
        createFolderInContainer();
        copyFiles(LabFolderTrackerReport.vectorizationTaskTrain, "/root/train");
        copyFiles(LabFolderTrackerReport.vectorizationTaskTest, "/root/test");
        copyCode("src/main/resources/kerasCode/seq/", "/root");
    }

    private void sanityCheckPredictionFile(File f)
        throws IOException
    {
        List<String> lines = FileUtils.readLines(f, "utf-8");
        assertEquals(51, lines.size());
        assertTrue(lines.get(0).startsWith("#"));

        for (int i = 1; i < lines.size() - 1; i++) {
            // validate tab-separated format
            if (i == 25) {
                assertTrue(lines.get(i).isEmpty());
                continue;
            }
            assertEquals(2, lines.get(i).split("\t").length);
        }
    }

    private File retrievePredictions()
        throws Exception
    {
        File f = File.createTempFile("predictionRetrieved", ".txt");
        TarArchiveInputStream tarStream = new TarArchiveInputStream(
                docker.archiveContainer(id, PREDICTION_FILE));

        tarStream.getNextEntry();
            OutputStream outputFileStream = new FileOutputStream(f);
            IOUtils.copy(tarStream, outputFileStream);
            outputFileStream.close();
        tarStream.close();

        return f;
    }

    private void runCode()
        throws Exception
    {
        String[] command = { "bash", "-c",
                "python3 /root/posTaggingLstm.py /root/train/instanceVectors.txt /root/train/outcomeVectors.txt /root/test/instanceVectors.txt /root/test/outcomeVectors.txt '' 75 "
                        + PREDICTION_FILE };

        ExecCreation execCreation = docker.execCreate(id, command,
                DockerClient.ExecCreateParam.attachStdout(),
                DockerClient.ExecCreateParam.attachStderr());
        LogStream output = docker.execStart(execCreation.id());
        String readFully = output.readFully();
        System.out.println("[" + readFully + "]");

    }

    private void copyCode(String source, String target)
        throws Exception
    {
        docker.copyToContainer(new File(source).toPath(), id, target);
    }

    private void createFolderInContainer()
        throws Exception
    {
        final ContainerConfig containerConfig = ContainerConfig.builder().image("dkprotc")
                .user("root").attachStdout(Boolean.TRUE).attachStderr(Boolean.TRUE)
                .attachStdin(Boolean.TRUE).tty(true).build();

        final ContainerCreation creation = docker.createContainer(containerConfig);
        id = creation.id();

        docker.startContainer(id);

        mkdir(docker, id, "/root/train");
        mkdir(docker, id, "/root/test");
    }

    private void mkdir(DockerClient docker, String id, String folder)
        throws Exception
    {
        String[] command = { "bash", "-c", "mkdir " + folder };
        ExecCreation execCreation = docker.execCreate(id, command,
                DockerClient.ExecCreateParam.attachStdout(),
                DockerClient.ExecCreateParam.attachStderr());
        LogStream output = docker.execStart(execCreation.id());
        output.readFully();
    }

    private void copyFiles(String folder, String out)
        throws Exception
    {
        docker.copyToContainer(new File(folder + "/output/").toPath(), id, out);
    }

    private void verifyOutputFolders()
    {
        assertTrue(new File(LabFolderTrackerReport.vectorizationTaskTrain).exists());
        assertTrue(new File(LabFolderTrackerReport.vectorizationTaskTest).exists());
        assertTrue(new File(LabFolderTrackerReport.preparationTask).exists());
    }
}
