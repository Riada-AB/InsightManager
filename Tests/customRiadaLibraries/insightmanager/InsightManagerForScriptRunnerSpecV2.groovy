package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.permission.GlobalPermissionKey
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.security.GlobalPermissionManager
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.security.roles.ProjectRole
import com.atlassian.jira.security.roles.ProjectRoleManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.util.BaseUrl
import com.onresolve.scriptrunner.canned.jira.utils.servicedesk.ServiceDeskUtils
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.IQLFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.InsightPermissionFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.ObjectSchemaFacadeImpl
import com.riadalabs.jira.plugins.insight.services.core.impl.ServiceDeskService
import com.riadalabs.jira.plugins.insight.services.model.ObjectSchemaBean
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import jline.internal.InputStreamReader
import org.apache.groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.config.ConfigurationException
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.TimeoutException

@WithPlugin("com.riadalabs.jira.plugins.insight")




Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)


/*
setupTestEnvironment(jiraAdminUsername, jiraAdminPassword)


static void setupTestEnvironment(String jiraAdminUsername, String jiraAdminPassword) {

    SpecHelper specHelper = new SpecHelper(jiraAdminUsername, jiraAdminPassword)
    specHelper.setupGoldenObjectSchema("Testing 1", "testar1")

    /**
     * Manual Steps:
     *  Create a JSD project, use the "Basic" template
     */
//}


/*
JUnitCore jUnitCore = new JUnitCore()

//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, 'Test readOnly mode of attachment operations'))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecificationsV2)



spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())
*/

SpecHelper specHelper = new SpecHelper()
specHelper.validateAndCacheSettings()
specHelper.getObjectSchemaRoles(9)


class InsightManagerForScriptRunnerSpecificationsV2 extends Specification {


    /*
    @Shared
    String jiraAdminUserName = "anders"
    @Shared
    String jiraAdminPassword = "anders"

     */


    @Shared
    Class iqlFacadeClass
    @Shared
    IQLFacadeImpl iqlFacade
    @Shared
    ObjectSchemaFacadeImpl objectSchemaFacade
    @Shared
    Class objectSchemaFacadeClass

    @Shared
    UserManager userManager
    @Shared
    JiraAuthenticationContext jiraAuthenticationContext


    @Shared
    Logger log = Logger.getLogger(this.class)
    /*
    @Shared
    File jiraHome = ComponentAccessor.getComponentOfType(JiraHome).getHome()
    @Shared
    String jiraBaseUrl = ComponentAccessor.getComponentOfType(BaseUrl).getCanonicalBaseUrl()
    @Shared
    ApplicationUser userRunningTheScript
    @Shared
    ApplicationUser jiraAdminUser

     */

    @Shared
    SpecHelper specHelper = new SpecHelper()


    def setupSpec() {

        log.setLevel(Level.ALL)
        objectSchemaFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectSchemaFacade")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");

        objectSchemaFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectSchemaFacadeClass) as ObjectSchemaFacadeImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl

        userManager = ComponentAccessor.getUserManager()
        jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()

        assert specHelper.validateAndCacheSettings()


        if (specHelper.userRunningTheScript == specHelper.jiraAdminUser) {
            log.warn("The jiraAdmin and the user running this script should ideally not be the same.")
        }
    }


    def "Verify that setServiceAccount() finds the user regardless of input type"() {


        when: "Testing with username"
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.setServiceAccount(specHelper.jiraAdminUser.username)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == specHelper.userRunningTheScript
        im.serviceUser == specHelper.jiraAdminUser

        log.info("setServiceAccount when supplied with a username works as intended")


        when: "Testing with applicationUser"
        im = new InsightManagerForScriptrunner()
        im.setServiceAccount(specHelper.jiraAdminUser)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == specHelper.userRunningTheScript
        im.serviceUser == specHelper.jiraAdminUser

        log.info("setServiceAccount when supplied with a applicationUser works as intended")


    }


    def "Verify IQL searching"(String iql, long matchSize, ApplicationUser user) {
        //def "Verify IQL searching"() {

        setup:
        ObjectSchemaBean objectSchema = specHelper.setupGoldenObjectSchema()

        when:
        int i = 1

        then:
        i == 1


        where:
        iql                                           | matchSize | user
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.jiraAdminUser
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.insightSchemaManager
        "ObjectType = \"Object With All Attributes\"" | 2         | specHelper.insightSchemaUser


    }


}


class SpecHelper {

    Logger log = Logger.getLogger(this.class)
    Class iqlFacadeClass
    IQLFacadeImpl iqlFacade
    ObjectSchemaFacadeImpl objectSchemaFacade
    Class objectSchemaFacadeClass
    Class insightPermissionFacadeClass
    InsightPermissionFacadeImpl insightPermissionFacade


    UserManager userManager = ComponentAccessor.getUserManager()
    ProjectManager projectManager = ComponentAccessor.getProjectManager()
    ProjectRoleManager projectRoleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager)
    GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager()


    File jiraHome = ComponentAccessor.getComponentOfType(JiraHome).getHome()
    String jiraBaseUrl = ComponentAccessor.getComponentOfType(BaseUrl).getCanonicalBaseUrl()

    File settingsFile = new File(System.getProperty("java.io.tmpdir") + "/" + super.class.simpleName + "/settings.json")
    Map settings


    ApplicationUser jiraAdminUser
    String jiraAdminPassword
    ApplicationUser userRunningTheScript
    ApplicationUser insightSchemaUser
    ApplicationUser insightSchemaManager
    ApplicationUser projectAdmin
    ApplicationUser projectCustomer

    Project jsdProject
    ObjectSchemaBean objectSchemaBean

    SpecHelper() {
        this.log.setLevel(Level.ALL)

        objectSchemaFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectSchemaFacade")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");
        insightPermissionFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.InsightPermissionFacade");

        objectSchemaFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectSchemaFacadeClass) as ObjectSchemaFacadeImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl
        insightPermissionFacade = ComponentAccessor.getOSGiComponentInstanceOfType(insightPermissionFacadeClass) as InsightPermissionFacadeImpl


        userRunningTheScript = ComponentAccessor.getJiraAuthenticationContext().loggedInUser


        Map defaultSettings = [

                jiraGlobal: [
                        adminUsername: "admin",
                        adminPassword: "password"
                ],
                insight   : [
                        objectSchemaId: 999,
                        schemaManager : "UserName",
                        schemaUser    : "SchemaUser"
                ],
                jsdProject: [
                        key            : "KEY",
                        projectAdmin   : "UserName",
                        projectCustomer: "CustomerName"
                ]
        ]
        boolean fileCreated = settingsFile.createNewFile()

        if (fileCreated) {


            settingsFile.write(JsonOutput.prettyPrint(JsonOutput.toJson(defaultSettings)))
            log.info("A new settings file was created:" + settingsFile.path)
            settings = new JsonSlurper().parse(settingsFile) as Map

            log.info("Will now stop execution to allow user setup of settingsFile:" + settingsFile.canonicalPath)
            throw new ConfigurationException("Setup the newly created settingsFile:" + settingsFile.canonicalPath)
        } else {
            settings = new JsonSlurper().parse(settingsFile) as Map

            if (settings == null || settings.isEmpty()) {
                throw new InputMismatchException("Could not parse settings file")
            }

            if (!settings.keySet().containsAll(defaultSettings.keySet())) {

                throw new InputMismatchException("Settings file is missing keys, it should contain:" + defaultSettings.keySet())
            }

        }


    }


    boolean validateAndCacheSettings() {

        log.info("Checking if settings are valid")
        log.info("Settings file:" + settingsFile.canonicalPath)
        log.info("The settings are:" + settings.toPrettyJsonString())

        //Check the supplied project
        jsdProject = projectManager.getProjectByCurrentKey(settings.jsdProject.key)
        assert jsdProject != null: "Could not find project with key: " + settings.jsdProject.key
        assert jsdProject.projectTypeKey.key == "service_desk": "The supplied project is not a service desk project."

        //Check the supplied projectAdmin
        projectAdmin = userManager.getUserByName(settings.jsdProject.projectAdmin)
        assert projectAdmin != "Could not find the supplied project admin: " + settings.jsdProject.projectAdmin
        assert projectRoleManager.isUserInProjectRole(projectAdmin, projectRoleManager.getProjectRole("Administrators"), jsdProject): "The supplied project admin ($projectAdmin) is not admin of the project" + jsdProject.name

        //Chech the supplied JSD Customer
        projectCustomer = userManager.getUserByName(settings.jsdProject.projectCustomer)
        assert projectRoleManager.getProjectRoles(projectCustomer, jsdProject).isEmpty(): "The JSD customer should not have any project roles in the JSD project:" + projectCustomer


        //Check the supplied JIRA admin
        jiraAdminUser = userManager.getUserByName(settings.jiraGlobal.adminUsername)
        jiraAdminPassword = settings.jiraGlobal.adminPassword
        assert jiraAdminUser != null: "Could not find adminUser:" + settings.jiraGlobal.adminUsername
        assert jiraAdminPassword != null: "Jira adminPassword not supplied"
        assert insightPermissionFacade.hasAdminPermission(jiraAdminUser): "The jiraGlobal.adminUsername is not Insight Admin"
        assert globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, jiraAdminUser): "The supplied JIRA global admin, is not admin"

        //Check the supplied Insight Schema
        objectSchemaBean = objectSchemaFacade.loadObjectSchema(settings.insight.objectSchemaId)
        assert objectSchemaBean != null: "Could not find ObjectSchema with ID:" + settings.insight.objectSchemaId

        //Check the supplied schema manager
        insightSchemaManager = userManager.getUserByName(settings.insight.schemaManager)
        assert insightSchemaManager != null: "Could not find schema manager:" + settings.insight.schemaManager
        assert insightPermissionFacade.hasInsightSchemaManagerPermission(insightSchemaManager, objectSchemaBean.id): "Theinsight.schemaManager should have Admin permissions in Insight scheme " + objectSchemaBean.name

        //Check the supplied schemaUser
        insightSchemaUser = userManager.getUserByName(settings.insight.schemaUser)
        assert insightSchemaUser != null: "Could not find schema user:" + settings.insight.schemaUser
        assert !insightPermissionFacade.hasAdminPermission(insightSchemaUser): "The insight.schemaUser should not have Admin permissions in Insight"
        assert insightPermissionFacade.hasInsightObjectSchemaViewPermission(insightSchemaUser, objectSchemaBean.id): "The insight.schemaUser should have user permissions in Insight scheme " + objectSchemaBean.name


        log.info("The settings file appears valid and has been cached")

        return true

    }

    File createNewGoldenSchemaImage(int schemaId, String zipFileName = "SPOC-golden-image.zip") {

        log.info("Creating new Golden Schema Image")

        File exportedFile = exportScheme(zipFileName, schemaId, true)

        assert exportedFile.exists(): "Failed to create export file:" + exportedFile.path
        assert exportedFile.size() > 0: "Failed to create export file:" + exportedFile.path

        log.info("\tThe image was created successfully and placed here:" + exportedFile.canonicalPath)
        log.info("\tNow manually upload it to:" + "https://github.com/Riada-AB/InsightManager-TestingResources/blob/master/SPOC-golden-image.zip")


        return exportedFile

    }

    ObjectSchemaBean setupGoldenObjectSchema(String schemaName = "SPOC Testing of IM", String schemaKey = "SPIM") {

        String imageUrl = "https://github.com/Riada-AB/InsightManager-TestingResources/raw/master/SPOC-golden-image.zip"

        log.info("Setting up new Golden ObjectSchema")
        log.info("Download Schema zip from:" + imageUrl)


        File destinationFolder = new File(System.getProperty("java.io.tmpdir") + "/" + super.class.simpleName)
        destinationFolder.mkdirs()

        log.info("Downloading to:" + destinationFolder.canonicalPath)

        File imageZip = downloadFile(imageUrl, destinationFolder.canonicalPath, jiraAdminUser.username, jiraAdminPassword)

        log.info("Download complete, moving Insight import folder")
        imageZip = moveFile(imageZip.canonicalPath, jiraHome.canonicalPath + "/import/insight/" + imageZip.name)
        log.debug("\tFile moved to " + imageZip.canonicalPath)

        log.info("Importing ZIP in to the new schema \"$schemaName\" ($schemaKey)")
        ObjectSchemaBean newSchema = importScheme(imageZip.name, schemaName, schemaKey)

        log.info("The new schema got id:" + newSchema.id)
        return newSchema
    }


    File downloadFile(String url, String destinationFolder, String username = "", String password = "") {

        HttpURLConnection connection = setupConnection(url, username, password)


        try {

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                String fileName = "";
                String disposition = connection.getHeaderField("Content-Disposition");
                String contentType = connection.getContentType();
                int contentLength = connection.getContentLength();

                if (disposition != null) {
                    // extracts file name from header field
                    int index = disposition.indexOf("filename=");
                    if (index > 0) {
                        fileName = disposition.substring(index + 10,
                                disposition.length() - 1);
                    }
                } else {
                    // extracts file name from URL
                    fileName = url.substring(url.lastIndexOf("/") + 1,
                            url.length());
                }

                log.debug("Content-Type = " + contentType);
                log.debug("Content-Disposition = " + disposition);
                log.debug("Content-Length = " + contentLength);
                log.debug("fileName = " + fileName);

                // opens input stream from the HTTP connection
                InputStream inputStream = connection.getInputStream();
                String saveFilePath = destinationFolder + File.separator + fileName;

                // opens an output stream to save into file
                FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                File downloadedFile = new File(saveFilePath)

                if (downloadedFile.size() > 0) {
                    log.debug("File downloaded successfully: " + downloadedFile.canonicalPath)
                    return downloadedFile
                } else {
                    log.error("Failed to download file:" + url)
                    return null
                }


            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" + connection.URL.toString())
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }


    }


    static HttpURLConnection setupConnection(String url, String username = "", String password = "") {

        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection

        if (username != "" && password != "") {
            String auth = username + ":" + password
            auth = "Basic " + auth.bytes.encodeBase64().toString()

            connection.setRequestProperty("Authorization", auth)
        }


        return connection

    }

    LazyMap httpPostJson(LazyMap json, String url, String username = "", String password = "") {

        HttpURLConnection connection = setupConnection(url, username, password)


        try {
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            byte[] jsonByte = new JsonBuilder(json).toPrettyString().getBytes("UTF-8")

            connection.outputStream.write(jsonByte, 0, jsonByte.length)

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                def rawReturn = new JsonSlurper().parse(connection.getInputStream())
                LazyMap outJson
                if (rawReturn instanceof ArrayList) {
                    outJson = ["data": rawReturn] as LazyMap
                } else {
                    outJson = rawReturn as LazyMap
                }


                return outJson

            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" + connection.URL.toString())
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }
    }


    LazyMap httpGetJson(String url, String username = "", String password = "") {


        HttpURLConnection connection = setupConnection(url, username, password)
        try {

            connection.setRequestMethod("GET")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {

                LazyMap json = new JsonSlurper().parse(connection.getInputStream()) as LazyMap

                return json

            } else {
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + url)
            }
        } catch (all) {

            log.error("There was an error in the sendGetJson method")
            log.error("\tUrl:" + url)
            if (connection != null) {
                log.error("\tResponse code:" + connection.responseCode)
                log.error("\tResponse Message:" + connection.responseMessage)
                log.error("\tErrorStream:" + readErrorStream(connection))
            }
            log.error("\tException:" + all.message)

            throw all


        }
    }


    //File is expected to be placed in $JIRAHOME/import/insight
    ObjectSchemaBean importScheme(String fileName, String objectSchemaName, String objectSchemaKey, String objectSchemaDescription = "", boolean includeObjects = true, boolean importAttachments = true, boolean importObjectAvatars = true) {

        log.info("Import Scheme")
        log.info("\tFilename" + fileName)
        log.info("\tSchema Name" + objectSchemaName)

        LazyMap inputJson = [
                fileName               : fileName,
                objectSchemaName       : objectSchemaName,
                objectSchemaKey        : objectSchemaKey,
                objectSchemaDescription: objectSchemaDescription,
                includeObjects         : includeObjects,
                importAttachments      : importAttachments,
                importObjectAvatars    : importObjectAvatars
        ]

        log.debug("Sending JSON to Insight:")
        inputJson.each {
            log.debug(it.key + ":" + it.value)
        }

        LazyMap result = httpPostJson(inputJson, jiraBaseUrl + "/rest/insight/1.0/objectschemaimport/import/server", jiraAdminUser.username, jiraAdminPassword)

        log.debug("Got response JSON from Insight:")
        result.each {
            log.debug(it.key + ":" + it.value)
        }

        return objectSchemaFacade.loadObjectSchema(result.resultData.objectSchemaId)

    }


    //File is placed in $JIRAHOME/export/insight
    File exportScheme(String fileName, int SchemaId, boolean includeObjects) {

        long maxWaitForExportMs = 60000
        log.info("Exporting Scheme")
        log.info("\tFilename:" + fileName)
        log.info("\tSchema ID" + SchemaId)
        log.info("\tWill include objects:" + includeObjects)


        ObjectSchemaBean objectSchema = objectSchemaFacade.loadObjectSchema(SchemaId)
        int totalObjectsInExport = iqlFacade.findObjects(objectSchema.id, "ObjectSchemaId = " + objectSchema.id).size()

        LazyMap json = [
                fileName            : fileName,
                objectSchemaId      : objectSchema.id,
                includeObjects      : includeObjects,
                objectSchemaName    : objectSchema.name,
                totalObjectsInExport: totalObjectsInExport

        ]

        log.debug("Sending JSON to Insight:")
        json.each {
            log.debug(it.key + ":" + it.value)
        }

        LazyMap result = httpPostJson(json, this.jiraBaseUrl + "/rest/insight/1.0/objectschemaexport/export/server", jiraAdminUser.username, jiraAdminPassword)

        log.debug("Got response JSON from Insight:")
        result.each {
            log.debug(it.key + ":" + it.value)
        }

        String actualFileName = result.resultData.exportFileName

        log.info("The exported file will be named:" + actualFileName)


        Instant startOfImport = new Date().toInstant()
        File exportedFile = new File(jiraHome.path + "/export/insight/" + actualFileName)
        long fileSize = 0

        while ((new Date().toInstant().toEpochMilli() - startOfImport.toEpochMilli()) <= maxWaitForExportMs) {

            if (!exportedFile.exists() || exportedFile.size() == 0) {
                sleep(100)
            } else if (fileSize == exportedFile.size()) {

                return exportedFile
            } else {

                fileSize = exportedFile.size()
                sleep(100)

            }


        }

        throw new TimeoutException("Timed out waiting for export to finish, gave up after:" + (new Date().toInstant().toEpochMilli() - startOfImport.toEpochMilli()) + " ms")


    }


    Map getObjectSchemaRoles(long schemaId) {

        LazyMap httpResult = httpGetJson(this.jiraBaseUrl + "/rest/insight/1.0/config/role/objectschema/" + schemaId, jiraAdminUser.username, jiraAdminPassword)

        Map returnMap = [:]

        httpResult.each {
            returnMap.put(it.key, it.value.find("\\d+\$"))
        }
        log.info("Http Result:" + returnMap)
    }


    static File moveFile(String source, String destination) {

        Path sourcePath = new File(source).toPath()
        Path destinationPath = new File(destination).toPath()
        return Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING).toFile()
    }

    private static String readErrorStream(HttpURLConnection connection) {

        String returnString = ""
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))
        bufferedReader.eachLine {
            returnString += it
        }

        return returnString

    }
}

