package customRiadaLibraries.insightmanager

import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.Specification




String hostURI  = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser

Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)



clearCodeCache(hostURI, restUser, restPw)
JUnitCore jUnitCore = new JUnitCore()
//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, "Verify group gets added on first logout"))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecifications)


spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())



class InsightManagerForScriptRunnerSpecifications extends Specification {

    Logger log = Logger.getLogger(this.class)



    /*
    def setup() {

        throw new InputMismatchException("HEEEJ")
        log.setLevel(Level.ALL)
        //log.warn("Setup")



        //clearCodeCache()


    }*/


    def "Retrieve object attachments"() {

        setup:
        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        ObjectBean testObject = im.getObjectBean("TAS-6590")





    }






}


void clearCodeCache(String hostURI, String restUser, String restPw) {

    HttpURLConnection cacheClearConnection = new URL(hostURI + "/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches").openConnection() as HttpURLConnection
    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    cacheClearConnection.setRequestProperty("Authorization", auth)
    cacheClearConnection.setDoOutput(true)
    cacheClearConnection.setRequestMethod("POST")
    cacheClearConnection.setRequestProperty("Content-Type", "application/json")
    cacheClearConnection.setRequestProperty("Accept", "application/json")
    byte[] jsonByte = new JsonBuilder(["FIELD_WHICH_CACHE": "gcl", "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches"]).toPrettyString().getBytes("UTF-8")
    cacheClearConnection.outputStream.write(jsonByte, 0, jsonByte.length)
    LazyMap rawReturn = new JsonSlurper().parse(cacheClearConnection.getInputStream())
    log.setLevel(Level.ALL)
    log.debug("Cache clear outpup:" + rawReturn.output)


}


