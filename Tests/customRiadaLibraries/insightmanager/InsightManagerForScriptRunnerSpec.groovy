package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.AttachmentBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.Specification
import org.junit.runner.Request
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean

/**
 * If script fails due to "unable to resolve class customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean"
 *  First run this in the SR console:

 import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner

 customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean simplifiedAttachmentBean

 log.warn("hello")
 */


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
    String jiraDataPath = ComponentAccessor.getComponentOfType(JiraHome).getDataDirectory().path


    def setup() {

        log.setLevel(Level.ALL)



    }




    def 'Test attachment operations'(String sourceFilePath, boolean testDeletionOfSource, String attachmentComment) {

        setup:
        String destinationPath = System.getProperty("java.io.tmpdir") + "/" + this.class.simpleName
        log.info("Testing attachment operations")
        log.debug("\tUsing sourceFile:" + sourceFilePath)
        log.debug("\tFile will be temporarily placed here:" + destinationPath)


        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.log.setLevel(Level.WARN)
        ObjectBean testObject = im.getObjectBean("TAS-6590")

        String expectedAttachmentPath = jiraDataPath + "/attachments/insight/object/${testObject.id}/"

        log.debug("\tDownloading source file")
        File testFile = downloadFile(sourceFilePath, destinationPath)
        String testFileHash = testFile.getBytes().sha256()
        log.debug("\tDownload complete: ${testFile.path}, size: " + testFile.size())

        when:
        log.debug("\tTesting attaching file to $testObject")

        SimplifiedAttachmentBean newAttachmentBean = im.addObjectAttachment(testObject, testFile, attachmentComment, testDeletionOfSource)
        expectedAttachmentPath += newAttachmentBean.attachmentBean.nameInFileSystem


        then:
        newAttachmentBean != null
        newAttachmentBean.isValid()
        assert new File(expectedAttachmentPath).canRead(): "The attached file wasn't found at the expected path:" + expectedAttachmentPath

        if (testDeletionOfSource) {
            assert !testFile.canRead(): "Source file wasn't deleted by IM when expected"
        } else {
            assert testFile.canRead(): "Source file was unintentionally deleted by IM"
        }

        log.trace("\t\tA new attachmentBean was created")

        when:
        log.debug("\tTesting getObjectAttachments() to find and verify the new attachment")
        ArrayList<SimplifiedAttachmentBean> objectAttachments = im.getAllObjectAttachmentBeans(testObject)
        SimplifiedAttachmentBean retrievedSimplifiedAttachment = objectAttachments.find { it.id == newAttachmentBean.id }

        then:
        assert retrievedSimplifiedAttachment.originalFileName == testFile.name: "The name of the test file and the retrieved attachment file doesn't match"
        assert retrievedSimplifiedAttachment.attachmentFile.getBytes().sha256() == testFileHash: "The hash of the test file and the retrieved attachment file doesn't match"
        assert newAttachmentBean.attachmentBean.comment == retrievedSimplifiedAttachment.attachmentBean.comment : "The comment of the SimplifiedAttachmentBean differs"
        log.trace("\t\tThe new attachment was successfully verified")

        when:
        log.debug("\tTesting deleteObjectAttachment() by deleting the new attachment")
        boolean deletionResult = im.deleteObjectAttachment(newAttachmentBean)

        then:
        assert deletionResult : "deleteObjectAttachment was unsuccessful and returned false"
        assert im.objectFacade.loadAttachmentBeanById(newAttachmentBean.id) == null
        assert !new File(expectedAttachmentPath).canRead(): "The attached file can still be found at the expected path:" + expectedAttachmentPath
        log.trace("\t\tThe attachment was successfully deleted")

        cleanup:
        if (!testDeletionOfSource) {
            log.debug("Deleting test file from filesystem:" + testFile.path)
            assert testFile.delete(): "Error deleting test file:" + testFile.path
            log.debug("\tThe test file was successfully deleted")
        }




        where:
        sourceFilePath                                                                       | testDeletionOfSource | attachmentComment
        "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png" | false                | "no comment"
        "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png" | true                 | "no comment"
        "https://www.atlassian.com/dam/jcr:242ae640-3d6a-472d-803d-45d8dcc2a8d2/Atlassian-horizontal-blue-rgb.svg" | true | " a comment"
        "https://bitbucket.org/atlassian/jira_docs/downloads/JIRACORESERVER_8.10.pdf"                              | false | "another commenct"

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




