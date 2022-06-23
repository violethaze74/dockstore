/*
 *    Copyright 2018 OICR
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

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.openapi.api.impl.ToolsApiServiceImpl.DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.ImageData;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.AliasesApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ParsedInformation;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.Workflow.DescriptorTypeEnum;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.ws.rs.core.GenericType;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 *
 * @author dyuen
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class WorkflowIT extends BaseIT {
    public static final String DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME = "DockstoreTestUser2/hello-dockstore-workflow";
    public static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/" + DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
    public static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW =
        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore-whalesay-imports";
    private static final String DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/gdc-dnaseq-cwl";
    // workflow with external library in lib directory
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/rnatoy";
    // workflow that uses containers
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/galaxy-workflows";
    // workflow with includeConfig in config file directory
    private static final String DOCKSTORE_TEST_USER2_INCLUDECONFIG_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/vipr";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL =
        Registry.QUAY_IO.getDockerPath() + "/dockstoretestuser2/dockstore-cgpmap";
    private static final String DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/workflow-seq-import";
    private static final String GATK_SV_TAG = "dockstore-test";
    private static final String DOCKER_IMAGE_SHA_TYPE_FOR_TRS = "sha-256";
    private final String installationId = "1179416";
    private final String toolAndWorkflowRepo = "DockstoreTestUser2/test-workflows-and-tools";
    private final String toolAndWorkflowRepoToolPath = "DockstoreTestUser2/test-workflows-and-tools/md5sum";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();


    private WorkflowDAO workflowDAO;
    private WorkflowVersionDAO workflowVersionDAO;
    private FileDAO fileDAO;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use workflowDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

    }
    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Manually register and publish a workflow with the given path and name
     *
     * @param workflowsApi
     * @param workflowPath
     * @param workflowName
     * @param descriptorType
     * @param sourceControl
     * @param descriptorPath
     * @param toPublish
     * @return Published workflow
     *
    private Workflow manualRegisterAndPublish(WorkflowsApi workflowsApi, String workflowPath, String workflowName, String descriptorType,
        SourceControl sourceControl, String descriptorPath, boolean toPublish) {
        // Manually register
        Workflow workflow = workflowsApi
            .manualRegister(sourceControl.getFriendlyName().toLowerCase(), workflowPath, descriptorPath, workflowName, descriptorType,
                "/test.json");
        Assert.assertEquals(0, testingPostgres.getPublishEventCountForWorkflow(workflow.getId()));
        assertEquals(Workflow.ModeEnum.STUB, workflow.getMode());

        // Refresh
        workflow = workflowsApi.refresh(workflow.getId(), false);
        assertEquals(Workflow.ModeEnum.FULL, workflow.getMode());

        // Publish
        if (toPublish) {
            workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        }
        assertEquals(workflow.isIsPublished(), toPublish);
        Assert.assertEquals(toPublish ? 1 : 0, testingPostgres.getPublishEventCountForWorkflow(workflow.getId()));
        return workflow;
    }
    */
    // Tests 3 things:
    // WDL workflow with local imports
    // WDL workflow with HTTP imports
    // WDL workflow with HTTP imports and local imports and nested
    @Test
    public void testWDLLanguageParsingInformation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow wdl = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/md5sum-checker", "/md5sum/md5sum-workflow.wdl", "WDL",
                        DescriptorLanguage.WDL.toString(), "/test.json");
        Long id = wdl.getId();
        workflowApi.refresh(id, false);
        Workflow workflow = workflowApi.getWorkflow(id, null);
        WorkflowVersion workflowWithLocalImport = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithLocalImport")).findFirst().get();
        ParsedInformation parsedInformation = workflowWithLocalImport.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertTrue(parsedInformation.isHasLocalImports());
        Assert.assertFalse(parsedInformation.isHasHTTPImports());
        WorkflowVersion workflowWithHTTPImport = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithHTTPImport")).findFirst().get();
        ParsedInformation parsedInformationHTTP = workflowWithHTTPImport.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertFalse(parsedInformationHTTP.isHasLocalImports());
        Assert.assertTrue(parsedInformationHTTP.isHasHTTPImports());

        Workflow wdlChecker = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/md5sum-checker", "/checker-workflow-wrapping-workflow.wdl", "WDLChecker",
                        DescriptorLanguage.WDL.toString(), "/test.json");
        id = wdlChecker.getId();
        workflowApi.refresh(id, false);
        workflow = workflowApi.getWorkflow(id, null);
        WorkflowVersion workflowWithBothImports = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithHTTPImport")).findFirst().get();
        parsedInformation = workflowWithBothImports.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertTrue(parsedInformation.isHasLocalImports());
        Assert.assertTrue(parsedInformation.isHasHTTPImports());

    }

    // Tests 3 things:
    // CWL workflow with local imports
    // CWL workflow with HTTP imports
    // CWL workflow with HTTP imports and local imports and nested
    @Test
    public void testCWLLanguageParsingInformation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow cwlWorkflow = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "CWL",
                        DescriptorLanguage.CWL.toString(), "/test.json");
        Long cwlId = cwlWorkflow.getId();
        workflowApi.refresh(cwlId, false);
        Workflow workflow = workflowApi.getWorkflow(cwlId, null);
        WorkflowVersion workflowWithLocalImport = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithLocalImport")).findFirst().get();
        ParsedInformation parsedInformation = workflowWithLocalImport.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertTrue(parsedInformation.isHasLocalImports());
        Assert.assertFalse(parsedInformation.isHasHTTPImports());
        WorkflowVersion workflowWithHTTPImport = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithHTTPImport")).findFirst().get();
        ParsedInformation parsedInformationHTTP = workflowWithHTTPImport.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertFalse(parsedInformationHTTP.isHasLocalImports());
        Assert.assertTrue(parsedInformationHTTP.isHasHTTPImports());
        Workflow cwlChecker = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/md5sum-checker", "/checker-workflow-wrapping-workflow.cwl", "CWLChecker",
                        DescriptorLanguage.CWL.toString(), "/test.json");
        Long id = cwlChecker.getId();
        workflowApi.refresh(id, false);
        workflow = workflowApi.getWorkflow(id, null);
        WorkflowVersion workflowWithBothImports = workflow.getWorkflowVersions().stream()
                .filter(version -> version.getName().equals("workflowWithHTTPImport")).findFirst().get();
        parsedInformation = workflowWithBothImports.getVersionMetadata().getParsedInformationSet().get(0);
        Assert.assertTrue(parsedInformation.isHasLocalImports());
        Assert.assertTrue(parsedInformation.isHasHTTPImports());
    }

    @Test
    public void testStubRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl",
                "/test.json");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");

        final List<Workflow> workflows = usersApi.userWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        assertTrue("workflow size was " + workflows.size(), workflows.size() > 1);
        assertTrue(
            "found non stub workflows " + workflows.stream().filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).count(),
            workflows.stream().allMatch(workflow -> workflow.getMode() == Workflow.ModeEnum.STUB));
    }

    
    @Test
    public void testHostedEditAndDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        Workflow workflow = manualRegisterAndPublish(workflowApi, DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "", "cwl", SourceControl.GITHUB,
            "/Dockstore.cwl", false);

        // using hosted apis to delete normal workflow should fail
        HostedApi hostedApi = new HostedApi(webClient);
        try {
            hostedApi.deleteHostedWorkflowVersion(workflow.getId(), "v1.0");
            fail("Should throw API exception");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("cannot modify non-hosted entries this way"));
        }

        // using hosted apis to edit normal workflow should fail
        try {
            hostedApi.editHostedWorkflow(workflow.getId(), new ArrayList<>());
            fail("Should throw API exception");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("cannot modify non-hosted entries this way"));
        }
    }

    @Test
    public void testTableToolAndDagContent() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = manualRegisterAndPublish(workflowApi, "DockstoreTestUser2/cwl-gene-prioritization", "", "cwl", SourceControl.GITHUB, "/Dockstore.cwl", true);
        Assert.assertEquals("Other", workflow.getLicenseInformation().getLicenseName());
        WorkflowVersion branchVersion = workflow.getWorkflowVersions().stream().filter(wv -> wv.getName().equals("master")).findFirst().get();
        WorkflowVersion tagVersion = workflow.getWorkflowVersions().stream().filter(wv -> wv.getName().equals("test")).findFirst().get();

        // test getting tool table json on a branch and that it clears after refresh workflow
        String branchToolJsonFromApi = workflowApi.getTableToolContent(workflow.getId(), branchVersion.getId());
        String branchToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNotNull(branchToolJson);
        assertFalse(branchToolJson.isEmpty());
        assertEquals(branchToolJsonFromApi, branchToolJson);

        workflow = workflowApi.refresh(workflow.getId(), true);
        branchToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNull(branchToolJson);

        // Test getting tool table json for a tag and that only that version is cleared after a refreshVersion.
        String tagToolJsonFromApi = workflowApi.getTableToolContent(workflow.getId(), tagVersion.getId());
        String tagToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", tagVersion.getId()), String.class);
        assertNotNull(tagToolJson);
        assertFalse(tagToolJson.isEmpty());
        assertEquals(tagToolJsonFromApi, tagToolJson);

        workflowApi.getTableToolContent(workflow.getId(), branchVersion.getId());
        workflow = workflowApi.refreshVersion(workflow.getId(), tagVersion.getName(), true);
        tagToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", tagVersion.getId()), String.class);
        assertNull(tagToolJson);
        branchToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNotNull(branchToolJson);

        // Test getting dag json for a branch and that it clears after a refresh workflow
        String branchDagJsonFromApi = workflowApi.getWorkflowDag(workflow.getId(), branchVersion.getId());
        String branchDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNotNull(branchDagJson);
        assertFalse(branchDagJson.isEmpty());
        assertEquals(branchDagJsonFromApi, branchDagJson);

        workflow = workflowApi.refresh(workflow.getId(), true);
        branchDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNull(branchDagJson);

        // Test getting dag json for a tag that only that version is cleared after a refreshVersion
        String tagDagJsonFromApi = workflowApi.getWorkflowDag(workflow.getId(), tagVersion.getId());
        String tagDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", tagVersion.getId()), String.class);
        assertNotNull(tagDagJson);
        assertFalse(tagDagJson.isEmpty());
        assertEquals(tagDagJsonFromApi, tagDagJson);

        workflowApi.getWorkflowDag(workflow.getId(), branchVersion.getId());
        workflowApi.refreshVersion(workflow.getId(), tagVersion.getName(), true);
        tagDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", tagVersion.getId()), String.class);
        assertNull(tagDagJson);
        branchDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNotNull(branchDagJson);

        // Test json is cleared after an organization refresh
        UsersApi usersApi = new UsersApi(webClient);
        long userId = 1;
        workflow = workflowApi.refresh(workflow.getId(), true);

        final List<Workflow> workflows = usersApi.userWorkflows(userId);
        branchDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNull(branchDagJson);
        branchToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", branchVersion.getId()), String.class);
        assertNull(branchToolJson);

        // Test freezing versions (uses a different workflow that has versioned images)
        workflow = manualRegisterAndPublish(workflowApi, "dockstore-testing/hello_world", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", true);
        WorkflowVersion frozenVersion = snapshotWorkflowVersion(workflowApi, workflow, "1.0.1");
        String frozenDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", frozenVersion.getId()), String.class);
        String frozenToolTableJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", frozenVersion.getId()), String.class);
        assertNotNull(frozenDagJson);
        assertNotNull(frozenToolTableJson);
    }

    /**
     * Tests for https://github.com/dockstore/dockstore/issues/3928
     */
    @Test
    public void testNextflowTableToolAndDagContent() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Test getting the tool table and dag for a nextflow workflow that has a nextflow.config and main.nf
        Workflow workflow = manualRegisterAndPublish(workflowApi, "DockstoreTestUser2/hello-nextflow-workflow", "", "nfl", SourceControl.GITHUB, "/nextflow.config", false);
        WorkflowVersion masterVersion = workflow.getWorkflowVersions().stream().filter(wv -> wv.getName().equals("master")).findFirst().get();
        String masterToolJsonFromApi = workflowApi.getTableToolContent(workflow.getId(), masterVersion.getId());
        String masterToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", masterVersion.getId()), String.class);
        assertNotNull(masterToolJson);
        assertFalse(masterToolJson.isEmpty());
        assertEquals(masterToolJsonFromApi, masterToolJson);

        String masterDagJsonFromApi = workflowApi.getWorkflowDag(workflow.getId(), masterVersion.getId());
        String masterDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", masterVersion.getId()), String.class);
        assertNotNull(masterDagJson);
        assertFalse(masterDagJson.isEmpty());
        assertEquals(masterDagJsonFromApi, masterDagJson);

        // Test getting the tool table and dag for a nextflow workflow that has a nextflow.config but is missing main.nf
        WorkflowVersion missingMainScriptVersion = workflow.getWorkflowVersions().stream().filter(wv -> wv.getName().equals("missingMainScriptFile")).findFirst().get();
        String missingMainScriptToolJsonFromApi = workflowApi.getTableToolContent(workflow.getId(), missingMainScriptVersion.getId());
        String missingMainScriptToolJson = testingPostgres.runSelectStatement(String.format("select tooltablejson from workflowversion where id = '%s'", missingMainScriptVersion.getId()), String.class);
        assertNotNull(missingMainScriptToolJson);
        assertFalse(missingMainScriptToolJson.isEmpty());
        assertEquals(missingMainScriptToolJsonFromApi, missingMainScriptToolJson);

        String missingMainScriptDagJsonFromApi = workflowApi.getWorkflowDag(workflow.getId(), missingMainScriptVersion.getId());
        String missingMainScriptDagJson = testingPostgres.runSelectStatement(String.format("select dagjson from workflowversion where id = '%s'", missingMainScriptVersion.getId()), String.class);
        assertNotNull(missingMainScriptDagJson);
        assertFalse(missingMainScriptDagJson.isEmpty());
        assertEquals(missingMainScriptDagJsonFromApi, missingMainScriptDagJson);
    }

    /**
     * This tests that you are able to download zip files for versions of a workflow
     */
    @Test
    public void downloadZipFile() throws IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Register and refresh workflow
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), false);
        Long workflowId = refresh.getId();
        WorkflowVersion workflowVersion = refresh.getWorkflowVersions().get(0);
        Long versionId = workflowVersion.getId();

        // Download unpublished workflow version
        workflowApi.getWorkflowZip(workflowId, versionId);
        byte[] arbitraryURL = CommonTestUtilities.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, webClient);
        File tempZip = File.createTempFile("temp", "zip");
        Path write = Files.write(tempZip.toPath(), arbitraryURL);
        ZipFile zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));

        // should not be able to get zip anonymously before publication
        boolean thrownException = false;
        try {
            CommonTestUtilities.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
            }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        } catch (Exception e) {
            thrownException = true;
        }
        assertTrue(thrownException);
        tempZip.deleteOnExit();

        // Download published workflow version
        workflowApi.publish(workflowId, CommonTestUtilities.createPublishRequest(true));
        arbitraryURL = CommonTestUtilities.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        File tempZip2 = File.createTempFile("temp", "zip");
        write = Files.write(tempZip2.toPath(), arbitraryURL);
        zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));
        tempZip2.deleteOnExit();
    }

    /**
     * This tests a not found zip file
     */
    @Test
    public void sillyWorkflowZipFile() throws IOException {
        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        WorkflowsApi anonWorkflowApi = new WorkflowsApi(anonWebClient);
        boolean success = false;
        try {
            anonWorkflowApi.getWorkflowZip(100000000L, 1000000L);
        } catch (ApiException ex) {
            assertEquals(ex.getCode(), HttpStatus.SC_NOT_FOUND);
            success = true;
        }
        assertTrue("should have got 404", success);
    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        WorkflowsApi anonWorkflowApi = new WorkflowsApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        WorkflowsApi otherUserWorkflowApi = new WorkflowsApi(otherUserWebClient);

        // Register and refresh workflow
        Workflow workflow = ownerWorkflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = ownerWorkflowApi.refresh(workflow.getId(), false);
        Long workflowId = refresh.getId();
        Long versionId = refresh.getWorkflowVersions().get(0).getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Anon: Should fail
        boolean success = true;
        try {
            anonWorkflowApi.getWorkflowZip(workflowId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserWorkflowApi.getWorkflowZip(workflowId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success);
        }

        // Publish
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        ownerWorkflowApi.publish(workflowId, publishRequest);

        // Try downloading published
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Anon: Should pass
        anonWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Other user: Should pass
        otherUserWorkflowApi.getWorkflowZip(workflowId, versionId);
    }

    /**
     * Downloads and verifies dockstore-testing/gatk-sv-clinical, a complex WDL workflow with lots
     * of imports
     */
    @Test
    public void downloadZipComplex() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);
        Workflow refresh = registerGatkSvWorkflow(ownerWorkflowApi);

        Long workflowId = refresh.getId();
        Long versionId = refresh.getWorkflowVersions().get(0).getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);

        // Unfortunately, the generated Swagger client for getWorkflowZip returns void.
        // Verify the zip contents by making the server side calls
        final io.dockstore.webservice.core.Workflow dockstoreWorkflow = workflowDAO.findById(workflowId);
        final Optional<io.dockstore.webservice.core.WorkflowVersion> version = dockstoreWorkflow.getWorkflowVersions().stream()
                .filter(wv -> wv.getId() == versionId).findFirst();
        Assert.assertTrue(version.isPresent());
        final File tempFile = File.createTempFile("dockstore-test", ".zip");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            final List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(version.get().getId());

            new EntryVersionHelperImpl().writeStreamAsZip(sourceFiles.stream().collect(Collectors.toSet()), fos, Paths.get("/GATKSVPipelineClinical.wdl"));
            final ZipFile zipFile = new ZipFile(tempFile);
            final long wdlCount = zipFile.stream().filter(e -> e.getName().endsWith(".wdl")).count();
            Assert.assertEquals(sourceFiles.size(), wdlCount);
            zipFile.stream().filter(e -> e.getName().endsWith(".wdl")).forEach(e -> {
                final String name = "/" + e.getName();
                Assert.assertTrue("expected " + name, sourceFiles.stream().anyMatch(sf -> sf.getAbsolutePath().equals(name)));
            });
            zipFile.close();
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        sessionFactory.getCurrentSession().close();
    }


    @Test
    public void testCheckerWorkflowDownloadBasedOnCredentials() throws IOException {
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final ApiClient webClientNoAccess = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApiNoAccess = new WorkflowsApi(webClientNoAccess);

        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId(), false);
        assertFalse(refresh.isIsPublished());
        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");
        workflowApi.refresh(workflow.getId(), false);

        final String fileWithIncorrectCredentials = ResourceHelpers.resourceFilePath("config_file.txt");
        final String fileWithCorrectCredentials = ResourceHelpers.resourceFilePath("config_file2.txt");

        final Long versionId = refresh.getWorkflowVersions().get(0).getId();

        // should be able to download properly with correct credentials even though the workflow is not published
        workflowApi.getWorkflowZip(refresh.getId(), versionId);

        // Publish the workflow
        final long publishEventCount = testingPostgres.getPublishEventCount();
        workflowApi.publish(refresh.getId(), CommonTestUtilities.createPublishRequest(true));
        // The checker workflow also gets published
        assertEquals(2 + publishEventCount, testingPostgres.getPublishEventCount());

        // Owner should still have access
        workflowApiNoAccess.getWorkflowZip(refresh.getId(), versionId);

        // should be able to download properly with incorrect credentials because the entry is published
        workflowApiNoAccess.getWorkflowZip(refresh.getId(), versionId);

        final long unpublishEventCount = testingPostgres.getUnpublishEventCount();
        // Unpublish the workflow
        workflowApi.publish(refresh.getId(), CommonTestUtilities.createPublishRequest(false));
        // The checker workflow also gets unpublished
        Assert.assertEquals(2 + unpublishEventCount, testingPostgres.getUnpublishEventCount());

        // should not be able to download properly with incorrect credentials because the entry is not published
        try {
            workflowApiNoAccess.getWorkflowZip(refresh.getId(), versionId);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Forbidden"));
        }
    }

    @Test
    public void testNextflowRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflowByPathGithub = manualRegisterAndPublish(workflowApi, "DockstoreTestUser2/rnatoy", "", "nfl", SourceControl.GITHUB,
            "/nextflow.config", false);

        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);

        // look that branches and tags are typed correctly for workflows on GitHub
        assertTrue("should see at least 6 branches", refreshGithub.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 6);
        assertTrue("should see at least 6 tags", refreshGithub.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 6);

        assertEquals("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(), 12,
            refreshGithub.getWorkflowVersions().size());
        assertEquals("should find 12 versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count(), 12,
            refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count());
        assertEquals("should find 12 valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 12,
            refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        // nextflow version should have
        assertTrue("should find 2 files for each version for now: " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> fileDAO.findSourceFilesByVersion(workflowVersion.getId()).size() != 2).count(),
            refreshGithub.getWorkflowVersions().stream().noneMatch(workflowVersion -> fileDAO.findSourceFilesByVersion(workflowVersion.getId()).size() != 2));

        // check that container is properly parsed
        Optional<WorkflowVersion> nextflow = refreshGithub.getWorkflowVersions().stream()
            .filter(workflow -> workflow.getName().equals("master")).findFirst();
        String workflowDag = workflowApi.getWorkflowDag(refreshGithub.getId(), nextflow.get().getId());
        ArrayList<String> dagList = Lists.newArrayList(workflowDag);

        Assert.assertTrue("Should use nextflow/rnatoy and not ubuntu:latest", dagList.get(0)
            .contains("\"docker\":\"nextflow/rnatoy@sha256:9ac0345b5851b2b20913cb4e6d469df77cf1232bafcadf8fd929535614a85c75\""));
    }

    @Test
    public void testNextflowWorkflowWithConfigIncludes() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        Workflow workflowByPathGithub = manualRegisterAndPublish(workflowApi, "DockstoreTestUser2/vipr", "", "nfl", SourceControl.GITHUB,
            "/nextflow.config", false);

        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_INCLUDECONFIG_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);

        assertEquals("workflow does not include expected config included files", 3,
            fileDAO.findSourceFilesByVersion(refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("master")).findFirst().get().getId())
                    .stream().filter(file -> file.getPath().startsWith("conf/")).count());
    }

    @Test
    public void testNextflowWorkflowWithImages() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflowByPathGithub = manualRegisterAndPublish(workflowApi, "DockstoreTestUser2/galaxy-workflows", "", "nfl", SourceControl.GITHUB,
            "/nextflow.config", false);

        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);
        Optional<WorkflowVersion> first = refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("1.0"))
            .findFirst();
        String tableToolContent = workflowApi.getTableToolContent(refreshGithub.getId(), first.get().getId());
        String workflowDag = workflowApi.getWorkflowDag(refreshGithub.getId(), first.get().getId());
        assertFalse(tableToolContent.isEmpty());
        assertFalse(workflowDag.isEmpty());
        Gson gson = new Gson();
        List<Map<String, String>> list = gson.fromJson(tableToolContent, List.class);
        Map<Map, List> map = gson.fromJson(workflowDag, Map.class);
        assertTrue("tool table should be present", list.size() >= 9);
        long dockerCount = list.stream().filter(tool -> !tool.get("docker").isEmpty()).count();
        assertEquals("tool table is populated with docker images", dockerCount, list.size());
        assertTrue("workflow dag should be present", map.entrySet().size() >= 2);
        assertTrue("workflow dag is not as large as expected", map.get("nodes").size() >= 11 && map.get("edges").size() >= 13);
    }



    /**
     * This test does not use admin rights, note that a number of operations go through the UserApi to get this to work
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testPublishingAndListingOfPublished() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);

        // should start with nothing published
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);

        refreshByOrganizationReplacement(workflowApi, openAPIWebClient);

        assertTrue("should remain with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).isEmpty());
        // ensure that sorting or filtering don't expose unpublished workflows
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, "descriptorType", "asc", false, null).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", null, null, false, null).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", "descriptorType", "asc", false, null).isEmpty());

        // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId(), false);

        // publish one
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertEquals("should have one published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size(),
            1, workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size());
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId(), null);
        assertNotNull("did not get published workflow", publishedWorkflow);
        final Workflow publishedWorkflowByPath = workflowApi
            .getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null,  null);
        assertNotNull("did not get published workflow", publishedWorkflowByPath);

        // publish everything so pagination testing makes more sense (going to unfortunately use rate limit)
        Lists.newArrayList("github.com/" + DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME,
            "github.com/DockstoreTestUser2/dockstore-whalesay-imports", "github.com/DockstoreTestUser2/parameter_test_workflow")
            .forEach(path -> {
                Workflow workflow = workflowApi.getWorkflowByPath(path, BIOWORKFLOW, null);
                workflowApi.refresh(workflow.getId(), false);
                workflowApi.publish(workflow.getId(), publishRequest);
            });
        List<Workflow> workflows = workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null);
        // test offset
        assertEquals("offset does not seem to be working",
            workflowApi.allPublishedWorkflows(1, null, null, null, null, false, null).get(0).getId(), workflows.get(1).getId());
        // test limit
        assertEquals(1, workflowApi.allPublishedWorkflows(null, 1, null, null, null, false, null).size());
        // test custom sort column
        List<Workflow> ascId = workflowApi.allPublishedWorkflows(null, null, null, "id", "asc", false, null);
        List<Workflow> descId = workflowApi.allPublishedWorkflows(null, null, null, "id", "desc", false, null);
        assertEquals("sort by id does not seem to be working", ascId.get(0).getId(), descId.get(descId.size() - 1).getId());
        // test filter
        List<Workflow> filteredLowercase = workflowApi.allPublishedWorkflows(null, null, "whale", "stars", null, false, null);
        assertEquals(1, filteredLowercase.size());
        filteredLowercase.forEach(workflow -> assertNull(workflow.getAliases()));
        List<Workflow> filteredUppercase = workflowApi.allPublishedWorkflows(null, null, "WHALE", "stars", null, false, null);
        assertEquals(1, filteredUppercase.size());
        assertEquals(filteredLowercase, filteredUppercase);

        // Tests for subclass

        assertEquals("There should be no app tools published", 0,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.APPTOOL.getValue()).size());

        final int publishedWorkflowsCount = workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
            null).size();
        assertEquals("An null subclass param defaults to services param value",
            publishedWorkflowsCount,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
            WorkflowSubClass.BIOWORKFLOW.getValue()).size());

        // Create an app tool and publish it
        workflowApi.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        Workflow appTool = workflowApi.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions");
        workflowApi.publish(appTool.getId(), publishRequest);
        assertEquals("There should be 1 app tool published", 1,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.APPTOOL.getValue()).size());
        assertEquals("Published workflow count should be unchanged", publishedWorkflowsCount,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.BIOWORKFLOW.getValue()).size());
    }

   
    /**
     * Tests that the info for quay images included in CWL workflows are grabbed and that the trs endpoints convert this info correctly
     */
    @Test
    public void testGettingImagesFromQuay() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        //Check image info is grabbed
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello_world", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "1.0.1");
        assertEquals("Should only be one image in this workflow", 1, version.getImages().size());
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello_world");
        verifyTRSImageConversion(versions, "1.0.1", 1);

        // Test that a workflow version that contains duplicate images will not store multiples
        workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/zhanghj-8555114", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/main.cwl", true);
        WorkflowVersion versionWithDuplicateImages = snapshotWorkflowVersion(workflowsApi, workflow, "1.0");
        assertEquals("Should have grabbed 3 images", 3, versionWithDuplicateImages.getImages().size());
        verifyImageChecksumsAreSaved(versionWithDuplicateImages);
        versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/zhanghj-8555114");
        verifyTRSImageConversion(versions, "1.0", 3);
    }

    @Test
    public void testGettingImagesFromGitHubContainerRegistry() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a versioned multi-architecture image gets an image per architecture: ghcr.io/homebrew/core/python/3.9:3.9.6 -> 5 OS/Arch images
        // Test that a specific architecture image referenced in the following format is grabbed correctly: ghcr.io/<owner>/<image_name>:<tag>@sha256:<digest>
        // Test that an image referenced by digest is grabbed correctly
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "ghcrImages");
        assertTrue("Should have at least 7 images. There are " + version.getImages().size(), version.getImages().size() >= 7);
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow");
        verifyTRSImageConversion(versions, "ghcrImages", 7);
    }

    @Test
    public void testGettingImagesFromAmazonECR() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a versioned multi-architecture image gets an image per architecture: public.ecr.aws/ubuntu/ubuntu:18.04 -> 5 OS/Arch images
        // Test that an image referenced by digest is grabbed correctly
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "ecrImages");
        assertTrue("Should have at least 6 images. There are " + version.getImages().size(), version.getImages().size() >= 6);
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow");
        verifyTRSImageConversion(versions, "ecrImages", 6);
    }

    /**
     * Tests that snapshotting a workflow version fails if any of the images have no tag, use the 'latest' tag, or are specified using a parameter.
     */
    @Test
    public void testSnapshotImageFailures() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", false);
        String errorMessage = "Snapshot for workflow version %s failed because not all images are specified using a digest nor a valid tag.";

        // Test that the snapshot fails for a workflow version containing an image with no tag
        try {
            snapshotWorkflowVersion(workflowsApi, workflow, "noTagImage");
            Assert.fail("Should not be able to snapshot a workflow version containing an image with no tag.");
        } catch (ApiException ex) {
            Assert.assertTrue(ex.getMessage().contains(String.format(errorMessage, "noTagImage")));
        }

        // Test that the snapshot fails for a workflow version containing an image with the 'latest' tag
        try {
            snapshotWorkflowVersion(workflowsApi, workflow, "latestTagImage");
            Assert.fail("Should not be able to snapshot a workflow version containing an image with the 'latest' tag.");
        } catch (ApiException ex) {
            Assert.assertTrue(ex.getMessage().contains(String.format(errorMessage, "latestTagImage")));
        }

        // Test that the snapshot fails for a workflow version containing an image specified using a parameter
        try {
            snapshotWorkflowVersion(workflowsApi, workflow, "parameterImage");
            Assert.fail("Should not be able to snapshot a workflow version containing an image specified using a parameter.");
        } catch (ApiException ex) {
            Assert.assertTrue(ex.getMessage().contains(String.format(errorMessage, "parameterImage")));
        }
    }

    @Test
    public void testHiddenAndDefaultVersions() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow workflow = workflowsApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", DescriptorLanguage.WDL.toString(), "/test.json");

        workflow = workflowsApi.refresh(workflow.getId(), false);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        WorkflowVersion version = workflowVersions.stream().filter(w -> w.getReference().equals("testBoth")).findFirst().get();
        version.setHidden(true);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));

        try {
            workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), version.getName());
            fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            Assert.assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }

        // Set the default version to a non-hidden version
        version.setHidden(false);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), version.getName());

        // Should not be able to hide a default version
        version.setHidden(true);
        try {
            workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
            fail("Should not be able to hide a default version");
        } catch (ApiException ex) {
            Assert.assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        // Test same for hosted workflows
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\n" + "class: Workflow");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        hostedWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));

        WorkflowVersion hostedVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).get(0);
        hostedVersion.setHidden(true);
        try {
            workflowsApi.updateWorkflowVersion(hostedWorkflow.getId(), Collections.singletonList(hostedVersion));
            fail("Shouldn't be able to hide the default version.");
        } catch (ApiException ex) {
            Assert.assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        file.setContent("cwlVersion: v1.0\n\n" + "class: Workflow");
        hostedWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        hostedVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).stream().filter(v -> v.getName().equals("1")).findFirst().get();
        hostedVersion.setHidden(true);
        workflowsApi.updateWorkflowVersion(hostedWorkflow.getId(), Collections.singletonList(hostedVersion));

        try {
            workflowsApi.updateWorkflowDefaultVersion(hostedWorkflow.getId(), hostedVersion.getName());
            fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            Assert.assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }
    }

    /**
    * Tests the a checksum is calculated for workflow sourcefiles on refresh or snapshot. Also checks that trs endpoints convert correctly.
    * */
    @Test
    public void testChecksumsForSourceFiles() {
        // Test grabbing checksum on refresh
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);
        Workflow workflow = workflowsApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", DescriptorLanguage.WDL.toString(), "/test.json");

        workflow = workflowsApi.refresh(workflow.getId(), false);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        assertFalse(workflowVersions.isEmpty());
        boolean testedWDL = false;

        for (WorkflowVersion workflowVersion : workflowVersions) {
            if (workflowVersion.getName().equals("testBoth") || workflowVersion.getName().equals("testWDL")) {
                testedWDL = true;
                List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersion.getId());
                assertNotNull(sourceFiles);
                verifySourcefileChecksumsSaved(sourceFiles);
                sourceFiles.stream().forEach(sourceFile -> assertFalse("Source file should have a checksum", sourceFile.getChecksums().get(0).toString().isEmpty()));
            }
        }
        assertTrue(testedWDL);

        // Test grabbing checksum on snapshot
        Workflow workflow2 = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello_world", "", DescriptorLanguage.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", true);
        WorkflowVersion snapshotVersion = workflow2.getWorkflowVersions().stream().filter(v -> v.getName().equals("1.0.1")).findFirst().get();
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(snapshotVersion.getId());
        assertNotNull(sourceFiles);
        snapshotWorkflowVersion(workflowsApi, workflow2, "1.0.1");
        verifySourcefileChecksumsSaved(sourceFiles);

        // Make sure refresh does not error.
        workflowsApi.refresh(workflow2.getId(), false);

        // Test TRS conversion
        io.dockstore.openapi.client.model.FileWrapper fileWrapper = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(DescriptorLanguage.CWL.toString(), "#workflow/github.com/dockstore-testing/hello_world", "1.0.1");
        verifyTRSSourcefileConversion(fileWrapper);

        testingPostgres.runUpdateStatement("update sourcefile set content = null");
        // Make sure the above worked
        final Long nullContentCount = testingPostgres.runSelectStatement(
            "select count(*) from sourcefile where content is null", Long.class);
        assertNotEquals(0, nullContentCount.longValue());

        // Test that null content has a checksum
        final Long nullSha256Count = testingPostgres.runSelectStatement(
            "select count(*) from sourcefile where sha256 is null", Long.class);
        assertEquals(0, nullSha256Count.longValue());

    }

    private void verifyTRSSourcefileConversion(final io.dockstore.openapi.client.model.FileWrapper fileWrapper) {
        assertEquals(1, fileWrapper.getChecksum().size());
        fileWrapper.getChecksum().stream().forEach(checksum -> {
            assertFalse(checksum.getChecksum().isEmpty());
            assertEquals(DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS, checksum.getType());
        });
    }

    private void verifySourcefileChecksumsSaved(final List<io.dockstore.webservice.core.SourceFile> sourceFiles) {
        assertTrue(sourceFiles.size() >= 1);
        sourceFiles.stream().forEach(sourceFile -> {
            assertFalse("Source File should have a checksum", sourceFile.getChecksums().isEmpty());
            assertTrue(sourceFile.getChecksums().size() >= 1);
            sourceFile.getChecksums().stream().forEach(checksum -> {
                assertEquals(io.dockstore.webservice.core.SourceFile.SHA_TYPE, checksum.getType());
                assertFalse(checksum.getChecksum().isEmpty());
            });
        });
    }

    private void verifyTRSImageConversion(final List<ToolVersion> versions, final String snapShottedVersionName, final int numImages) {
        assertFalse("Should have at least one version", versions.isEmpty());
        boolean snapshotInList = false;
        for (ToolVersion trsVersion : versions) {
            if (trsVersion.getName().equals(snapShottedVersionName)) {
                assertTrue(trsVersion.isIsProduction());
                assertTrue(String.format("There should be at least %s image(s) in this workflow. There are %s.", numImages, trsVersion.getImages().size()), trsVersion.getImages().size() >= numImages);
                snapshotInList = true;
                assertFalse(trsVersion.getImages().isEmpty());
                for (ImageData imageData :trsVersion.getImages()) {
                    assertNotNull(imageData.getChecksum());
                    imageData.getChecksum().stream().forEach(checksum -> {
                        assertEquals(checksum.getType(), DOCKER_IMAGE_SHA_TYPE_FOR_TRS);
                        assertFalse(checksum.getChecksum().isEmpty());
                    });
                    assertNotNull(imageData.getSize());
                    assertNotNull(imageData.getRegistryHost());
                }
            } else {
                assertFalse(trsVersion.isIsProduction());
                assertEquals("Non-snapshotted versions should have 0 images ", 0, trsVersion.getImages().size());
            }
        }
        assertTrue("Snapshotted version should be in the list", snapshotInList);
    }



    /**
     * Test that the image_name is set correctly after TRS image conversion.
     * This is a separate test from verifyTRSImageConversion because it's difficult to map the snapshot version's images to the
     * TRS version's images if there's more than 1 Docker reference in the workflow.
     * This test works with workflows containing 1 Docker reference (may not necessarily have only 1 image because DockerHub can provide
     * multiple images for a single version, one for each os/architecture).
     */
    @Test
    public void testTRSImageName() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);
        WorkflowVersion snapshotVersion;
        ToolVersion trsVersion;

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "",
                DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);

        // Workflow with Quay image specified using a tag
        String quayTagVersionName = "1.0";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, quayTagVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", quayTagVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals("quay.io/ga4gh-dream/dockstore-tool-helloworld:1.0.2", image.getImageName()));

        // Workflow with Quay image specified using a digest
        String quayDigestVersionName = "quayDigestImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, quayDigestVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", quayDigestVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals(
                "quay.io/ga4gh-dream/dockstore-tool-helloworld@sha256:3a854fd1ebd970011fa57c8c099347314eda36cc746fd831f4deff9a1d433718",
                image.getImageName()));

        // Workflow with Docker Hub image specified using a tag (6 images actually retrieved, one per architecture type)
        String dockerHubTagVersionName = "dockerHubTagImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, dockerHubTagVersionName);
        assertEquals("Should only be six images in this workflow", 6, snapshotVersion.getImages().size()); // 1 image per architecture type
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", dockerHubTagVersionName);
        assertEquals("Should be six images in this TRS version", 6, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals("library/ubuntu:16.04", image.getImageName()));

        // Workflow with Docker Hub image specified using a digest
        String dockerHubDigestVersionName = "dockerHubDigestImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, dockerHubDigestVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", dockerHubDigestVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        // library/ubuntu@sha256:d7bb0589725587f2f67d0340edb81fd1fcba6c5f38166639cf2a252c939aa30c refers to ubuntu version 16.04, amd64 os/arch
        trsVersion.getImages().stream().forEach(image ->
            assertEquals("library/ubuntu@sha256:d7bb0589725587f2f67d0340edb81fd1fcba6c5f38166639cf2a252c939aa30c", image.getImageName()));
    }

    private WorkflowVersion snapshotWorkflowVersion(WorkflowsApi workflowsApi, Workflow workflow, String versionName) {
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals(versionName)).findFirst().get();
        version.setFrozen(true);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
        workflow = workflowsApi.getWorkflow(workflow.getId(), "images");
        return workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals(versionName)).findFirst().get();
    }

    private void verifyImageChecksumsAreSaved(WorkflowVersion version) {
        assertFalse(version.getImages().isEmpty());
        version.getImages().stream().forEach(image -> image.getChecksums().stream().forEach(checksum -> {
            assertFalse(checksum.getChecksum().isEmpty());
            assertFalse(checksum.getType().isEmpty());
        })
        );
    }

    @Test
    public void testGettingImagesFromDockerHub() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a version of an official dockerhub image will get an image per architecture. (python 2.7) Also check that regular
        // DockerHub images are grabbed correctly broadinstitute/gatk:4.0.1.1
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/broad-prod-wgs-germline-snps-indels", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/JointGenotypingWf.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "1.1.2");
        assertEquals("Should 10 images in this workflow", 10, version.getImages().size());
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/broad-prod-wgs-germline-snps-indels");
        verifyTRSImageConversion(versions, "1.1.2", 10);
    }

    @Test
    public void testCreationOfIncorrectHostedWorkflowTypeGarbage() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.createHostedWorkflow("name", null, "garbage type", null, null);
    }

    @Test
    public void testGetEntryByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.model.Entry foundEntry;

        // Find a hosted workflow
        io.dockstore.openapi.client.model.Workflow workflow = hostedApi.createHostedWorkflow(null, "name", io.openapi.model.DescriptorType.CWL.toString(), null, null);
        try {
            foundEntry = workflowsApi.getEntryByPath("dockstore.org/DockstoreTestUser2/name");
            assertEquals(workflow.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            fail("Should be able to find the workflow entry with path " + workflow.getFullWorkflowPath());
        }

        // Try to find a workflow that doesn't exist
        try {
            workflowsApi.getEntryByPath("workflow/does/not/exist");
            fail("Should not be able to find a workflow that doesn't exist.");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals("Entry not found", e.getMessage());
        }

        // Find a hosted tool -> simple case where the repo-name has no slashes: 'foo', no tool name
        io.dockstore.openapi.client.model.DockstoreTool tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo",
                io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo");
            assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo name: 'foo/bar', no tool name
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar");
            assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo-name: 'foo/bar', tool name: 'tool-name'
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", "tool-name");
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar/tool-name");
            assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }
    }

    @Test
    public void testDuplicateHostedWorkflowCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        thrown.expectMessage("already exists");
        hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
    }

    @Test
    public void testDuplicateHostedToolCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedTool("name", Registry.DOCKER_HUB.getDockerPath(), DescriptorType.CWL.toString(), "namespace", null);
        thrown.expectMessage("already exists");
        hostedApi.createHostedTool("name", Registry.DOCKER_HUB.getDockerPath(), DescriptorType.CWL.toString(), "namespace", null);
    }

    @Test
    public void testAmazonECRHostedToolCreation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        io.dockstore.openapi.client.api.ContainersApi containersApi = new io.dockstore.openapi.client.api.ContainersApi(webClient);

        // Create a hosted Amazon ECR tool using a private repository
        io.dockstore.openapi.client.model.DockstoreTool tool = hostedApi.createHostedTool("test.dkr.ecr.us-east-1.amazonaws.com", "foo", io.openapi.model.DescriptorType.CWL.toString(), "namespace", "bar");
        assertNotNull(containersApi.getContainer(tool.getId(), ""));

        // Create a hosted Amazon ECR tool using a public repository
        tool = hostedApi.createHostedTool("public.ecr.aws", "foo", io.openapi.model.DescriptorType.CWL.toString(), "namespace", "bar");
        assertNotNull(containersApi.getContainer(tool.getId(), ""));
    }

    @Test
    public void testDuplicateAmazonECRHostedToolCreation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        String alreadyExistsMessage = "already exists";

        // Simple case: the two tools have the same names and entry names
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        thrown.expectMessage(alreadyExistsMessage);
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);

        // The two tools have different names and entry names, but the tool paths are the same
        // Scenario 1:
        // Tool 1 has name: 'foo/bar' and no entry name
        // Tool 2 has name: 'foo' and entry name: 'bar'
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        thrown.expectMessage(alreadyExistsMessage);
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", "bar");

        // Scenario 2:
        // Tool 1 has name: 'foo' and entry name: 'bar'
        // Tool 2 has name: 'foo/bar' and no entry name
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "wxyz6789", "bar");
        thrown.expectMessage(alreadyExistsMessage);
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "wxyz6789", null);
    }

    @Test
    public void testHostedWorkflowMetadata() throws IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        assertNotNull(hostedWorkflow.getLastModifiedDate());
        assertNotNull(hostedWorkflow.getLastUpdated());

        // make a couple garbage edits
        SourceFile source = new SourceFile();
        source.setPath("/Dockstore.cwl");
        source.setAbsolutePath("/Dockstore.cwl");
        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source1 = new SourceFile();
        source1.setPath("sorttool.cwl");
        source1.setContent("foo");
        source1.setAbsolutePath("/sorttool.cwl");
        source1.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source2 = new SourceFile();
        source2.setPath("revtool.cwl");
        source2.setContent("foo");
        source2.setAbsolutePath("/revtool.cwl");
        source2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));

        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source1.setContent("food");
        source2.setContent("food");
        final Workflow updatedHostedWorkflow = hostedApi
            .editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertNotNull(updatedHostedWorkflow.getLastModifiedDate());
        assertNotNull(updatedHostedWorkflow.getLastUpdated());

        // note that this workflow contains metadata defined on the inputs to the workflow in the old (pre-map) CWL way that is still valid v1.0 CWL
        source.setContent(FileUtils
            .readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        source1.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/sorttool.cwl")), StandardCharsets.UTF_8));
        source2.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/revtool.cwl")), StandardCharsets.UTF_8));
        Workflow workflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertFalse(workflow.getInputFileFormats().isEmpty());
        assertFalse(workflow.getOutputFileFormats().isEmpty());
    }

    /**
     * This tests that a nested WDL workflow (three levels) is properly parsed
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testNestedWdlWorkflow() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Set up postgres

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/nested-wdl", "/Dockstore.wdl", "altname", "wdl", "/test.json");

        // Assert some things
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("No workflows are in full mode", 0, count);

        // Refresh the workflow
        workflowApi.refresh(githubWorkflow.getId(), false);

        // Confirm that correct number of sourcefiles are found
        githubWorkflow = workflowApi.getWorkflow(githubWorkflow.getId(), null);
        List<WorkflowVersion> versions = githubWorkflow.getWorkflowVersions();
        assertEquals("There should be two versions", 2, versions.size());

        Optional<WorkflowVersion> loopVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "infinite-loop"))
            .findFirst();
        if (loopVersion.isPresent()) {
            assertEquals("There should be two sourcefiles", 2, fileDAO.findSourceFilesByVersion(loopVersion.get().getId()).size());
        } else {
            fail("Could not find version infinite-loop");
        }

        Optional<WorkflowVersion> masterVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "master"))
            .findFirst();
        if (masterVersion.isPresent()) {
            assertEquals("There should be three sourcefiles", 3, fileDAO.findSourceFilesByVersion(masterVersion.get().getId()).size());
        } else {
            fail("Could not find version master");
        }
    }



    /**
     * Tests that trying to register a duplicate workflow fails, and that registering a non-existant repository fails
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testManualRegisterErrors() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);

        // Manually register workflow
        boolean success = true;
        try {
            workflowApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", "wdl", "/test.json");
            workflowApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertFalse(success);
        }

        success = true;
        try {
            workflowApi.manualRegister("github", "dasn/iodnasiodnasio", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertFalse(success);
        }
    }

    /**
     * Tests that the workflow name is validated when manually registering a workflow
     */
    @Test
    public void testManualWorkflowNameValidation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        try {
            workflowsApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "!@#$/%^&*<foo><bar>", "wdl", "/test.json");
            fail("Should not be able to register a workflow with a workflow name containing special characters that are not underscores and hyphens.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Invalid workflow name"));
        }
    }

    @Test
    public void testSecondaryFileOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files
        final List<SourceFile> masterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals("should find 2 imports, found " + masterImports.size(), 2, masterImports.size());
        final SourceFile master = workflowApi.primaryDescriptor(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertTrue("master content incorrect", master.getContent().contains("untar") && master.getContent().contains("compile"));

        // get secondary files by path
        SourceFile argumentsTool = workflowApi
            .secondaryDescriptorPath(workflow.getId(), "arguments.cwl", "master", DescriptorLanguage.CWL.toString());
        assertTrue("argumentstool content incorrect", argumentsTool.getContent().contains("Example trivial wrapper for Java 7 compiler"));
    }


    /**
     * This tests that the absolute path is properly set for CWL workflow sourcefiles for the primary descriptor and any imported files
     */
    @Test
    public void testAbsolutePathForImportedFilesCWL() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", "", "cwl",
            "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW, BIOWORKFLOW, null);
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);

        Assert.assertEquals("should have 2 version", 2, workflow.getWorkflowVersions().size());
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "test")).findFirst();
        if (workflowVersion.isEmpty()) {
            Assert.fail("Missing the test release");
        }

        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersion.get().getId());
        Optional<io.dockstore.webservice.core.SourceFile> primarySourceFile = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "/workflows/dnaseq/transform.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/workflows/dnaseq/transform.cwl")).findFirst();
        if (primarySourceFile.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the primary descriptor.");
        }

        Optional<io.dockstore.webservice.core.SourceFile> importedSourceFileOne = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "../../tools/bam_readgroup_to_json.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/tools/bam_readgroup_to_json.cwl")).findFirst();
        if (importedSourceFileOne.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the imported file.");
        }

        Optional<io.dockstore.webservice.core.SourceFile> importedSourceFileTwo = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "integrity.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/workflows/dnaseq/integrity.cwl")).findFirst();
        if (importedSourceFileTwo.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the imported file.");
        }
    }

    /**
     * NOTE: This test is not normally run. It is only for running locally to confirm that the discourse topic generation is working.
     * <p>
     * Adds a discourse topic for a workflow (adds to a Automatic Tool and Workflow Threads - NEED TO DELETE TOPIC)
     * <p>
     * Requires you to have the correct discourse information set in the dockstoreTest.yml
     */
    @Ignore
    public void publishWorkflowAndTestDiscourseTopicCreation() {
        final ApiClient curatorApiClient = getWebClient(curatorUsername, testingPostgres);
        EntriesApi curatorEntriesApi = new EntriesApi(curatorApiClient);
        final ApiClient userApiClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);

        // Create a workflow with a random name
        String workflowName = Long.toString(Instant.now().toEpochMilli());
        userWorkflowsApi
            .manualRegister("github", "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", workflowName, "cwl",
                "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath(DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW + "/" + workflowName, BIOWORKFLOW, null);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId(), true);

        // Publish workflow, which will also add a topic
        userWorkflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // Should not be able to create a topic for the same workflow
        try {
            curatorEntriesApi.setDiscourseTopic(workflow.getId());
            fail("Should still not be able to set discourse topic.");
        } catch (ApiException ignored) {
            assertTrue(true);
        }

        // Unpublish and publish, should not throw error
        Workflow unpublishedWf = userWorkflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));
        Workflow publishedWf = userWorkflowsApi.publish(unpublishedWf.getId(), CommonTestUtilities.createPublishRequest(true));
        assertEquals("Topic id should remain the same.", unpublishedWf.getTopicId(), publishedWf.getTopicId());
    }

    /**
     * Test for cwl1.1
     * Of the languages support features, this tests:
     * Workflow Registration
     * Metadata Display
     * Validation
     */
    @Test
    public void cwlVersion11() {
        final ApiClient userApiClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);
        userWorkflowsApi.manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/metadata.cwl", "metadata", "cwl",
            "/cwl/v1.1/cat-job.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/metadata", BIOWORKFLOW, null);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId(), true);
        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            Assert.assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
            Assert.assertEquals("Peter Amstutz", workflow.getAuthor());
            Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        });
        Assert.assertEquals("Default branch should've been set to get metadata", "master", workflow.getDefaultVersion());
        Assert.assertEquals("peter.amstutz@curoverse.com", workflow.getEmail());
        Assert.assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
        Assert.assertEquals("Peter Amstutz", workflow.getAuthor());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Assert.assertEquals("Default version should've been set to get metadata", "master", workflow.getDefaultVersion());
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersion.getId());
        Assert.assertEquals(2, sourceFiles.size());
        Assert.assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/cat-job.json")));
        Assert.assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/metadata.cwl")));
        // Check validation works.  It is invalid because this is a tool and not a workflow.
        Assert.assertFalse(workflowVersion.isValid());

        userWorkflowsApi
            .manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/count-lines1-wf.cwl", "count-lines1-wf", "cwl",
                "/cwl/v1.1/wc-job.json");
        final Workflow workflowByPathGithub2 = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf", BIOWORKFLOW, null);
        final Workflow workflow2 = userWorkflowsApi.refresh(workflowByPathGithub2.getId(), false);
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion2 = workflow2.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion2.isPresent());
        WorkflowVersion workflowVersion2 = optionalWorkflowVersion2.get();
        // Check validation works.  It should be valid
        Assert.assertTrue(workflowVersion2.isValid());
        userWorkflowsApi.publish(workflowByPathGithub2.getId(), CommonTestUtilities.createPublishRequest(true));
    }

    
    @Test
    public void testWorkflowVersionAliasOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv",
                "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);
        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();

        // give the workflow version a few aliases
        AliasesApi aliasesApi = new AliasesApi(webClient);
        WorkflowVersion workflowVersionWithAliases = aliasesApi.addAliases(workflowVersion.getId(), "awesome workflowversion, spam, test workflowversion");
        Assert.assertTrue("entry is missing expected aliases",
                workflowVersionWithAliases.getAliases().containsKey("awesome workflowversion")
                        && workflowVersionWithAliases.getAliases().containsKey("spam")
                        && workflowVersionWithAliases.getAliases().containsKey("test workflowversion"));

        // add a few new aliases
        workflowVersion = aliasesApi.addAliases(workflowVersion.getId(), "foobar, another workflowversion");
        Assert.assertTrue("entry is missing expected aliases",
                workflowVersion.getAliases().containsKey("foobar")
                        && workflowVersion.getAliases().containsKey("test workflowversion")
                        && workflowVersion.getAliases().size() == 5);

        // try to add duplicates; this is not allowed
        boolean throwsError = false;
        try {
            // add a few new aliases
            workflowVersion = aliasesApi.addAliases(workflow.getId(), "another workflowversion");
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to add a duplicate Workflow version alias.");
        }

        // Get workflow version by alias
        io.dockstore.webservice.core.WorkflowVersion aliasWorkflowVersion = workflowVersionDAO.findByAlias("foobar");
        Assert.assertNotNull("Should retrieve the workflow by alias", aliasWorkflowVersion);
    }

    @Test
    public void testWorkflowVersionAliasesAreReturned() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv",
                "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);
        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();

        // give the workflow version a few aliases
        AliasesApi aliasesApi = new AliasesApi(webClient);
        WorkflowVersion workflowVersionWithAliases = aliasesApi
                .addAliases(workflowVersion.getId(), "awesome workflowversion, spam, test workflowversion");
        Assert.assertTrue("entry is missing expected aliases",
                workflowVersionWithAliases.getAliases().containsKey("awesome workflowversion") && workflowVersionWithAliases.getAliases().containsKey("spam")
                        && workflowVersionWithAliases.getAliases().containsKey("test workflowversion"));

        // Do not include the validation parameter that requests workflow version aliases be included in the returned object
        // So the aliases portion of the returned object should be null
        Workflow workflowById = workflowApi.getWorkflow(workflow.getId(), null);
        Optional<WorkflowVersion> optionalWorkflowVersionById = workflowById.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionById.isPresent());
        WorkflowVersion workflowVersionById = optionalWorkflowVersionById.get();
        Assert.assertNull("Getting workflow version via workflow ID has null alias", workflowVersionById.getAliases());

        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflow.getId(), null);
        assertNotNull("did not get published workflow", publishedWorkflow);
        Optional<WorkflowVersion> optionalWorkflowVersionByPublished = publishedWorkflow.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPublished.isPresent());
        WorkflowVersion workflowVersionByPublshed = optionalWorkflowVersionByPublished.get();
        Assert.assertNull("Getting workflow version via published workflow has null alias", workflowVersionByPublshed.getAliases());

        final Workflow workflowByPath = workflowApi
                .getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, "versions");
        assertNotNull("did not get published workflow by path", workflowByPath);
        Optional<WorkflowVersion> optionalWorkflowVersionByPath = workflowByPath.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPath.isPresent());
        WorkflowVersion workflowVersionByPath = optionalWorkflowVersionByPath.get();
        Assert.assertNull("Getting workflow version via workflow path has null alias", workflowVersionByPath.getAliases());

        final Workflow publishedWorkflowByPath = workflowApi
                .getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, "versions",  null);
        assertNotNull("did not get published workflow by path", publishedWorkflowByPath);
        Optional<WorkflowVersion> optionalWorkflowVersionByPublishedByPath = publishedWorkflowByPath.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPublishedByPath.isPresent());
        WorkflowVersion workflowVersionByPublshedByPath = optionalWorkflowVersionByPublishedByPath.get();
        Assert.assertNull("Getting workflow version via published workflow has null alias", workflowVersionByPublshedByPath.getAliases());



        // Include the validation parameter that requests workflow version aliases be included in the returned object
        Workflow workflowByIdValidation = workflowApi.getWorkflow(workflow.getId(), "aliases");
        Optional<WorkflowVersion> optionalWorkflowVersionByIdValidation = workflowByIdValidation.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByIdValidation.isPresent());
        WorkflowVersion workflowVersionByIdValidation = optionalWorkflowVersionByIdValidation.get();
        Assert.assertFalse("Getting workflow version via workflow ID has null or empty alias",
                MapUtils.isEmpty(workflowVersionByIdValidation.getAliases()));

        final Workflow publishedWorkflowValidation = workflowApi.getPublishedWorkflow(workflow.getId(), "aliases");
        assertNotNull("did not get published workflow", publishedWorkflowValidation);
        Optional<WorkflowVersion> optionalWorkflowVersionByPublishedValidation = publishedWorkflowValidation.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPublishedValidation.isPresent());
        WorkflowVersion workflowVersionByPublshedValidation = optionalWorkflowVersionByPublishedValidation.get();
        Assert.assertFalse("Getting workflow version via published workflow has null or empty alias",
                MapUtils.isEmpty(workflowVersionByPublshedValidation.getAliases()));

        final Workflow workflowByPathValidation = workflowApi
                .getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, "aliases");
        assertNotNull("did not get published workflow by path", workflowByPathValidation);
        Optional<WorkflowVersion> optionalWorkflowVersionByPathValidation = workflowByPathValidation.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPathValidation.isPresent());
        WorkflowVersion workflowVersionByPathValidation = optionalWorkflowVersionByPathValidation.get();
        Assert.assertFalse("Getting workflow version via workflow path has null or empty alias",
                MapUtils.isEmpty(workflowVersionByPathValidation.getAliases()));


        final Workflow publishedWorkflowByPathValidation = workflowApi
                .getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, "aliases", null);
        assertNotNull("did not get published workflow by path", publishedWorkflowByPathValidation);
        Optional<WorkflowVersion> optionalWorkflowVersionByPublishedByPathValidation = publishedWorkflowByPathValidation.getWorkflowVersions().stream()
                .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersionByPublishedByPathValidation.isPresent());
        WorkflowVersion workflowVersionByPublshedByPathValidation = optionalWorkflowVersionByPublishedByPathValidation.get();
        Assert.assertFalse("Getting workflow version via published workflow has null alias",
                MapUtils.isEmpty(workflowVersionByPublshedByPathValidation.getAliases()));

    }

    @Test
    public void testGettingSourceFilesForWorkflowVersion() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsOpenApi = new io.dockstore.openapi.client.api.WorkflowsApi(openAPIWebClient);

        // Sourcefiles for workflowversions
        Workflow workflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "", "cwl", "/test.json");
        workflow = workflowsApi.refresh(workflow.getId(), false);

        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion1 -> workflowVersion1.getName().equals("testCWL")).findFirst().get();

        List<io.dockstore.openapi.client.model.SourceFile> sourceFiles = workflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), null);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());

        // Check that filtering works
        List<String> fileTypes = new ArrayList<>();
        fileTypes.add(DescriptorLanguage.FileType.DOCKSTORE_CWL.toString());
        sourceFiles = workflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());

        fileTypes.clear();
        fileTypes.add(DescriptorLanguage.FileType.DOCKSTORE_WDL.toString());
        sourceFiles = workflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(0, sourceFiles.size());

        // Check that you can't retrieve a version's sourcefiles if it doesn't belong to the workflow
        Workflow workflow2 = workflowsApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                        "test", "cwl", null);
        workflow2 = workflowsApi.refresh(workflow2.getId(), false);
        WorkflowVersion workflow2Version = workflow2.getWorkflowVersions().get(0);
        try {
            sourceFiles = workflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflow2Version.getId(), null);
            fail("Should not be able to grab sourcefile for a version not belonging to a workflow");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals("Version " + workflow2Version.getId() + " does not exist for this entry", ex.getMessage());
        }

        // check that sourcefiles can't be viewed by another user if entry isn't published
        final io.dockstore.openapi.client.ApiClient user1OpenAPIWebClient = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi user1WorkflowsOpenApi = new io.dockstore.openapi.client.api.WorkflowsApi(user1OpenAPIWebClient);
        try {
            sourceFiles = user1WorkflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), null);
            fail("Should not be able to grab sourcefiles if not published and doesn't belong to user.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals("Forbidden: you do not have the credentials required to access this entry.", ex.getMessage());
        }

        // sourcefiles can be viewed by others once published
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        sourceFiles = user1WorkflowsOpenApi.getWorkflowVersionsSourcefiles(workflow.getId(), workflowVersion.getId(), null);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());
    }

    /**
     * This tests that you can get all workflows by path (ignores workflow name)
     */
    @Test
    public void testGetAllWorkflowByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        String path = "github.com/DockstoreTestUser2/nested-wdl";

        io.dockstore.openapi.client.model.Workflow workflow1 = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
                "/Dockstore.wdl", "workflow1", "wdl", "/test.json");
        assertEquals(path, workflow1.getPath());

        io.dockstore.openapi.client.model.Workflow workflow2 = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
                "/Dockstore.wdl", "workflow2", "wdl", "/test.json");
        assertEquals(path, workflow2.getPath());

        List<io.dockstore.openapi.client.model.Workflow> foundWorkflows = workflowsApi.getAllWorkflowByPath(path);
        assertEquals(2, foundWorkflows.size());
    }

    /**
     * This tests that you can get a workflows by full workflow path
     */
    @Test
    public void testGetWorkflowByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        // Find a workflow with no workflow name
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
                "/Dockstore.wdl", null, "wdl", "/test.json");
        assertEquals("github.com/DockstoreTestUser2/nested-wdl", workflow.getFullWorkflowPath());
        io.dockstore.openapi.client.model.Workflow foundWorkflow = workflowsApi.getWorkflowByPath(workflow.getFullWorkflowPath(), WorkflowSubClass.BIOWORKFLOW, "");
        assertEquals(workflow.getId(), foundWorkflow.getId());

        // Find a workflow with a workflow name
        workflow = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
                "/Dockstore.wdl", "foo", "wdl", "/test.json");
        assertEquals("github.com/DockstoreTestUser2/nested-wdl/foo", workflow.getFullWorkflowPath());
        foundWorkflow = workflowsApi.getWorkflowByPath(workflow.getFullWorkflowPath(), WorkflowSubClass.BIOWORKFLOW, "");
        assertEquals(workflow.getId(), foundWorkflow.getId());
    }

    /**
     * We need an EntryVersionHelper instance so we can call EntryVersionHelper.writeStreamAsZip; getDAO never gets invoked.
     */
    private static class EntryVersionHelperImpl implements EntryVersionHelper {

        @Override
        public EntryDAO getDAO() {
            return null;
        }
    }
}


