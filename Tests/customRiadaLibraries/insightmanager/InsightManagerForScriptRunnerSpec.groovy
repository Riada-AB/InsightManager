package customRiadaLibraries.insightmanager

import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.AttachmentBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.Specification
import org.junit.runner.Request

@WithPlugin("com.riadalabs.jira.plugins.insight")



String hostURI = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser

Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)

JUnitCore jUnitCore = new JUnitCore()
Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, "Test attachment operations"))
//Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecifications)


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
        im.log.setLevel(Level.WARN)
        ObjectBean testObject
        Map<String, File> attachments


        when:
        testObject = im.getObjectBean("TAS-6590")
        attachments = im.getAllObjectAttachmentBeans(testObject)


        then:
        attachments.size() > 0
        attachments.findAll { !it.value instanceof File }.isEmpty()
        attachments.every { it.value.canRead() }
        attachments.every { it.value.bytes.size() > 0 }


    }

    def 'Test attachment operations'(String sourceFilePath) {

        setup:
        String destinationPath = System.getProperty("java.io.tmpdir") +"/" + this.class.simpleName
        log.info("Testing attachment operations")
        log.debug("\tUsing sourceFile:" + sourceFilePath)
        log.debug("\tFile will be temporarily placed here:" + destinationPath)


        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.log.setLevel(Level.WARN)
        ObjectBean testObject = im.getObjectBean("TAS-6590")

        when:
        log.debug("\tTesting attaching file to object")
        log.debug("\tDownloading source file")
        File testFile = downloadFile(sourceFilePath,  destinationPath)
        log.debug("\tDownload complete: ${testFile.path}, size: " + testFile.size())

        log.debug("\tAttaching file to $testObject")

        AttachmentBean newAttachmentBean = im.addObjectAttachment(testObject, testFile, "", false)




        then:
        newAttachmentBean != null
        log.trace("\t\tA new attachmentBean was created")

        when:
        log.debug("\tTesting getObjectAttachments() to find and verify the new attachment")
        Map<String, File> objectAttachments = im.getAllObjectAttachmentBeans(testObject)
        File retrievedAttachmentFile = im.getAllObjectAttachmentBeans(testObject).find {it.value.name == testFile.name}.value

        then:
        assert retrievedAttachmentFile.name == testFile.name : "The name of the test file and the retrieved attachment file doesn't match"
        assert retrievedAttachmentFile.getBytes().sha256() == testFile.getBytes().sha256() : "The hash of the test file and the retrieved attachment file doesn't match"
        log.trace("\t\tThe new attachment was successfully verified")

        cleanup:
        log.debug("Deleting test file from filesystem:" + testFile.path)
        assert testFile.delete() : "Error deleting test file:" + testFile.path


        where:
        sourceFilePath                                                                                             | _
        "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png"                       | _
        //"https://www.atlassian.com/dam/jcr:242ae640-3d6a-472d-803d-45d8dcc2a8d2/Atlassian-horizontal-blue-rgb.svg" | _
        //"https://bitbucket.org/atlassian/jira_docs/downloads/JIRACORESERVER_8.10.pdf"                              | _

    }


    private static File downloadFile(String remoteUrl, String destinationDirectory) {
        String fileName = remoteUrl.substring(remoteUrl.lastIndexOf("/") + 1, remoteUrl.length())


        if (destinationDirectory[-1] != "/") {

            destinationDirectory += "/"
        }

        new File(destinationDirectory).mkdirs()
        File newFile = new File(destinationDirectory + fileName)

        newFile.withOutputStream { out ->
            new URL(remoteUrl).withInputStream { from ->
                out << from
            }
        }

        return newFile
    }


}




