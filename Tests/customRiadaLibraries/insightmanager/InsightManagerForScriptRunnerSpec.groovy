package customRiadaLibraries.insightmanager

import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.Specification

@WithPlugin("com.riadalabs.jira.plugins.insight")



String hostURI  = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser

Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)

JUnitCore jUnitCore = new JUnitCore()
//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, "Verify group gets added on first logout"))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecifications)


spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())



class InsightManagerForScriptRunnerSpecifications extends Specification {

    Logger log = Logger.getLogger(this.class)


    def setup() {

        log.setLevel(Level.ALL)
        log.debug("SETUP")


    }


    def "Retrieve object attachments"() {

        setup:

        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.log.setLevel(Level.ALL)
        ObjectBean testObject
        Map<String, File>attachments


        when:
        testObject = im.getObjectBean("TAS-6590")
        attachments = im.getObjectAttachments(testObject)



        then:
        attachments.size() > 0
        attachments.findAll {!it.value instanceof File}.isEmpty()
        attachments.every {it.value.canRead()}
        attachments.every {it.value.bytes.size() > 0}





    }






}




