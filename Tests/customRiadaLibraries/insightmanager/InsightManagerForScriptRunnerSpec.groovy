package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import org.apache.commons.io.FileUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.Specification
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean

/**
 * If script fails due to "unable to resolve class customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean"
 *  First run this in the SR console:

 import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner

 customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean simplifiedAttachmentBean

 log.warn("hello")
 */


@WithPlugin("com.riadalabs.jira.plugins.insight")

//FIXME Add additional tests
// - Export and import files with same name

String hostURI = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser

Logger log = Logger.getLogger("test.report")
log.setLevel(Level.ALL)

JUnitCore jUnitCore = new JUnitCore()
//Result spockResult = jUnitCore.run(Request.method(InsightManagerForScriptRunnerSpecifications.class, 'Test readOnly mode of attachment operations'))
Result spockResult = jUnitCore.run(InsightManagerForScriptRunnerSpecifications)


spockResult.failures.each { log.error(it) }

spockResult.each { log.info("Result:" + it.toString()) }

log.info("Was successful:" + spockResult.wasSuccessful())


class InsightManagerForScriptRunnerSpecifications extends Specification {

    Logger log = Logger.getLogger(this.class)
    String jiraDataPath = ComponentAccessor.getComponentOfType(JiraHome).getDataDirectory().path


    def setup() {

        log.setLevel(Level.ALL)


    }


    def 'Test readOnly mode of attachment operations'() {

        setup:
        log.info("Testing readOnly mode of attachment operations")
        String sourceObjectKey = "TAS-6590" //An object that already has attachments

        //An object that we will test importing attachments to.
        // It´s preexisting attachments will be deleted
        //It should have a text attribute "Old Object key" with the value of $sourceObjectKey
        String destinationObjectKey = "TAD-6592"

        boolean okToDeleteExportPath = false
        String exportPath = System.getProperty("java.io.tmpdir") + "/" + this.class.simpleName + "/TestAttachmentReadOnly"
        assert !new File(exportPath).exists(): "Export directory already exists:" + exportPath
        okToDeleteExportPath = true

        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.log.setLevel(Level.ALL)

        ObjectBean destinationObject = im.getObjectBean(destinationObjectKey)

        log.debug("\tGetting source objects current attachment beans")
        ArrayList<SimplifiedAttachmentBean> sourceAttachmentBeans = im.getAllObjectAttachmentBeans(sourceObjectKey)
        ArrayList<File> sourceFiles = im.exportObjectAttachments(sourceObjectKey, exportPath)
        log.trace("\t" * 2 + "Source object has ${sourceAttachmentBeans.size()} attachments")
        assert sourceAttachmentBeans.size() > 0: "Source object doesn't have any attachments"

        log.debug("\tDeleting destination objects preexisting attachments")
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            im.deleteObjectAttachment(it)
        }
        ArrayList<SimplifiedAttachmentBean> destinationAttachmentBeans = []
        log.trace("\t" * 2 + "Done")


        when:
        log.debug("\tStarting test of addObjectAttachment() readOnly false")
        im.readOnly = false


        sourceFiles.each { sourceFile ->
            destinationAttachmentBeans.add(im.addObjectAttachment(destinationObject, sourceFile))

        }


        then:
        destinationAttachmentBeans.size() == sourceFiles.size()
        log.debug("\t" + "*" * 20 + " addObjectAttachment() readOnly false was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of addObjectAttachment() readOnly true")
        im.readOnly = false
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            im.deleteObjectAttachment(it)
        }
        im.readOnly = true
        destinationAttachmentBeans = []


        sourceFiles.each { sourceFile ->
            destinationAttachmentBeans.add(im.addObjectAttachment(destinationObject, sourceFile))

        }


        then:
        destinationAttachmentBeans.every { it == null }
        im.getAllObjectAttachmentBeans(destinationObject).size() == 0
        log.debug("\t" + "*" * 20 + " addObjectAttachment() readOnly true was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of deleteObjectAttachment() readOnly true")
        im.readOnly = false
        destinationAttachmentBeans = []
        log.trace("\t" * 2 + "Adding attachments to destination object")

        sourceFiles.each { sourceFile ->
            im.addObjectAttachment(destinationObject, sourceFile)

        }

        assert im.getAllObjectAttachmentBeans(destinationObject).every { it.isValid() }
        log.trace("\t" * 2 + "Verified that they got added correctly")


        log.debug("\tSwitching IM to read only mode and deleting destination objects attachments")
        im.readOnly = true
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            assert !im.deleteObjectAttachment(it)
        }
        destinationAttachmentBeans = im.getAllObjectAttachmentBeans(destinationObject)

        then:
        destinationAttachmentBeans.size() > 0
        destinationAttachmentBeans.every { it.valid }
        log.debug("\tThe attachments weren't deleted")
        log.debug("\t" + "*" * 20 + " deleteObjectAttachment() readOnly true was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of deleteObjectAttachment() readOnly false")
        im.readOnly = false
        assert im.getAllObjectAttachmentBeans(destinationObject).every { it.isValid() }

        log.debug("\t" * 2 + "Deleting all of destination objects attachments")
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            assert im.deleteObjectAttachment(it)
        }
        log.debug("\t" * 2 + "Getting all of destination object attachments")
        destinationAttachmentBeans = im.getAllObjectAttachmentBeans(destinationObject)

        then:
        assert destinationAttachmentBeans.size() == 0
        log.debug("\tThe attachments were deleted")
        log.debug("\t" + "*" * 20 + " deleteObjectAttachment() readOnly false was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of exportObjectAttachments() readOnly true")
        im.readOnly = true
        ArrayList<File> exportedFiles = im.exportObjectAttachments(sourceObjectKey, exportPath + "/export")

        then:
        assert exportedFiles.isEmpty(): "exportObjectAttachments() returned File objects even though in read only mode"
        File exportDirectory = new File(exportPath + "/export")
        if (exportDirectory.listFiles()?.size() != 0) {
            log.warn("\tThe export directory has files in it:")
            exportDirectory.listFiles().each{
                log.warn("\t"*2 + it)
            }
        }

        assert exportDirectory.listFiles() == null: "exportObjectAttachments() placed files in export folder even though in read only mode"
        log.debug("\t" + "*" * 20 + " exportObjectAttachments() readOnly true was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of exportObjectAttachments() readOnly false")
        im.readOnly = false
        exportedFiles = im.exportObjectAttachments(sourceObjectKey, exportPath + "/export")


        then:
        assert !exportedFiles.isEmpty(): "exportObjectAttachments() didn't return File objects even though not in read only mode"
        assert new File(exportPath + "/export").listFiles().size() > 0: "exportObjectAttachments() didn't place files in export folder even though not in read only mode"
        log.debug("\t" + "*" * 20 + " exportObjectAttachments() readOnly false was tested successfully " + "*" * 20)


        when:
        log.debug("\tStarting test of importObjectAttachments() readOnly true")
        im.readOnly = false
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            im.deleteObjectAttachment(it)
        }
        log.debug("\tDeleted destination objects attachments")
        im.readOnly = true
        ArrayList<SimplifiedAttachmentBean>importedFiles = im.importObjectAttachments(exportPath + "/export")

        then:
        assert importedFiles.every {it == null} : "importObjectAttachments() returned SimplifiedAttachmentBean even though in read only mode"
        im.getAllObjectAttachmentBeans(destinationObject).size() == 0
        log.debug("\t" + "*" * 20 + " importObjectAttachments() readOnly true was tested successfully " + "*" * 20)


        cleanup:
        log.debug("\tStarting cleanup of tests.")
        if (okToDeleteExportPath) {
            log.debug("\tDeleting export directory:" + exportPath)
            FileUtils.deleteDirectory(new File(exportPath))
        }


    }


    def 'Test attachment export and import'() {

        setup:
        log.info("Testing export  and import of attachments")
        String sourceObjectKey = "TAS-6590" //An object that already has attachments
        String destinationObjectKey = "TAD-6592" //An object that we will test importing attachments to. It´s preexisting attachments will be deleted


        boolean okToDeleteExportPath = false
        String exportPath = System.getProperty("java.io.tmpdir") + "/" + this.class.simpleName + "/TestAttachmentExportAndImport"
        assert !new File(exportPath).exists(): "Export directory already exists:" + exportPath
        okToDeleteExportPath = true

        InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
        im.log.setLevel(Level.ALL)

        ObjectBean destinationObject = im.getObjectBean(destinationObjectKey)

        log.debug("\tGetting source objects current attachment beans")
        ArrayList<SimplifiedAttachmentBean> sourceAttachmentBeans = im.getAllObjectAttachmentBeans(sourceObjectKey)
        log.trace("\t" * 2 + "Source object has ${sourceAttachmentBeans.size()} attachments")
        assert sourceAttachmentBeans.size() > 0: "Source object doesn't have any attachments"

        log.debug("\tDeleting destination objects preexisting attachments")
        im.getAllObjectAttachmentBeans(destinationObjectKey).each {
            im.deleteObjectAttachment(it)
        }
        log.trace("\t" * 2 + "Done")


        when:
        log.debug("\tStarting export of source objects attachments")
        ArrayList<File> exportedFiles = im.exportObjectAttachments(sourceObjectKey, exportPath)
        log.debug("\tExport complete")

        then:
        exportedFiles.name == sourceAttachmentBeans.originalFileName
        log.debug("\tThe exported files have the expected names")

        exportedFiles.size() == sourceAttachmentBeans.attachmentFile.size()
        log.debug("\tThe exported files are of the correct quantity")

        exportedFiles.parentFile.name.every { it == sourceObjectKey }
        log.debug("\tThe exported files are in the correct parent folder")

        exportedFiles.collect { it.bytes.sha256() } == sourceAttachmentBeans.attachmentFile.collect { it.bytes.sha256() }
        log.debug("\tThe hashes of the source files and the attachments match")

        log.debug("\t" + "*" * 20 + " Export was tested successfully " + "*" * 20)

        when:
        log.debug("\tTesting import of attachments with default parameters")
        ArrayList<SimplifiedAttachmentBean> importedBeans = im.importObjectAttachments(exportPath)
        log.debug("\tImport complete")

        then:
        importedBeans.originalFileName == sourceAttachmentBeans.originalFileName
        log.debug("\tThe imported files have the expected names")

        importedBeans.attachmentFile.size() == sourceAttachmentBeans.attachmentFile.size()
        log.debug("\tThe imported files are of the correct quantity")

        importedBeans.attachmentFile.collect { it.bytes.sha256() } == sourceAttachmentBeans.attachmentFile.collect { it.bytes.sha256() }
        log.debug("\tThe hashes of the source files and the imported files match")

        importedBeans.attachmentBean.objectId.every { it == destinationObject.id }
        log.debug("\tThe attachments where added to the correct destination object")

        importedBeans.attachmentBean.comment.every { it == "Imported from " + sourceObjectKey }
        log.debug("\tThe attachments where given the correct comment")

        exportedFiles.every { it.canRead() && it.exists() }
        log.debug("\tThe source files where not removed")

        log.debug("\t" + "*" * 20 + " Import with default parameters was tested successfully " + "*" * 20)


        when:
        log.debug("\tTesting import with ignore duplicates = true")
        Map<String, ArrayList<SimplifiedAttachmentBean>> destObjectAttachments = [:]
        destObjectAttachments.firstImport = im.getAllObjectAttachmentBeans(destinationObject)

        importedBeans = im.importObjectAttachments(exportPath, "\"Old Object key\" = SOURCE_OBJECT_KEY", "Imported from $sourceObjectKey", false, true)
        destObjectAttachments.ignoreDuplicates = im.getAllObjectAttachmentBeans(destinationObject)

        then:
        importedBeans.isEmpty()
        log.trace("\tNo new attachment beans where created")

        destObjectAttachments.firstImport == destObjectAttachments.ignoreDuplicates
        log.debug("\tNo new attachments where added to the destination object")

        log.debug("\t" + "*" * 20 + " Testing import with ignore duplicates = true was tested successfully " + "*" * 20)

        when:
        log.debug("\tTesting import with ignore duplicates = false")
        importedBeans = im.importObjectAttachments(exportPath, "\"Old Object key\" = SOURCE_OBJECT_KEY", "Imported from $sourceObjectKey", false, false)
        destObjectAttachments.importDuplicates = im.getAllObjectAttachmentBeans(destinationObject)


        then:
        importedBeans.size() == sourceAttachmentBeans.size()
        log.debug("\tThe imported files are of the correct quantity")


        importedBeans.originalFileName.every { importedFileName ->
            destObjectAttachments.importDuplicates.count { it.originalFileName == importedFileName } == 2
        }
        log.debug("\tThe destination object contains duplicates of all the imported files")


        log.debug("\t" + "*" * 20 + " Testing import with ignore duplicates = true was tested successfully " + "*" * 20)


        cleanup:
        log.debug("\tStarting cleanup of tests.")
        if (okToDeleteExportPath) {
            log.debug("\tDeleting export directory:" + exportPath)
            FileUtils.deleteDirectory(new File(exportPath))
        }


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
        assert testFile.exists(): "Error downloading sourcefile:" + sourceFilePath
        String testFileHash = testFile.getBytes().sha256()
        log.debug("\tDownload complete: ${testFile.path}, size: " + testFile.size())

        when:
        log.debug("\tTesting attaching file to $testObject")

        SimplifiedAttachmentBean newAttachmentBean = im.addObjectAttachment(testObject, testFile, "", attachmentComment, testDeletionOfSource)
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
        log.debug("\tTesting getAllObjectAttachmentBeans() to find and verify the new attachment")
        ArrayList<SimplifiedAttachmentBean> objectAttachments = im.getAllObjectAttachmentBeans(testObject)
        SimplifiedAttachmentBean retrievedSimplifiedAttachment = objectAttachments.find { it.id == newAttachmentBean.id }

        then:
        assert retrievedSimplifiedAttachment.originalFileName == testFile.name: "The name of the test file and the retrieved attachment file doesn't match"
        assert retrievedSimplifiedAttachment.attachmentFile.getBytes().sha256() == testFileHash: "The hash of the test file and the retrieved attachment file doesn't match"
        assert newAttachmentBean.attachmentBean.comment == retrievedSimplifiedAttachment.attachmentBean.comment: "The comment of the SimplifiedAttachmentBean differs"
        log.trace("\t\tThe new attachment was successfully verified")

        when:
        log.debug("\tTesting deleteObjectAttachment() by deleting the new attachment")
        boolean deletionResult = im.deleteObjectAttachment(newAttachmentBean)

        then:
        assert deletionResult: "deleteObjectAttachment was unsuccessful and returned false"
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
        sourceFilePath                                                                                             | testDeletionOfSource | attachmentComment
        "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png"                       | false                | "no comment"
        "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png"                       | true                 | "no comment"
        "https://www.atlassian.com/dam/jcr:242ae640-3d6a-472d-803d-45d8dcc2a8d2/Atlassian-horizontal-blue-rgb.svg" | true                 | " a comment"
        "https://bitbucket.org/atlassian/jira_docs/downloads/JIRACORESERVER_8.10.pdf"                              | false                | "another commenct"

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




