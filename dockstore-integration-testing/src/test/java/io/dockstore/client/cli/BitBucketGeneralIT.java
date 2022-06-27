/*
 *    Copyright 2022 OICR and UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Tag;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

@Category(BitBucketTest.class)
public class BitBucketGeneralIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    private FileDAO fileDAO;
    private Session session;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false);
    }

    @Test
    public void testGrabChecksumFromDockerHub() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = createManualDockerHubTool();

        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Docstore.cwl");
        tool = toolApi.registerManual(tool);

        tool = addDockerHubTag(tool, toolTagsApi, toolApi);
        List<Tag> tags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(tags);

        // Check for case where user deletes tag and creates new one of same name.
        // Check that the new imageid and checksums are grabbed on refresh. Also check the old images have been deleted.
        refreshAfterDeletedTag(toolApi, tool, tags);
        testingPostgres.runUpdateStatement("update tool set name = 'thisnamedoesnotexist' where giturl = 'git@bitbucket.org:dockstoretestuser2/dockstore-whalesay-2.git'");
        toolApi.refresh(tool.getId());
        List<Tag> updatedTags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(updatedTags);
    }


    private DockstoreTool createManualDockerHubTool() {
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("dockstore-whalesay-2");
        tool.setNamespace("dockstoretestuser");
        tool.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setDefaultWdlPath("/Dockstore.wdl");
        tool.setDefaultCWLTestParameterFile("/test.cwl.json");
        tool.setDefaultWDLTestParameterFile("/test.wdl.json");
        tool.setIsPublished(false);
        // This actually exists: https://bitbucket.org/DockstoreTestUser/dockstore-whalesay-2/src/master/
        tool.setGitUrl("git@bitbucket.org:DockstoreTestUser/dockstore-whalesay-2.git");
        tool.setToolname("alternate");
        tool.setPrivateAccess(false);
        return tool;
    }


    @Test
    public void testGrabChecksumFromGitHubContainerRegistry() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = registerManualGitHubContainerRegistryToolAndAddTag();
        List<Tag> tags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(tags);

        // Check for case where user deletes tag and creates new one of same name.
        // Check that the new imageid and checksums are grabbed on refresh. Also check the old images have been deleted.
        refreshAfterDeletedTag(toolApi, tool, tags);

        // mimic getting a registry being slow/not responding and verify we do not delete the image information we already have by going to an invalid url.
        testingPostgres.runUpdateStatement("update tool set name = 'thisnamedoesnotexist' where id=" + tool.getId());
        toolApi.refresh(tool.getId());
        List<Tag> updatedTags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(updatedTags);
    }

    private DockstoreTool registerManualGitHubContainerRegistryToolAndAddTag() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);

        // This image is used for the tool: https://ghcr.io/homebrew/core/python/3.9:3.9.6
        tool.setRegistryString(Registry.GITHUB_CONTAINER_REGISTRY.getDockerPath());
        tool.setNamespace("homebrew");
        tool.setName("core/python/3.9");
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setDefaultWdlPath("/Dockstore.wdl");
        tool.setDefaultCWLTestParameterFile("/test.cwl.json");
        tool.setDefaultWDLTestParameterFile("/test.wdl.json");
        tool.setIsPublished(false);
        // This actually exists: https://bitbucket.org/DockstoreTestUser/dockstore-whalesay-2/src/master/
        tool.setGitUrl("git@bitbucket.org:DockstoreTestUser/dockstore-whalesay-2.git");
        tool.setPrivateAccess(false);

        tool = toolApi.registerManual(tool);

        // Add the 3.9.6 tag for the ghcr.io/homebrew/core/python/3.9 tool
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("3.9.6");
        tag.setReference("master");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        return tool;
    }


    private DockstoreTool registerManualAmazonECRToolAndAddTag() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);

        // This image is used for the tool: public.ecr.aws/ubuntu/ubuntu:18.04
        tool.setRegistryString(Registry.AMAZON_ECR.getDockerPath());
        tool.setNamespace("ubuntu");
        tool.setName("ubuntu");
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setDefaultWdlPath("/Dockstore.wdl");
        tool.setDefaultCWLTestParameterFile("/test.cwl.json");
        tool.setDefaultWDLTestParameterFile("/test.wdl.json");
        tool.setIsPublished(false);
        // This actually exists: https://bitbucket.org/DockstoreTestUser/dockstore-whalesay-2/src/master/
        tool.setGitUrl("git@bitbucket.org:DockstoreTestUser/dockstore-whalesay-2.git");
        tool.setPrivateAccess(false);

        tool = toolApi.registerManual(tool);

        // Add the 18.04 tag for the public.ecr.aws/ubuntu/ubuntu tool
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("18.04");
        tag.setReference("master");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        return tool;
    }

    @Test
    public void testGrabChecksumFromAmazonECR() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = registerManualAmazonECRToolAndAddTag();
        List<Tag> tags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(tags);

        // Check for case where user deletes tag and creates new one of same name.
        // Check that the new imageid and checksums are grabbed on refresh. Also check the old images have been deleted.
        refreshAfterDeletedTag(toolApi, tool, tags);

        // mimic getting a registry being slow/not responding and verify we do not delete the image information we already have by going to an invalid url.
        testingPostgres.runUpdateStatement("update tool set name = 'thisnamedoesnotexist' where id=" + tool.getId());
        toolApi.refresh(tool.getId());
        List<Tag> updatedTags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(updatedTags);
    }

    private DockstoreTool addDockerHubTag(DockstoreTool tool, ContainertagsApi toolTagsApi, ContainersApi toolApi) {
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("latest");
        tag.setReference("master");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        return tool;
    }

}
