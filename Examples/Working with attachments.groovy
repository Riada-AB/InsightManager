import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.nio.file.Files


String fileName = "leia.jpg"
String sourceObjectKey = "TAS-6590"
String destinationObjectKey = "TAS-6591"

InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
Logger log = Logger.getLogger("im.attachments.example")
log.setLevel(Level.ALL)

log.info("Example of moving attachment from one object to another")
log.debug("\tFilename:" + fileName)
log.debug("\tSource object:" + sourceObjectKey)
log.debug("\tDestination object:" + destinationObjectKey)

log.debug("\tRetrieving source files")
ArrayList<SimplifiedAttachmentBean>sourceAttachmentBeans = im.getAllObjectAttachmentBeans(sourceObjectKey)
log.trace("\t"*2 + "The source object has " + sourceAttachmentBeans.size() + " attachments")

sourceAttachmentBeans= sourceAttachmentBeans.findAll{it.originalFileName == fileName}
log.trace("\t"*2 + "The source object has " + sourceAttachmentBeans.size() + " attachments with the filename:" + fileName)

log.debug("\tAttaching files to destination object")
sourceAttachmentBeans.each {beanToBeMoved ->

    log.trace("\t"*2 + "Creating a temporary file with the correct name")
    File tempFile = new File(beanToBeMoved.attachmentFile.parent + "/" + fileName)
    Files.copy(beanToBeMoved.attachmentFile.toPath(), tempFile.toPath() )


    log.trace("\t"*2 + "Attaching:" + tempFile.name + " to destination object")
    SimplifiedAttachmentBean newSimplifiedAttachmentBean = im.addObjectAttachment(destinationObjectKey, tempFile, "", "Moved from object:" + sourceObjectKey, true)

    assert newSimplifiedAttachmentBean.isValid()

    log.trace("\t"*2 + "Deleting original attachment from source object")
    assert im.deleteObjectAttachment(beanToBeMoved)

    log.trace("\t"*2 + "The attachment was successfully moved" )

}









