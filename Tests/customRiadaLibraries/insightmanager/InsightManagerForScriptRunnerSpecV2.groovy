package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.IQLFacadeImpl
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.ObjectSchemaFacadeImpl
import com.riadalabs.jira.plugins.insight.services.model.MutableObjectSchemaBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectSchemaBean
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import io.riada.insight.api.graphql.resolvers.objectschema.ObjectSchema
import jline.internal.InputStreamReader
import org.apache.groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.runner.Result
@WithPlugin("com.riadalabs.jira.plugins.insight")




String hostURI = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser

Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)


//InsightManagerForScriptRunnerSpecificationsV2 spec = new InsightManagerForScriptRunnerSpecificationsV2()

//spec.exportScheme("export.zip", 1, true)

//spec.importScheme("export.zip", "Testing Import 3", "TI3", "Testing Import description", true, true, true)



JUnitCore jUnitCore = new JUnitCore()

//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, 'Test readOnly mode of attachment operations'))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecificationsV2)



spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())

class InsightManagerForScriptRunnerSpecificationsV2 extends Specification{

    @Shared String jiraUrlString = "http://jirascania1.stuxnet.se"
    @Shared String jiraAdminUserName = "anders"
    @Shared String jiraAdminPassword = "anders"


    @Shared Class iqlFacadeClass
    @Shared IQLFacadeImpl iqlFacade
    @Shared ObjectSchemaFacadeImpl objectSchemaFacade
    @Shared Class objectSchemaFacadeClass

    @Shared UserManager userManager
    @Shared JiraAuthenticationContext jiraAuthenticationContext


    @Shared Logger log = Logger.getLogger(this.class)
    @Shared String jiraDataPath = ComponentAccessor.getComponentOfType(JiraHome).getDataDirectory().path
    @Shared ApplicationUser userRunningTheScript
    @Shared ApplicationUser jiraAdminUser


    def setupSpec() {

        log.setLevel(Level.ALL)
        objectSchemaFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectSchemaFacade")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");

        objectSchemaFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectSchemaFacadeClass) as ObjectSchemaFacadeImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl

        userManager = ComponentAccessor.getUserManager()
        jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()

        userRunningTheScript = jiraAuthenticationContext.getLoggedInUser()
        jiraAdminUser = userManager.getUserByName(jiraAdminUserName)


        if(userRunningTheScript == jiraAdminUser) {
            log.warn("The JiraAdminUsername and the user running this script should ideally not be the same.")
        }
    }




    def "Verify that setServiceAccount() finds the user regardless of input type"() {


        when: "Testing with username"
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.setServiceAccount(jiraAdminUser.username)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == userRunningTheScript
        im.serviceUser == jiraAdminUser

        log.info("setServiceAccount when supplied with a username works as intended")


        when:"Testing with applicationUser"
        im = new InsightManagerForScriptrunner()
        im.setServiceAccount(jiraAdminUser)

        then: "Checking that im.initialUser and im.serviceUser was set correctly"

        im.initialUser == userRunningTheScript
        im.serviceUser == jiraAdminUser

        log.info("setServiceAccount when supplied with a applicationUser works as intended")


    }




    HttpURLConnection setupConnection(String url) {

        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection

        String auth = jiraAdminUser + ":" + jiraAdminPassword
        auth = "Basic " + auth.bytes.encodeBase64().toString()

        connection.setRequestProperty("Authorization", auth)

        return connection

    }

    LazyMap httpPostJson(LazyMap json, String url) {

        HttpURLConnection connection = setupConnection(url)


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
                throw new ConnectException("Unsupported response (" + connection.responseCode + "(${connection.responseMessage})) from " + this.jiraUrlString)
            }
        } catch (all) {

            log.error("There was an error in the exportScheme method")
            log.error("\tUrl:" +connection.URL.toString())
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
    void importScheme(String fileName, String objectSchemaName, String objectSchemaKey, String objectSchemaDescription, boolean includeObjects, boolean importAttachments, boolean importObjectAvatars) {

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

        LazyMap result = httpPostJson(inputJson, this.jiraUrlString + "/rest/insight/1.0/objectschemaimport/import/server")

        log.debug("Got response JSON from Insight:")
        result.each {
            log.debug(it.key + ":" + it.value)
        }


    }


    //File is placed in $JIRAHOME/export/insight
    void exportScheme(String fileName, int SchemaId, boolean includeObjects) {

        log.info("Exporting Scheme")
        log.info("\tFilename" + fileName)
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

        LazyMap result = httpPostJson(json, this.jiraUrlString + "/rest/insight/1.0/objectschemaexport/export/server")

        log.debug("Got response JSON from Insight:")
        result.each {
            log.debug(it.key + ":" + it.value)
        }

    }


    static void moveFile(String source, String destination) {

        Path sourcePath = new File(source).toPath()
        Path destinationPath = new File(destination).toPath()
        Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
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




